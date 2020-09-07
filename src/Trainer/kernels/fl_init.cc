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

#include <vector>

#include "tensorflow/core/framework/common_shape_fns.h"
#include "tensorflow/core/framework/op.h"
#include "tensorflow/core/framework/op_kernel.h"
#include "tensorflow/core/framework/shape_inference.h"
#include "tensorflow/core/framework/tensor.h"
#include "tensorflow/core/framework/tensor_shape.h"
#include "tensorflow/core/framework/types.h"
#include "tensorflow/core/lib/core/errors.h"
#include "tensorflow/core/lib/strings/numbers.h"

#include "tensorflow/contrib/jdfl/rpc/rpc_bridge/rpc_bridge_mgr.h"

namespace jdfl {

class FlBridgeServerInitOp : public OpKernel {
 public:
  explicit FlBridgeServerInitOp(OpKernelConstruction* ctx) : OpKernel(ctx) {
    bridge_mgr_ = RpcBridgeMgr::Singleton();
  }

  ~FlBridgeServerInitOp() override {}

  void Compute(OpKernelContext* ctx) override {
    const Tensor* input_tensor;
    OP_REQUIRES_OK(ctx, ctx->input("server_address", &input_tensor));
    auto input = input_tensor->flat<string>();
    OP_REQUIRES(ctx, input_tensor->NumElements() > 0,
                errors::InvalidArgument("server_address size should > 0"));

    Params conf;

    OP_REQUIRES_OK(ctx, ctx->input("appli_id", &input_tensor));
    OP_REQUIRES(ctx, input_tensor->NumElements() > 0,
                errors::InvalidArgument("appli_id size should > 0"));
    auto appli = input_tensor->flat<string>();
    conf.appli_id = appli(0);
    OP_REQUIRES_OK(ctx, ctx->input("rank_id", &input_tensor));
    OP_REQUIRES(ctx, input_tensor->NumElements() > 0,
                errors::InvalidArgument("rank_id size should > 0"));
    auto rand = input_tensor->flat<int>();
    conf.rank_id = rand(0);
    OP_REQUIRES_OK(ctx, ctx->input("role_def", &input_tensor));
    OP_REQUIRES(ctx, input_tensor->NumElements() > 0,
                errors::InvalidArgument("role_def size should > 0"));
    auto role = input_tensor->flat<string>();
    conf.role_def = role(0);
    OP_REQUIRES_OK(ctx, ctx->input("rpc_service_type", &input_tensor));
    OP_REQUIRES(ctx, input_tensor->NumElements() > 0,
                errors::InvalidArgument("rpc_service_type size should > 0"));
    auto rpc_service_type = input_tensor->flat<int>();
    if (rpc_service_type(0) == 0) {
      conf.service_type = KindOfServiceType::kUnary;
      LOG(INFO) << "KindOfServiceType: " << rpc_service_type(0)
                << "(Unary RPCs)";
    } else {
      conf.service_type = KindOfServiceType::kBidiStreaming;
      LOG(INFO) << "KindOfServiceType: " << rpc_service_type(0)
                << "(BidiStreaming RPCs)";
    }
    OP_REQUIRES_OK(ctx, ctx->input("contex_metadata", &input_tensor));
    auto meta_array = input_tensor->flat<string>();
    for (int i = 0; i < meta_array.size(); ++i) {
      LOG(INFO) << "Add Ctx Metadata, "
                << "uuid"
                << " : " << meta_array(i);
      conf.ctx_meta_conf.push_back(std::make_pair("uuid", meta_array(i)));
    }
    LOG(INFO) << "FlBridge Params:"
              << "appli_id(" << conf.appli_id << "), rank_id(" << conf.rank_id
              << "), role_def(" << conf.role_def << ")";

    Status s = bridge_mgr_->InitServer(input(0), conf);
    OP_REQUIRES(ctx, s.ok(), errors::InvalidArgument(s.error_message()));
  }

 private:
  RpcBridgeMgr* bridge_mgr_{nullptr};
};

class FlRpcChannelInitOp : public OpKernel {
 public:
  explicit FlRpcChannelInitOp(OpKernelConstruction* ctx) : OpKernel(ctx) {
    bridge_mgr_ = RpcBridgeMgr::Singleton();
    OP_REQUIRES_OK(ctx, ctx->GetAttr("channel_type", &channel_type_));
  }

  ~FlRpcChannelInitOp() override {}

  void Compute(OpKernelContext* ctx) override {
    const Tensor* input_tensor;
    OP_REQUIRES_OK(ctx, ctx->input("target_address", &input_tensor));
    auto input = input_tensor->flat<string>();

    Status s = bridge_mgr_->InitRpcChannel(channel_type_, input(0));
    OP_REQUIRES(ctx, s.ok(), errors::InvalidArgument(s.error_message()));
  }

 private:
  RpcBridgeMgr* bridge_mgr_{nullptr};
  std::string channel_type_;
};

namespace {
REGISTER_KERNEL_BUILDER(Name("FlBridgeServerInit").Device(DEVICE_CPU),
                        FlBridgeServerInitOp);

REGISTER_KERNEL_BUILDER(Name("FlRpcChannelInit").Device(DEVICE_CPU),
                        FlRpcChannelInitOp);
}  // namespace
}  // namespace jdfl
