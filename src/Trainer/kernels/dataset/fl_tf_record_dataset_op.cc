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
#include "tensorflow/core/framework/op_kernel.h"
#include "tensorflow/core/framework/partial_tensor_shape.h"
#include "tensorflow/core/framework/shape_inference.h"
#include "tensorflow/core/framework/tensor.h"
#include "tensorflow/core/kernels/data/name_utils.h"
#include "tensorflow/core/lib/core/blocking_counter.h"
#include "tensorflow/core/lib/gtl/cleanup.h"
#include "tensorflow/core/lib/io/buffered_inputstream.h"
#include "tensorflow/core/lib/io/inputbuffer.h"
#include "tensorflow/core/lib/io/random_inputstream.h"
#include "tensorflow/core/lib/io/record_reader.h"
#include "tensorflow/core/lib/io/zlib_compression_options.h"
#include "tensorflow/core/lib/io/zlib_inputstream.h"
#include "tensorflow/core/platform/macros.h"
#include "tensorflow/core/util/batch_util.h"

#include "tensorflow/contrib/jdfl/kernels/dataset/fl_tf_record_dataset_op.h"
#include "tensorflow/contrib/jdfl/rpc/rpc_bridge/rpc_bridge_mgr.h"

using namespace ::tensorflow;
using namespace ::tensorflow::data;

namespace jdfl {

constexpr const char* const FlTFRecordDatasetOp::kDatasetType;
constexpr const char* const FlTFRecordDatasetOp::kFileType;
constexpr const char* const FlTFRecordDatasetOp::kCompressionType;
constexpr const char* const FlTFRecordDatasetOp::kBufferSize;
constexpr const char* const FlTFRecordDatasetOp::kInputDataset;
constexpr const char* const FlTFRecordDatasetOp::kOutputTypes;
constexpr const char* const FlTFRecordDatasetOp::kOutputShapes;

constexpr char kFlTFRecordDataset[] = "FlTFRecordDataset";

class FlTFRecordDatasetOp::Dataset : public DatasetBase {
 public:
  explicit Dataset(OpKernelContext* ctx, const string& compression_type,
                   int64 buffer_size, const string& file_type,
                   const DatasetBase* input)
      : DatasetBase(DatasetContext(ctx)),
        compression_type_(compression_type),
        options_(io::RecordReaderOptions::CreateRecordReaderOptions(
            compression_type)),
        ftype_(file_type),
        input_(input) {
    input_->Ref();
    if (buffer_size > 0) {
      options_.buffer_size = buffer_size;
    }
  }

  ~Dataset() override { input_->Unref(); }

  std::unique_ptr<IteratorBase> MakeIteratorInternal(
      const string& prefix) const override {
    return absl::make_unique<Iterator>(Iterator::Params{
        this, name_utils::IteratorPrefix(kDatasetType, prefix)});
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
    TF_RETURN_IF_ERROR(b->AddInputDataset(ctx, input_, &input_graph_node));
    Node* compression_type = nullptr;
    TF_RETURN_IF_ERROR(b->AddScalar(compression_type_, &compression_type));
    Node* buffer_size = nullptr;
    TF_RETURN_IF_ERROR(b->AddScalar(options_.buffer_size, &buffer_size));
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

        // We are currently processing a file, so try to read the next record.
        if (reader_) {
          out_tensors->emplace_back(ctx->allocator({}), DT_STRING,
                                    TensorShape({}));
          Status s =
              reader_->ReadRecord(&out_tensors->back().scalar<string>()());
          if (s.ok()) {
            metrics::RecordTFDataBytesRead(
                kDatasetType, out_tensors->back().scalar<tstring>()().size());
            *end_of_sequence = false;
            return Status::OK();
          }
          out_tensors->pop_back();
          if (!errors::IsOutOfRange(s)) {
            // In case of other errors e.g., Abort...
            LOG(ERROR) << "Abort, ReadRecord() failed: " << s.error_message();
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
      return errors::Unimplemented("SaveInternal is currently not supported");
    }

    Status RestoreInternal(IteratorContext* ctx,
                           IteratorStateReader* reader) override {
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
        LOG(INFO) << "Get Next File: [" << *out_fname << "]";
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
      reader_ = absl::make_unique<io::SequentialRecordReader>(
          file_.get(), dataset()->options_);
      return Status::OK();
    }

    // Resets all reader streams.
    void ResetStreamsLocked() {
      reader_.reset();
      file_.reset();
    }

    // Resets all reader streams with remove.
    void ResetStreamsLockedWithCleanup(const std::string fname) {
      reader_.reset();
      int ret = CleanFile(fname);
      LOG(INFO) << "Cleanup " << fname << " with exit code : " << ret;
      file_.reset();
    }

    mutex mu_;
    std::string current_file_ GUARDED_BY(mu_);
    std::unique_ptr<IteratorBase> input_impl_ GUARDED_BY(mu_);
    std::unique_ptr<RandomAccessFile> file_ GUARDED_BY(mu_);
    std::unique_ptr<io::SequentialRecordReader> reader_ GUARDED_BY(mu_);
  };

  const string compression_type_;
  io::RecordReaderOptions options_;
  const string ftype_;
  const DatasetBase* const input_;
  std::vector<PartialTensorShape> output_shapes_;
};

FlTFRecordDatasetOp::FlTFRecordDatasetOp(OpKernelConstruction* ctx)
    : UnaryDatasetOpKernel(ctx) {
  if (ctx->HasAttr(kFileType)) {
    OP_REQUIRES_OK(ctx, ctx->GetAttr(kFileType, &file_type_));
  }
}

void FlTFRecordDatasetOp::MakeDataset(OpKernelContext* ctx, DatasetBase* input,
                                      DatasetBase** output) {
  string compression_type;
  OP_REQUIRES_OK(ctx, ParseScalarArgument<string>(ctx, kCompressionType,
                                                  &compression_type));

  int64 buffer_size = -1;
  OP_REQUIRES_OK(ctx,
                 ParseScalarArgument<int64>(ctx, kBufferSize, &buffer_size));
  OP_REQUIRES(ctx, buffer_size >= 0,
              errors::InvalidArgument(
                  "`buffer_size` must be >= 0 (0 == no buffering)"));

  *output = new Dataset(ctx, compression_type, buffer_size, file_type_, input);
}

namespace {

REGISTER_KERNEL_BUILDER(Name("FlTFRecordDataset").Device(DEVICE_CPU),
                        FlTFRecordDatasetOp);

}  // namespace

}  // namespace jdfl
