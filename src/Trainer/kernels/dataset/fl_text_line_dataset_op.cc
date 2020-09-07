// Copyright 2020 The 9nFL Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include <utility>
#include <vector>
#include <string>
#include <memory>

#include "tensorflow/core/common_runtime/metrics.h"
#include "tensorflow/core/framework/common_shape_fns.h"
#include "tensorflow/core/framework/partial_tensor_shape.h"
#include "tensorflow/core/framework/shape_inference.h"
#include "tensorflow/core/framework/tensor.h"
#include "tensorflow/core/kernels/data/name_utils.h"
#include "tensorflow/core/lib/io/buffered_inputstream.h"
#include "tensorflow/core/lib/io/inputbuffer.h"
#include "tensorflow/core/lib/io/random_inputstream.h"
#include "tensorflow/core/lib/io/zlib_compression_options.h"
#include "tensorflow/core/lib/io/zlib_inputstream.h"

#include "tensorflow/contrib/jdfl/kernels/dataset/fl_text_line_dataset_op.h"
#include "tensorflow/contrib/jdfl/rpc/rpc_bridge/rpc_bridge_mgr.h"

using namespace ::tensorflow;
using namespace ::tensorflow::data;
using namespace ::tensorflow::shape_inference;

namespace jdfl {

/* static */ constexpr const char* const FlTextLineDatasetOp::kDatasetType;
/* static */ constexpr const char* const FlTextLineDatasetOp::kCompressionType;
/* static */ constexpr const char* const FlTextLineDatasetOp::kBufferSize;

constexpr char kZLIB[] = "ZLIB";
constexpr char kGZIP[] = "GZIP";
constexpr char kCurrentFileIndex[] = "current_file_index";
constexpr char kCurrentPos[] = "current_pos";

class FlTextLineDatasetOp::Dataset : public DatasetBase {
 public:
  Dataset(OpKernelContext* ctx, const string& compression_type,
          const io::ZlibCompressionOptions& options, const DatasetBase* input)
      : DatasetBase(DatasetContext(ctx)),
        compression_type_(compression_type),
        use_compression_(!compression_type.empty()),
        options_(options),
        input_(input) {
    input_->Ref();
  }

  std::unique_ptr<IteratorBase> MakeIteratorInternal(
      const string& prefix) const override {
    return absl::make_unique<Iterator>(Iterator::Params{
        this,
        name_utils::IteratorPrefix(FlTextLineDatasetOp::kDatasetType, prefix)});
  }

  const DataTypeVector& output_dtypes() const override {
    static DataTypeVector* dtypes = new DataTypeVector({DT_STRING});
    return *dtypes;
  }

  const std::vector<PartialTensorShape>& output_shapes() const override {
    static std::vector<PartialTensorShape>* shapes =
        new std::vector<PartialTensorShape>({{}});
    return *shapes;
  }

  string DebugString() const override {
    return name_utils::DatasetDebugString(kDatasetType);
  }

  Status CheckExternalState() const override {
    return input_->CheckExternalState();
  }

 protected:
  Status AsGraphDefInternal(SerializationContext* ctx,
                            DatasetGraphDefBuilder* b,
                            Node** output) const override {
    Node* input_graph_node = nullptr;
    Node* compression_type = nullptr;
    Node* buffer_size = nullptr;
    TF_RETURN_IF_ERROR(b->AddInputDataset(ctx, input_, &input_graph_node));
    TF_RETURN_IF_ERROR(b->AddScalar(compression_type_, &compression_type));
    TF_RETURN_IF_ERROR(b->AddScalar(options_.input_buffer_size, &buffer_size));
    TF_RETURN_IF_ERROR(b->AddDataset(
        this, {input_graph_node, compression_type, buffer_size}, output));
    return Status::OK();
  }

 private:
  class Iterator : public DatasetIterator<Dataset> {
   public:
    explicit Iterator(const Params& params)
        : DatasetIterator<Dataset>(params) {}

    Status Initialize(IteratorContext* ctx) override {
      return dataset()->input_->MakeIterator(ctx, prefix(), &input_impl_);
    }

    Status GetNextInternal(IteratorContext* ctx,
                           std::vector<Tensor>* out_tensors,
                           bool* end_of_sequence) override {
      mutex_lock l(mu_);
      do {
        if (current_file_.empty()) {
          TF_RETURN_IF_ERROR(
              GetNextFileInternal(ctx, &current_file_, end_of_sequence));
          if (*end_of_sequence) {
            input_impl_.reset();
            return Status::OK();
          }
        }

        // We are currently processing a file, so try to read the next line.
        if (buffered_input_stream_) {
          string line_contents;
          Status s = buffered_input_stream_->ReadLine(&line_contents);

          if (s.ok()) {
            // Produce the line as output.
            metrics::RecordTFDataBytesRead(
                name_utils::OpName(FlTextLineDatasetOp::kDatasetType),
                line_contents.size());
            out_tensors->emplace_back(ctx->allocator({}), DT_STRING,
                                      TensorShape({}));
            out_tensors->back().scalar<tstring>()() = std::move(line_contents);
            *end_of_sequence = false;
            return Status::OK();
          } else if (!errors::IsOutOfRange(s)) {
            // Report non-EOF errors to the caller.
            LOG(ERROR) << "Abort, ReadLine() failed: " << s.error_message();
            *end_of_sequence = true;
            input_impl_.reset();
            return s;
          }

          // We have reached the end of the current file, so maybe move on to
          // next file.
          LOG(INFO) << "End of file : " << current_file_
                    << ", move to next file";
          ResetStreamsLockedWithCleanup(current_file_);
          current_file_.clear();
          TF_RETURN_IF_ERROR(
              GetNextFileInternal(ctx, &current_file_, end_of_sequence));

          if (*end_of_sequence) {
            input_impl_.reset();
            LOG(ERROR) << "end_of_sequence";
            return Status::OK();
          }
        }

        TF_RETURN_IF_ERROR(SetupStreamsLocked(ctx->env(), current_file_));
      } while (true);
    }

   protected:
    std::shared_ptr<model::Node> CreateNode(
        IteratorContext* ctx, model::Node::Args args) const override {
      return model::MakeSourceNode(std::move(args));
    }

    Status SaveInternal(IteratorStateWriter* writer) override {
      mutex_lock l(mu_);
      return errors::Unimplemented("SaveInternal is currently not supported");
    }

    Status RestoreInternal(IteratorContext* ctx,
                           IteratorStateReader* reader) override {
      mutex_lock l(mu_);
      return errors::Unimplemented(
          "RestoreInternal is currently not supported");
    }

   private:
    Status GetNextFileInternal(IteratorContext* ctx, std::string* out_fname,
                               bool* end_of_sequence) {
      if (!input_impl_) {
        LOG(ERROR) << "input_impl_ invalid.";
        *end_of_sequence = true;
        return Status::OK();
      }

      do {
        std::vector<Tensor> fname_element;
        TF_RETURN_IF_ERROR(
            input_impl_->GetNext(ctx, &fname_element, end_of_sequence));
        if (*end_of_sequence) {
          input_impl_.reset();
          return Status::OK();
        }
        *out_fname = fname_element[0].scalar<string>()();
        LOG(INFO) << "Get Next File: " << *out_fname;
        return Status::OK();
      } while (true);

      *end_of_sequence = true;
      return Status::OK();
    }

    Status SetupStreamsLocked(Env* env, const std::string& fname) {
      LOG(INFO) << "Setup File Stream for: [" << fname << "]";
      if(fname.empty()) {
          return errors::InvalidArgument("input file name invalid.");
      }
      TF_RETURN_IF_ERROR(env->NewRandomAccessFile(fname, &file_));
      input_stream_ =
          absl::make_unique<io::RandomAccessInputStream>(file_.get(), false);

      if (dataset()->use_compression_) {
        zlib_input_stream_ = absl::make_unique<io::ZlibInputStream>(
            input_stream_.get(), dataset()->options_.input_buffer_size,
            dataset()->options_.input_buffer_size, dataset()->options_);
        buffered_input_stream_ = absl::make_unique<io::BufferedInputStream>(
            zlib_input_stream_.get(), dataset()->options_.input_buffer_size,
            false);
      } else {
        buffered_input_stream_ = absl::make_unique<io::BufferedInputStream>(
            input_stream_.get(), dataset()->options_.input_buffer_size, false);
      }
      return Status::OK();
    }

    // Resets all reader streams.
    void ResetStreamsLocked() EXCLUSIVE_LOCKS_REQUIRED(mu_) {
      input_stream_.reset();
      zlib_input_stream_.reset();
      buffered_input_stream_.reset();
      file_.reset();
    }

    // Resets all reader streams with cleanup.
    void ResetStreamsLockedWithCleanup(const std::string fname) {
      input_stream_.reset();
      zlib_input_stream_.reset();
      buffered_input_stream_.reset();
      int ret = CleanFile(fname);
      LOG(INFO) << "Cleanup " << fname << " with exit code : " << ret;
      file_.reset();
    }

    mutex mu_;
    std::string current_file_ GUARDED_BY(mu_);
    std::unique_ptr<IteratorBase> input_impl_ GUARDED_BY(mu_);
    std::unique_ptr<io::RandomAccessInputStream> input_stream_ GUARDED_BY(mu_);
    std::unique_ptr<io::ZlibInputStream> zlib_input_stream_ GUARDED_BY(mu_);
    std::unique_ptr<io::BufferedInputStream> buffered_input_stream_
        GUARDED_BY(mu_);
    std::unique_ptr<RandomAccessFile> file_
        GUARDED_BY(mu_);  // must outlive input_stream_
  };

  const tstring compression_type_;
  const bool use_compression_;
  const io::ZlibCompressionOptions options_;
  const DatasetBase* const input_;
};

FlTextLineDatasetOp::FlTextLineDatasetOp(OpKernelConstruction* ctx)
    : UnaryDatasetOpKernel(ctx) {}

void FlTextLineDatasetOp::MakeDataset(OpKernelContext* ctx, DatasetBase* input,
                                      DatasetBase** output) {
  tstring compression_type;
  OP_REQUIRES_OK(ctx, ParseScalarArgument<tstring>(ctx, kCompressionType,
                                                   &compression_type));

  int64 buffer_size = -1;
  OP_REQUIRES_OK(ctx,
                 ParseScalarArgument<int64>(ctx, kBufferSize, &buffer_size));
  OP_REQUIRES(
      ctx, buffer_size >= 0,
      errors::InvalidArgument("`buffer_size` must be >= 0 (0 == default)"));

  io::ZlibCompressionOptions zlib_compression_options =
      io::ZlibCompressionOptions::DEFAULT();
  if (compression_type == kZLIB) {
    zlib_compression_options = io::ZlibCompressionOptions::DEFAULT();
  } else if (compression_type == kGZIP) {
    zlib_compression_options = io::ZlibCompressionOptions::GZIP();
  } else {
    OP_REQUIRES(ctx, compression_type.empty(),
                errors::InvalidArgument("Unsupported compression_type."));
  }

  if (buffer_size != 0) {
    // Set the override size.
    zlib_compression_options.input_buffer_size = buffer_size;
  }

  *output = new Dataset(ctx, compression_type, zlib_compression_options, input);
}

namespace {
REGISTER_KERNEL_BUILDER(Name("FlTextLineDataset").Device(DEVICE_CPU),
                        FlTextLineDatasetOp);
}  // namespace

}  // namespace jdfl
