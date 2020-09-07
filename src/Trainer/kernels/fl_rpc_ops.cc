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

namespace {

const int VAL_MAX_RETRIES = INT_MAX;

const int sleep_in_ms = (1 * 1000 * 1000);

const int max_length_dbgmsg = 256;

void DumpMsg(const TrainerWorkerMessage& msg) {
  switch (msg.msg_case()) {
    case TrainerWorkerMessage::kPrefetch:
      LOG(INFO) << "PrefetchMessage: ";
      break;
    case TrainerWorkerMessage::kStart:
      LOG(INFO) << "StartMessage: ";
      break;
    case TrainerWorkerMessage::kCommit:
      LOG(INFO) << "CommitMessage: ";
      break;
    case TrainerWorkerMessage::kData:
      LOG(INFO) << "DataMessage: ";
      break;
    default:
      LOG(ERROR) << "Invalid msg case:" << msg.msg_case();
      return;
  }
  return;
}

inline int RetryMaxCorrected(int times) {
  return (times >= 1) ? times : VAL_MAX_RETRIES;
}
}  // namespace

class FlTensorRecvOp : public OpKernel {
 public:
  explicit FlTensorRecvOp(OpKernelConstruction* ctx) : OpKernel(ctx) {
    bridge_mgr_ = RpcBridgeMgr::Singleton();
    OP_REQUIRES_OK(ctx, ctx->GetAttr("max_retries", &max_retries_));
    OP_REQUIRES_OK(ctx, ctx->GetAttr("timeout_in_ms", &timeout_in_ms_));
    OP_REQUIRES_OK(ctx, ctx->GetAttr("datamsg_rname", &datamsg_rname_));
    OP_REQUIRES_OK(ctx, ctx->GetAttr("datamsg_type", &datamsg_type_));
  }

  ~FlTensorRecvOp() override {}

  void Compute(OpKernelContext* ctx) override {
    // const Tensor* input_tensor;
    // OP_REQUIRES_OK(ctx, ctx->input("input_proto", &input_tensor));
    // auto input = input_tensor->flat<string>();

    BridgeInterface* bridge_api = bridge_mgr_->bridge_impl();
    OP_REQUIRES(ctx, bridge_api,
                errors::InvalidArgument("BridgeAgent not init..."));

    TrainerWorkerMessage msg_recv;
    Status s;
    int retries = RetryMaxCorrected(max_retries_);
    for (int i = 0; i < retries; i++) {
      s = bridge_api->QueryResult(0, FL_Key_DataMessage, datamsg_rname_,
                                  &msg_recv);
      if (s.ok()) {
        if (FlDebugging() > 99) {
          DumpMsg(msg_recv);
        }
        break;
      } else {
        LOG(INFO) << "QueryResult failed. " << s.error_message();
      }
    }
    OP_REQUIRES(ctx, s.ok(), errors::Aborted(s.error_message()));

    if ((logcnt_ < 100) || FlDebugging() ||
        !(bridge_mgr_->StepStats()->CurrentIterId() % 1000)) {
      LOG(INFO) << "Recv tensor: [" << msg_recv.data().name() << "]";
    }
    logcnt_++;

    TensorProto* proto = msg_recv.mutable_data()->mutable_tensor();
    Tensor parsed(proto->dtype());
    if (!parsed.FromProto(*proto)) {
      s = errors::InvalidArgument("Cannot parse recv tensor");
    }
    OP_REQUIRES(ctx, s.ok(), errors::Aborted(s.error_message()));
    if (FlDebugging() > 99) {
      LOG(INFO) << parsed.DebugString(max_length_dbgmsg);
    }

    // output tensor
    ctx->set_output(0, parsed);

    if (FlDebugging() > 49) {
      LOG(INFO) << "FlTensorRecvOp Compute Finished.";
    }
  }

 private:
  RpcBridgeMgr* bridge_mgr_{nullptr};
  int max_retries_;
  int timeout_in_ms_;
  std::string datamsg_rname_;
  std::string datamsg_type_;

  uint32_t logcnt_{0};
};

template <typename T>
class FlTensorSendOp : public OpKernel {
 public:
  explicit FlTensorSendOp(OpKernelConstruction* ctx) : OpKernel(ctx) {
    bridge_mgr_ = RpcBridgeMgr::Singleton();
    OP_REQUIRES_OK(ctx, ctx->GetAttr("max_retries", &max_retries_));
    OP_REQUIRES_OK(ctx, ctx->GetAttr("timeout_in_ms", &timeout_in_ms_));
    OP_REQUIRES_OK(ctx, ctx->GetAttr("datamsg_sname", &datamsg_sname_));
    OP_REQUIRES_OK(ctx, ctx->GetAttr("datamsg_type", &datamsg_type_));
  }

  ~FlTensorSendOp() override {}

  void Compute(OpKernelContext* ctx) override {
    const Tensor* input_tensor;
    OP_REQUIRES_OK(ctx, ctx->input("input", &input_tensor));

    // auto input = input_tensor->flat<float>();

    BridgeInterface* bridge_api = bridge_mgr_->bridge_impl();
    OP_REQUIRES(ctx, bridge_api,
                errors::InvalidArgument("BridgeAgent not init..."));

    if (FlDebugging() > 99) {
      LOG(INFO) << "Send tensor: "
                << input_tensor->DebugString(max_length_dbgmsg);
    }

    bridge_api->RequestWriteLock();

    // build Gradients msg
    TrainerWorkerMessage msg_send;
    int64_t current_seq = bridge_mgr_->StepStats()->CurrentSeqNum();
    msg_send.set_seq_num(current_seq);
    msg_send.mutable_data()->set_iter_id(
        bridge_mgr_->StepStats()->CurrentIterId());
    msg_send.mutable_data()->set_name(datamsg_sname_);
    TensorProto* proto = msg_send.mutable_data()->mutable_tensor();
    input_tensor->AsProtoTensorContent(proto);

    TrainerWorkerResponse resp;
    Status s;
    int retries = RetryMaxCorrected(max_retries_);
    for (int i = 0; i < retries; i++) {
      s = bridge_api->RequestDataTransmit(&msg_send, &resp);
      if (s.ok()) {
        break;
      } else {
        LOG(ERROR) << "FlTensorSendOp data transmit failed: "
                   << s.error_message();
        if ((i + 1) == retries) {
          break;
        }
        LOG(INFO) << "Sleeping for: " << sleep_in_ms;
        ctx->env()->SleepForMicroseconds(sleep_in_ms);
      }
    }

    if (s.ok()) {
      // Switch to Next sequence number
      int64_t next_seq = bridge_mgr_->StepStats()->CommitToNextSeqNum();
      if (FlDebugging() > 9) {
        LOG(INFO) << "sequence number: " << current_seq << " --> " << next_seq;
      }
    }

    bridge_api->RequestWriteCompletedUnlock();

    OP_REQUIRES(ctx, s.ok(), errors::Aborted(s.error_message()));

    if (IsRefType(ctx->input_dtype(0))) {
      ctx->forward_ref_input_to_ref_output(0, 0);
    } else {
      ctx->set_output(0, ctx->input(0));
    }

    if (FlDebugging() > 49) {
      LOG(INFO) << "FlTensorSendOp Compute Finished.";
    }
  }

 private:
  RpcBridgeMgr* bridge_mgr_{nullptr};
  int max_retries_;
  int timeout_in_ms_;
  std::string datamsg_sname_;
  std::string datamsg_type_;
};

class FlTrainStartOp : public OpKernel {
 public:
  explicit FlTrainStartOp(OpKernelConstruction* ctx) : OpKernel(ctx) {
    bridge_mgr_ = RpcBridgeMgr::Singleton();
    OP_REQUIRES_OK(ctx, ctx->GetAttr("max_retries", &max_retries_));
    OP_REQUIRES_OK(ctx, ctx->GetAttr("timeout_in_ms", &timeout_in_ms_));
    OP_REQUIRES_OK(ctx, ctx->GetAttr("datamsg_type", &datamsg_type_));
  }

  ~FlTrainStartOp() override {}

  void Compute(OpKernelContext* ctx) override {
    const Tensor* input_tensor;
    OP_REQUIRES_OK(ctx, ctx->input("input_proto", &input_tensor));

    auto input = input_tensor->flat<string>();

    BridgeInterface* bridge_api = bridge_mgr_->bridge_impl();
    OP_REQUIRES(ctx, bridge_api,
                errors::InvalidArgument("BridgeAgent not init..."));

    // Request Train Start
    TrainerWorkerMessage msg_send;
    TrainerWorkerResponse resp;

    bridge_api->RequestWriteLock();

    int64_t current_seq = bridge_mgr_->StepStats()->CurrentSeqNum();
    msg_send.set_seq_num(current_seq);
    msg_send.mutable_start()->set_iter_id(
        bridge_mgr_->StepStats()->CurrentIterId());

    if ((logcnt_ < 100) || FlDebugging() ||
        !(bridge_mgr_->StepStats()->CurrentIterId() % 1000)) {
      LOG(INFO) << "Request Train Start, Iter "
                << bridge_mgr_->StepStats()->CurrentIterId();
    }
    logcnt_++;

    Status s;
    int retries = RetryMaxCorrected(max_retries_);
    for (int i = 0; i < retries; i++) {
      s = bridge_api->RequestTrainStart(&msg_send, &resp);
      if (s.ok()) {
        break;
      }
      LOG(ERROR) << "FlTrainStartOp, send failed: " << s.error_message();
      if ((i + 1) == retries) {
        break;
      }
      LOG(INFO) << "Sleeping for: " << sleep_in_ms;
      ctx->env()->SleepForMicroseconds(sleep_in_ms);
    }

    if (s.ok()) {
      // Switch to Next sequence number
      int64_t next_seq = bridge_mgr_->StepStats()->CommitToNextSeqNum();
      if (FlDebugging() > 9) {
        LOG(INFO) << "sequence number: " << current_seq << " --> " << next_seq;
      }
    }

    bridge_api->RequestWriteCompletedUnlock();

    OP_REQUIRES(ctx, s.ok(), errors::Aborted(s.error_message()));

#if 0
    // Wait Commit Response
    LOG(INFO) << "Wait Commit Response ... ";
    TrainerWorkerMessage msg_recv;
    for (int i = 0; i < retries; i++) {
      s = bridge_api->QueryResult(
            0, FL_Key_StepCommit, FL_Key_StepCommit, &msg_recv);
      if (s.ok()) {
        if (FlDebugging() > 99) {
          DumpMsg(msg_recv);
        }
        break;
      } else {
        LOG(INFO) << "QueryResult failed. " << s.error_message();
      }
    }
    OP_REQUIRES(ctx, s.ok(),
        errors::Aborted(s.error_message()));
#endif

    if (IsRefType(ctx->input_dtype(0))) {
      ctx->forward_ref_input_to_ref_output(0, 0);
    } else {
      ctx->set_output(0, ctx->input(0));
    }

    if (FlDebugging() > 49) {
      LOG(INFO) << "FlTrainStartOp Compute Finished.";
    }
  }

 private:
  RpcBridgeMgr* bridge_mgr_{nullptr};
  int max_retries_;
  int timeout_in_ms_;
  std::string datamsg_type_;

  uint32_t logcnt_{0};
};

class FlTrainFollowOp : public OpKernel {
 public:
  explicit FlTrainFollowOp(OpKernelConstruction* ctx) : OpKernel(ctx) {
    bridge_mgr_ = RpcBridgeMgr::Singleton();
    OP_REQUIRES_OK(ctx, ctx->GetAttr("max_retries", &max_retries_));
    OP_REQUIRES_OK(ctx, ctx->GetAttr("timeout_in_ms", &timeout_in_ms_));
    OP_REQUIRES_OK(ctx, ctx->GetAttr("datamsg_type", &datamsg_type_));
  }

  ~FlTrainFollowOp() override {}

  void Compute(OpKernelContext* ctx) override {
    BridgeInterface* bridge_api = bridge_mgr_->bridge_impl();
    OP_REQUIRES(ctx, bridge_api,
                errors::InvalidArgument("BridgeAgent not init..."));

    // Wait Start Train cmd
    TrainerWorkerMessage msg_recv;
    Status s;
    int retries = RetryMaxCorrected(max_retries_);
    for (int i = 0; i < retries; i++) {
      s = bridge_api->QueryResult(0, FL_Key_TrainStart, FL_Key_TrainStart,
                                  &msg_recv);
      if (s.ok()) {
        if (FlDebugging() > 99) {
          DumpMsg(msg_recv);
        }
        break;
      } else {
        LOG(INFO) << "FlTrainFollowOp failed. " << s.error_message();
      }
    }
    // OP_REQUIRES( ctx, s.ok(),
    //    errors::Aborted(s.error_message()));
    if (!s.ok()) {
      LOG(INFO) << "Train Follow failed: " << s.error_message();
    }

#if 0
    bridge_api->RequestWriteLock();

    // Send back Commit
    TrainerWorkerMessage msg_send;
    msg_send->set_seq_num(bridge_mgr_->StepStats()->CurrentSeqNum());
    msg_send.mutable_commit()->set_round_id(
                bridge_mgr_->StepStats()->CurrentIterId());
    TrainerWorkerResponse resp;
    for (int i = 0; i < retries; i++) {
      s = bridge_api->RequestDataTransmit(&msg_send, &resp);
      if (s.ok()) {
        break;
      }
      LOG(ERROR) << "FlTrainFollowOp, send failed: " << s.error_message();
      if ( (i + 1) == retries ) { break; }
      LOG(INFO) << "Sleeping for: " << sleep_in_ms;
      ctx->env()->SleepForMicroseconds(sleep_in_ms);
    }
    if (s.ok()) {
      // Switch to Next sequence number
      int64_t seq = bridge_mgr_->StepStats()->CommitToNextSeqNum();
      if (FlDebugging() > 9) {
        LOG(INFO) << "Next sequence number: " << seq;
      }
    }
    bridge_api->RequestWriteCompletedUnlock();
    OP_REQUIRES(ctx, s.ok(),
        errors::Aborted(s.error_message()));
#endif

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
      LOG(INFO) << "FlTrainFollowOp Compute Finished.";
    }
  }

 private:
  RpcBridgeMgr* bridge_mgr_{nullptr};
  int max_retries_;
  int timeout_in_ms_;
  std::string datamsg_type_;
};

class FlTensorSendRecvOp : public OpKernel {
 public:
  explicit FlTensorSendRecvOp(OpKernelConstruction* ctx) : OpKernel(ctx) {
    bridge_mgr_ = RpcBridgeMgr::Singleton();
    OP_REQUIRES_OK(ctx, ctx->GetAttr("max_retries", &max_retries_));
    OP_REQUIRES_OK(ctx, ctx->GetAttr("timeout_in_ms", &timeout_in_ms_));
    OP_REQUIRES_OK(ctx, ctx->GetAttr("datamsg_sname", &datamsg_sname_));
    OP_REQUIRES_OK(ctx, ctx->GetAttr("datamsg_rname", &datamsg_rname_));
    OP_REQUIRES_OK(ctx, ctx->GetAttr("datamsg_type", &datamsg_type_));
  }

  ~FlTensorSendRecvOp() override {}

  void Compute(OpKernelContext* ctx) override {
    const Tensor* input_tensor;
    OP_REQUIRES_OK(ctx, ctx->input("input", &input_tensor));

    // auto input = input_tensor->flat<float>();

    BridgeInterface* bridge_api = bridge_mgr_->bridge_impl();
    OP_REQUIRES(ctx, bridge_api,
                errors::InvalidArgument("BridgeAgent not init..."));

    if (FlDebugging() > 99) {
      LOG(INFO) << "Send tensor: "
                << input_tensor->DebugString(max_length_dbgmsg);
    }

    bridge_api->RequestWriteLock();

    // build send msg
    TrainerWorkerMessage msg_send;
    int64_t current_seq = bridge_mgr_->StepStats()->CurrentSeqNum();
    msg_send.set_seq_num(current_seq);
    msg_send.mutable_data()->set_iter_id(
        bridge_mgr_->StepStats()->CurrentIterId());
    msg_send.mutable_data()->set_name(datamsg_sname_);
    TensorProto* proto = msg_send.mutable_data()->mutable_tensor();
    input_tensor->AsProtoTensorContent(proto);

    if ((s_logcnt_ < 100) || FlDebugging() ||
        !(bridge_mgr_->StepStats()->CurrentIterId() % 1000)) {
      LOG(INFO) << "Send " << datamsg_sname_;
    }
    s_logcnt_++;

    TrainerWorkerResponse resp;
    Status s;
    int retries = RetryMaxCorrected(max_retries_);
    for (int i = 0; i < retries; i++) {
      s = bridge_api->RequestDataTransmit(&msg_send, &resp);
      if (s.ok()) {
        break;
      }
      LOG(ERROR) << "FlTensorSendRecvOp data transmit failed: "
                 << s.error_message();
      if ((i + 1) == retries) {
        break;
      }
      LOG(INFO) << "Sleeping for: " << sleep_in_ms;
      ctx->env()->SleepForMicroseconds(sleep_in_ms);
    }

    if (s.ok()) {
      // Switch to Next sequence number
      int64_t next_seq = bridge_mgr_->StepStats()->CommitToNextSeqNum();
      if (FlDebugging() > 9) {
        LOG(INFO) << "sequence number: " << current_seq << " --> " << next_seq;
      }
    }

    bridge_api->RequestWriteCompletedUnlock();

    OP_REQUIRES(ctx, s.ok(), errors::Aborted(s.error_message()));

    // Wait Gradients
    TrainerWorkerMessage msg_recv;
    if ((r_logcnt_ < 100) || FlDebugging() ||
        !(bridge_mgr_->StepStats()->CurrentIterId() % 1000)) {
      LOG(INFO) << "Wait Recv " << datamsg_rname_;
    }
    for (int i = 0; i < retries; i++) {
      s = bridge_api->QueryResult(0, FL_Key_DataMessage, datamsg_rname_,
                                  &msg_recv);
      if (s.ok()) {
        if (FlDebugging() > 99) {
          DumpMsg(msg_recv);
        }
        break;
      } else {
        LOG(INFO) << "QueryResult failed. " << s.error_message();
      }
    }
    OP_REQUIRES(ctx, s.ok(), errors::Aborted(s.error_message()));

    if ((r_logcnt_ < 100) || FlDebugging() ||
        !(bridge_mgr_->StepStats()->CurrentIterId() % 1000)) {
      LOG(INFO) << "Recv tensor: [" << msg_recv.data().name() << "]";
    }
    r_logcnt_++;
    proto = msg_recv.mutable_data()->mutable_tensor();
    Tensor parsed(proto->dtype());
    if (!parsed.FromProto(*proto)) {
      s = errors::InvalidArgument("Cannot parse recv tensor");
    }
    OP_REQUIRES(ctx, s.ok(), errors::Aborted(s.error_message()));
    if (FlDebugging() > 99) {
      LOG(INFO) << parsed.DebugString(max_length_dbgmsg);
    }

    // output tensor
    ctx->set_output(0, parsed);

    if (FlDebugging() > 49) {
      LOG(INFO) << "FlTensorSendRecvOp Compute end.";
    }
  }

 private:
  RpcBridgeMgr* bridge_mgr_{nullptr};
  int max_retries_;
  int timeout_in_ms_;
  std::string datamsg_sname_;
  std::string datamsg_rname_;
  std::string datamsg_type_;

  uint32_t s_logcnt_{0};
  uint32_t r_logcnt_{0};
};

class FlTrainStepCommitOp : public OpKernel {
 public:
  explicit FlTrainStepCommitOp(OpKernelConstruction* ctx) : OpKernel(ctx) {
    bridge_mgr_ = RpcBridgeMgr::Singleton();
    OP_REQUIRES_OK(ctx, ctx->GetAttr("max_retries", &max_retries_));
    OP_REQUIRES_OK(ctx, ctx->GetAttr("timeout_in_ms", &timeout_in_ms_));
    OP_REQUIRES_OK(ctx, ctx->GetAttr("datamsg_type", &datamsg_type_));
  }

  ~FlTrainStepCommitOp() override {}

  void Compute(OpKernelContext* ctx) override {
    BridgeInterface* bridge_api = bridge_mgr_->bridge_impl();
    OP_REQUIRES(ctx, bridge_api,
                errors::InvalidArgument("BridgeAgent not init..."));

    bridge_api->RequestWriteLock();

    // Step Commit
    TrainerWorkerMessage msg_send;
    int64_t current_seq = bridge_mgr_->StepStats()->CurrentSeqNum();
    msg_send.set_seq_num(current_seq);
    msg_send.mutable_commit()->set_iter_id(
        bridge_mgr_->StepStats()->CurrentIterId());
    TrainerWorkerResponse resp;

    Status s;
    int retries = RetryMaxCorrected(max_retries_);
    for (int i = 0; i < retries; i++) {
      s = bridge_api->RequestDataTransmit(&msg_send, &resp);
      if (s.ok()) {
        break;
      }
      LOG(ERROR) << "FlTrainStepCommitOp, send failed: " << s.error_message();
      if ((i + 1) == retries) {
        break;
      }
      LOG(INFO) << "Sleeping for: " << sleep_in_ms;
      ctx->env()->SleepForMicroseconds(sleep_in_ms);
    }

    // Switch to Next IterId
    int64_t rid = bridge_mgr_->StepStats()->NextIterId();
    if ((logcnt_ < 100) || FlDebugging() ||
        !(bridge_mgr_->StepStats()->CurrentIterId() % 1000)) {
      LOG(INFO) << "Next IterId: " << rid;
    }
    logcnt_++;

    if (s.ok()) {
      // Switch to Next sequence number
      int64_t next_seq = bridge_mgr_->StepStats()->CommitToNextSeqNum();
      if (FlDebugging() > 9) {
        LOG(INFO) << "sequence number: " << current_seq << " --> " << next_seq;
      }
    }

    bridge_api->RequestWriteCompletedUnlock();

    OP_REQUIRES(ctx, s.ok(), errors::Aborted(s.error_message()));

    if (FlDebugging() > 49) {
      LOG(INFO) << "FlBridgeStepCommitOp Compute end.";
    }
  }

 private:
  RpcBridgeMgr* bridge_mgr_{nullptr};
  int max_retries_;
  int timeout_in_ms_;
  std::string datamsg_type_;

  uint32_t logcnt_{0};
};

namespace {
#define REGISTER_KERNEL(type)                                            \
  REGISTER_KERNEL_BUILDER(                                               \
      Name("FlTensorSend").Device(DEVICE_CPU).TypeConstraint<type>("T"), \
      FlTensorSendOp<type>)

REGISTER_KERNEL(int64);
REGISTER_KERNEL(float);

#undef REGISTER_KERNEL

#define REGISTER_KERNEL(type)                                               \
  REGISTER_KERNEL_BUILDER(Name("FlGradBackpropRequest").Device(DEVICE_CPU), \
                          FlTensorSendOp<type>)

REGISTER_KERNEL(float);

#undef REGISTER_KERNEL

REGISTER_KERNEL_BUILDER(Name("FlTensorRecv").Device(DEVICE_CPU),
                        FlTensorRecvOp);
REGISTER_KERNEL_BUILDER(Name("FlTensorRecvWithFakeInput").Device(DEVICE_CPU),
                        FlTensorRecvOp);
REGISTER_KERNEL_BUILDER(Name("FlTensorRecvWithGradBp").Device(DEVICE_CPU),
                        FlTensorRecvOp);
REGISTER_KERNEL_BUILDER(Name("FlGradRecv").Device(DEVICE_CPU), FlTensorRecvOp);

REGISTER_KERNEL_BUILDER(Name("FlTensorSendRecv").Device(DEVICE_CPU),
                        FlTensorSendRecvOp);

REGISTER_KERNEL_BUILDER(Name("FlTrainFollow").Device(DEVICE_CPU),
                        FlTrainFollowOp);

REGISTER_KERNEL_BUILDER(Name("FlTrainStart").Device(DEVICE_CPU),
                        FlTrainStartOp);

REGISTER_KERNEL_BUILDER(Name("FlTrainStepCommit").Device(DEVICE_CPU),
                        FlTrainStepCommitOp);
}  // namespace
}  // namespace jdfl
