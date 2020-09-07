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

#include <memory>
#include <vector>
#include <string>

#include "tensorflow/core/framework/common_shape_fns.h"
#include "tensorflow/core/framework/partial_tensor_shape.h"
#include "tensorflow/core/framework/shape_inference.h"
#include "tensorflow/core/framework/tensor.h"
#include "tensorflow/core/kernels/data/name_utils.h"

#include "tensorflow/contrib/jdfl/kernels/dataset/fl_grpc_fetch_dataset_op.h"
#include "tensorflow/contrib/jdfl/rpc/rpc_bridge/rpc_bridge_mgr.h"

using namespace ::tensorflow;
using namespace ::tensorflow::data;

namespace jdfl {

constexpr const char* const FlGrpcFetchDatasetOp::kDatasetType;
constexpr const char* const FlGrpcFetchDatasetOp::kRoleDef;
constexpr const char* const FlGrpcFetchDatasetOp::kMaxRetries;
constexpr const char* const FlGrpcFetchDatasetOp::kTimeoutInMs;

namespace {

const int sleep_in_ms = (10 * 1000 * 1000);
}  // namespace

class FlGrpcFetchDatasetOp::Dataset : public DatasetBase {
 private:
  const string role_def_;
  int max_retries_;
  int timeout_in_ms_;

 public:
  explicit Dataset(OpKernelContext* ctx, const string& role_def,
                   int max_retries, int timeout_in_ms)
      : DatasetBase(DatasetContext(ctx)),
        role_def_(role_def),
        max_retries_(max_retries),
        timeout_in_ms_(timeout_in_ms) {}

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

  Status CheckExternalState() const override { return Status::OK(); }

 protected:
  Status AsGraphDefInternal(SerializationContext* ctx,
                            DatasetGraphDefBuilder* b,
                            Node** output) const override {
    Node* n = nullptr;
    TF_RETURN_IF_ERROR(b->AddScalar(role_def_, &n));

    AttrValue attr_max_retries;
    b->BuildAttrValue(max_retries_, &attr_max_retries);
    AttrValue attr_timeout_in_ms;
    b->BuildAttrValue(timeout_in_ms_, &attr_timeout_in_ms);

    TF_RETURN_IF_ERROR(b->AddDataset(
        this, {n},
        {{kMaxRetries, attr_max_retries}, {kTimeoutInMs, attr_timeout_in_ms}},
        output));
    return Status::OK();
  }

 private:
  class Iterator : public DatasetIterator<Dataset> {
   public:
    explicit Iterator(const Params& params) : DatasetIterator<Dataset>(params) {
      bridge_mgr_ = RpcBridgeMgr::Singleton();
    }

    Status GetNextInternal(IteratorContext* ctx,
                           std::vector<Tensor>* out_tensors,
                           bool* end_of_sequence) override {
      DcInterface* db_api = bridge_mgr_->dc_impl();
      if (!db_api) {
        return errors::InvalidArgument("DbAgent not init...");
      }

      BridgeInterface* bridge_api = bridge_mgr_->bridge_impl();
      if (!bridge_api) {
        return errors::InvalidArgument("BridgeAgent not init...");
      }

      mutex_lock l(mu_);
      const string roledef = dataset()->role_def_;

      do {
        // Leader
        {
          if (roledef == RoleDef_Leader) {
            // fetch data block form datacenter
            FetchDataBlockRequest request;
            FetchDataBlockResponse response;
            Status s = db_api->FetchDataBlock(&request, &response);
            if (!s.ok()) {
              LOG(ERROR) << "FlGrpcFetchDatasetOp fetch data block failed: "
                         << s.error_message();
              LOG(INFO) << "Sleeping for: " << sleep_in_ms;
              ctx->env()->SleepForMicroseconds(sleep_in_ms);
              continue;
            }

            if (response.status_code() == StatusCode::ERROR_ABORTED) {
              LOG(ERROR) << "DC response internal error, Aborted ...";
              return errors::InvalidArgument(
                  "DC response internal error, Aborted ...");
            }

            if (response.status_code() == StatusCode::NOT_READY) {
              LOG(INFO) << "Data block not ready ... ";
              LOG(INFO) << "Sleeping for: " << sleep_in_ms;
              ctx->env()->SleepForMicroseconds(sleep_in_ms);
              continue;
            }

            if (response.status_code() == StatusCode::FINISHED) {
              LOG(INFO) << "Data block FINISHED... ";
              LoadDataBlockRequest msg_send;
              ResultStatus msg_recv;

              msg_send.set_count(datablock_count);

              // notify peer end of datablock
              for (int i = 0; i < 3; i++) {
                s = bridge_api->RequestLoadDataBlock(&msg_send, &msg_recv);
                if (!s.ok()) {
                  LOG(ERROR)
                      << "RequestLoadDataBlock failed: " << s.error_message();
                  LOG(INFO) << "Sleeping for: " << sleep_in_ms;
                  ctx->env()->SleepForMicroseconds(sleep_in_ms);
                  continue;
                }
                LOG(ERROR) << "Notify peer end of datablocks OK";
                break;
              }
              *end_of_sequence = true;
              return Status::OK();
            } else {
              // Copy hdfs file to local
              std::string src_fname, out_fname;
              src_fname = response.db_info().data_path();
              if (src_fname.empty()) {
                LOG(ERROR) << "Fetch DataBlock invalid(empty).";
                ctx->env()->SleepForMicroseconds(sleep_in_ms);
                // try next datablock
                continue;
              }
              LOG(INFO) << "Fetch DataBlock: " << src_fname;
              int ret = PrepareFile(src_fname, &out_fname);
              if (ret) {
                LOG(ERROR) << "Data block download failed: " << src_fname;
                ctx->env()->SleepForMicroseconds(sleep_in_ms);
                // try next datablock
                continue;
              }

              LoadDataBlockRequest msg_send;
              ResultStatus msg_recv;

              msg_send.set_count(datablock_count);
              msg_send.set_block_id(response.db_info().request_id());

              // notify peer prepare data block
              LOG(INFO) << "Request peer prepare data block: "
                        << response.db_info().request_id();
              for (int i = 0; i < 10; i++) {
                s = bridge_api->RequestLoadDataBlock(&msg_send, &msg_recv);
                // OP_REQUIRES( ctx, s.ok(),
                //    errors::InvalidArgument(s.error_message()));
                if (!s.ok()) {
                  LOG(ERROR)
                      << "RequestLoadDataBlock failed: " << s.error_message();
                  LOG(INFO) << "Sleeping for: " << sleep_in_ms;
                  ctx->env()->SleepForMicroseconds(sleep_in_ms);
                  continue;
                }
                if (msg_recv.result_code() != ResultCode::SUCCESS) {
                  LOG(ERROR) << "RequestLoadDataBlock resp failed, err_code:"
                             << msg_recv.result_code()
                             << ", err_info:" << msg_recv.error_message();
                  LOG(INFO) << "Sleeping for: " << sleep_in_ms;
                  ctx->env()->SleepForMicroseconds(sleep_in_ms);
                  continue;
                }
                LOG(ERROR) << "Peer datablock ready: "
                           << response.db_info().request_id();
                break;
              }
              if ((!s.ok()) || (msg_recv.result_code())) {
                LOG(ERROR) << "RequestLoadDataBlock skip : "
                           << response.db_info().request_id();
                int ret = CleanFile(out_fname);
                LOG(INFO) << "Claenup " << out_fname
                          << " with exit code : " << ret;

                // datablock count
                datablock_count++;

                continue;
              }

              // data block OK
              LOG(ERROR) << "enqueue datablock file: " << out_fname;
              out_tensors->emplace_back(ctx->allocator({}), DT_STRING,
                                        TensorShape({}));
              out_tensors->back().scalar<string>()() = out_fname;

              // datablock count
              datablock_count++;

              *end_of_sequence = false;
              return Status::OK();
            }
          }
        }

        // Follower
        {
          if (roledef == RoleDef_Follower) {
            //  Wait leader datablock request
            std::string local_fname;
            bool end_of_files = false;
            Status s = bridge_api->QueryReadyFile(0, "/LoadDataBlock",
                                                  &local_fname, &end_of_files);
            if (!s.ok()) {
              LOG(ERROR) << "Query ready datablock file failed: "
                         << s.error_message();
              LOG(INFO) << "Sleeping for: " << sleep_in_ms;
              ctx->env()->SleepForMicroseconds(sleep_in_ms);
              continue;
            }

            if (!end_of_files) {
              // data block OK
              LOG(ERROR) << "Ready datablock got: " << local_fname;
              out_tensors->emplace_back(ctx->allocator({}), DT_STRING,
                                        TensorShape({}));
              out_tensors->back().scalar<string>()() = local_fname;

              *end_of_sequence = false;
              return Status::OK();
            } else {
              LOG(INFO) << "Data block FINISHED... ";
              *end_of_sequence = true;
              return Status::OK();
            }
          }
        }

        // error role def
        { return errors::InvalidArgument("RoleDef invalid ... ", roledef); }
      } while (true);
    }

   protected:
    Status SaveInternal(IteratorStateWriter* writer) override {
      return errors::Unimplemented("SaveInternal is currently not supported");
    }

    Status RestoreInternal(IteratorContext* ctx,
                           IteratorStateReader* reader) override {
      return errors::Unimplemented(
          "RestoreInternal is currently not supported");
    }

   private:
    mutex mu_;
    int64 datablock_count{0};
    RpcBridgeMgr* bridge_mgr_ = nullptr;
  };
};

FlGrpcFetchDatasetOp::FlGrpcFetchDatasetOp(OpKernelConstruction* ctx)
    : DatasetOpKernel(ctx) {
  OP_REQUIRES_OK(ctx, ctx->GetAttr("max_retries", &max_retries_));
  OP_REQUIRES_OK(ctx, ctx->GetAttr("timeout_in_ms", &timeout_in_ms_));
}

void FlGrpcFetchDatasetOp::MakeDataset(OpKernelContext* ctx,
                                       DatasetBase** output) {
  string role_def;
  OP_REQUIRES_OK(ctx, ParseScalarArgument<string>(ctx, kRoleDef, &role_def));
  *output = new Dataset(ctx, role_def, max_retries_, timeout_in_ms_);
}

namespace {
REGISTER_KERNEL_BUILDER(Name("FlGrpcFetchDataset").Device(DEVICE_CPU),
                        FlGrpcFetchDatasetOp);
}  // namespace

}  // namespace jdfl
