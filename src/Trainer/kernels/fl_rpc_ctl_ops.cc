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

#include <limits>
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

#include "tensorflow/contrib/jdfl/rpc/proto/bridge_agent.pb.h"
#include "tensorflow/contrib/jdfl/rpc/rpc_bridge/rpc_bridge_mgr.h"


namespace jdfl {

class FlChannelConnectOp : public OpKernel {
 public:
  explicit FlChannelConnectOp(OpKernelConstruction* ctx) : OpKernel(ctx) {
    bridge_mgr_ = RpcBridgeMgr::Singleton();
  }

  ~FlChannelConnectOp() override {}

  void Compute(OpKernelContext* ctx) override {
    BridgeInterface* bridge_api = bridge_mgr_->bridge_impl();
    OP_REQUIRES(ctx, bridge_api,
                errors::InvalidArgument("BridgeAgent not init..."));

    ConnectRequest msg_send;
    ConnectResponse msg_recv;

    msg_send.set_app_id(bridge_mgr_->AppID());
    msg_send.set_worker_rank(bridge_mgr_->RankID());
    msg_send.set_identifier(bridge_mgr_->Identifier());

    Status s = bridge_api->RequestConnect(&msg_send, &msg_recv);
    if (!s.ok()) {
      LOG(ERROR) << "FlChannelConnectOp failed: " << s.error_message();
    }
    // OP_REQUIRES( ctx, s.ok(),
    //    errors::InvalidArgument(s.error_message()));

    if (s.ok()) {
      LOG(INFO) << " Peer Worker info: appli_id: (" << msg_recv.app_id()
                << "), worker_rank: (" << msg_recv.worker_rank() << ")";
    }

    Tensor* status_code_t = nullptr;
    Tensor* status_message_t = nullptr;
    OP_REQUIRES_OK(ctx, ctx->allocate_output(0, {1}, &status_code_t));
    OP_REQUIRES_OK(ctx, ctx->allocate_output(1, {1}, &status_message_t));
    auto status_code = status_code_t->template flat<int32>();
    auto status_message = status_message_t->template flat<std::string>();
    if (s.ok()) {
      status_code(0) = 0;
      status_message(0) = "OK";
    } else {
      status_code(0) = 1;
      status_message(0) = s.error_message();
    }

    if (FlDebugging() > 49) {
      LOG(INFO) << "FlChannelConnectOp Compute Finished.";
    }
  }

 private:
  RpcBridgeMgr* bridge_mgr_{nullptr};
};

class FlWaitPeerReadyOp : public OpKernel {
 public:
  explicit FlWaitPeerReadyOp(OpKernelConstruction* ctx) : OpKernel(ctx) {
    bridge_mgr_ = RpcBridgeMgr::Singleton();
  }

  ~FlWaitPeerReadyOp() override {}

  void Compute(OpKernelContext* ctx) override {
    BridgeInterface* bridge_api = bridge_mgr_->bridge_impl();
    OP_REQUIRES(ctx, bridge_api,
                errors::InvalidArgument("BridgeAgent not init..."));

    // Wait Peer ready
    LOG(INFO) << "Wait Peer ready ... ";
    Status s;
    ConnectRequest peer_recv;
    for (int i = 0; i < 1; i++) {
      s = bridge_api->WaitPeerReady(0, FL_Key_Connect, &peer_recv);
      if (s.ok()) {
        break;
      } else {
        LOG(INFO) << "WaitPeerReady failed. " << s.error_message();
      }
    }
    // OP_REQUIRES( ctx, s.ok(),
    //    errors::InvalidArgument(s.error_message()));

    if (s.ok()) {
      LOG(INFO) << " Peer Worker info: appli_id: (" << peer_recv.app_id()
                << "), worker_rank: (" << peer_recv.worker_rank() << ")";
    }

    Tensor* status_code_t = nullptr;
    Tensor* status_message_t = nullptr;
    OP_REQUIRES_OK(ctx, ctx->allocate_output(0, {1}, &status_code_t));
    OP_REQUIRES_OK(ctx, ctx->allocate_output(1, {1}, &status_message_t));
    auto status_code = status_code_t->template flat<int32>();
    auto status_message = status_message_t->template flat<std::string>();
    if (s.ok()) {
      status_code(0) = 0;
      status_message(0) = "OK";
    } else {
      status_code(0) = 1;
      status_message(0) = s.error_message();
    }

    if (FlDebugging() > 49) {
      LOG(INFO) << "FlWaitPeerConnectedOp Compute Finished.";
    }
  }

 private:
  RpcBridgeMgr* bridge_mgr_{nullptr};
};

class FlChannelHeartbeatOp : public OpKernel {
 public:
  explicit FlChannelHeartbeatOp(OpKernelConstruction* ctx) : OpKernel(ctx) {
    bridge_mgr_ = RpcBridgeMgr::Singleton();
  }

  ~FlChannelHeartbeatOp() override {}

  void Compute(OpKernelContext* ctx) override {
    BridgeInterface* bridge_api = bridge_mgr_->bridge_impl();
    OP_REQUIRES(ctx, bridge_api,
                errors::InvalidArgument("BridgeAgent not init..."));

    HeartbeatRequest request;
    HeartbeatResponse response;
    Status s = bridge_api->RequestHeartbeat(&request, &response);
    if (!s.ok()) {
      LOG(ERROR) << "FlChannelHeartbeatOp failed: " << s.error_message();
    }
    // OP_REQUIRES( ctx, s.ok(),
    //    errors::InvalidArgument(s.error_message()));

    if (s.ok()) {
      LOG(INFO) << " Peer Worker info: appli_id: (" << response.app_id()
                << "), worker_rank: (" << response.worker_rank()
                << "), current_iter_id: (" << response.current_iter_id() << ")";
    }

    Tensor* status_code_t = nullptr;
    Tensor* status_message_t = nullptr;
    OP_REQUIRES_OK(ctx, ctx->allocate_output(0, {1}, &status_code_t));
    OP_REQUIRES_OK(ctx, ctx->allocate_output(1, {1}, &status_message_t));
    auto status_code = status_code_t->template flat<int32>();
    auto status_message = status_message_t->template flat<std::string>();
    if (s.ok()) {
      status_code(0) = 0;
      status_message(0) = "OK";
    } else {
      status_code(0) = 1;
      status_message(0) = s.error_message();
    }

    if (FlDebugging() > 49) {
      LOG(INFO) << "FlChannelHeartbeatOp Compute Finished.";
    }
  }

 private:
  RpcBridgeMgr* bridge_mgr_{nullptr};
};

namespace {
REGISTER_KERNEL_BUILDER(Name("FlChannelConnect").Device(DEVICE_CPU),
                        FlChannelConnectOp);

REGISTER_KERNEL_BUILDER(Name("FlWaitPeerReady").Device(DEVICE_CPU),
                        FlWaitPeerReadyOp);

REGISTER_KERNEL_BUILDER(Name("FlChannelHeartbeat").Device(DEVICE_CPU),
                        FlChannelHeartbeatOp);
}  // namespace
}  // namespace jdfl

