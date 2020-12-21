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

#include <deque>
#include <memory>
#include <unordered_map>
#include <vector>
#include <string>

#include "grpcpp/alarm.h"
#include "grpcpp/server_builder.h"
#include "tensorflow/core/distributed_runtime/rpc/async_service_interface.h"
#include "tensorflow/core/distributed_runtime/rpc/grpc_call.h"
#include "tensorflow/core/distributed_runtime/rpc/grpc_util.h"
#include "tensorflow/core/framework/cancellation.h"
#include "tensorflow/core/framework/tensor.h"
#include "tensorflow/core/lib/core/errors.h"
#include "tensorflow/core/lib/core/status.h"
#include "tensorflow/core/lib/gtl/map_util.h"
#include "tensorflow/core/lib/strings/strcat.h"
#include "tensorflow/core/lib/strings/stringprintf.h"
#include "tensorflow/core/platform/logging.h"
#include "tensorflow/core/platform/mutex.h"
#include "tensorflow/core/platform/tracing.h"

#include "tensorflow/contrib/jdfl/rpc/rpc_bridge/fl_utils.h"
#include "tensorflow/contrib/jdfl/rpc/rpc_bridge/rpc_bridge_agent_service.h"
#include "tensorflow/contrib/jdfl/rpc/rpc_bridge/rpc_bridge_mgr.h"

namespace jdfl {

static int64 _RpcTEventCnt = 0;
static int64 _RpcDEventCnt = 0;
static int64 _RpcUnaryCallEventCnt = 0;
static int64 _RpcStreamingCallEventCnt = 0;
static int64 _RpcDataLoadEventCnt = 0;
static int64 _RpcDataLoadNextId = 0;

class RpcBridgeAgentService : public AsyncServiceInterface {
 public:
  RpcBridgeAgentService(::grpc::ServerBuilder* builder,
                        RpcBridgeRecvCache* service_cache,
                        RpcBridgeMgr* bridge_mgr)
      : cache_(service_cache), bridge_mgr_(bridge_mgr), is_shutdown_(false) {
    builder->RegisterService(&agent_service_);
    tcq_ = builder->AddCompletionQueue();
    dcq_ = builder->AddCompletionQueue();
  }

  void Shutdown() override {
    bool did_shutdown = false;
    {
      mutex_lock l(service_shutdown_mu_);
      if (!is_shutdown_) {
        LOG(INFO) << "Shutting down RpcBridgeAgentService.";
        is_shutdown_ = true;
        did_shutdown = true;
      }
    }
    if (did_shutdown) {
      tcq_->Shutdown();
      dcq_->Shutdown();
    }

    LOG(INFO) << "RpcBridgeAgentService Shutdown() finish.";
  }

  ~RpcBridgeAgentService() { LOG(INFO) << "~RpcBridgeAgentService() done."; }

#define ENQUEUE_REQUEST_METHOD(method, request_msg, response_msg, cq,      \
                               supports_cancel)                            \
  do {                                                                     \
    mutex_lock l(service_shutdown_mu_);                                    \
    if (!is_shutdown_) {                                                   \
      Call<RpcBridgeAgentService, TrainerWorkerService::AsyncService,      \
           request_msg, response_msg>::                                    \
          EnqueueRequestForMethod(                                         \
              &agent_service_, cq.get(),                                   \
              static_cast<int>(RpcBridgeAgentMethod::k##method),           \
              &RpcBridgeAgentService::method##Handler, (supports_cancel)); \
    }                                                                      \
  } while (0)

#define ENQUEUE_REQUEST(method, request_msg, response_msg, cq,                 \
                        supports_cancel)                                       \
  do {                                                                         \
    mutex_lock l(service_shutdown_mu_);                                        \
    if (!is_shutdown_) {                                                       \
      Call<RpcBridgeAgentService, TrainerWorkerService::AsyncService,          \
           request_msg, response_msg>::                                        \
          EnqueueRequest(&agent_service_, cq.get(),                            \
                         &TrainerWorkerService::AsyncService::Request##method, \
                         &RpcBridgeAgentService::method##Handler,              \
                         (supports_cancel));                                   \
    }                                                                          \
  } while (0)

  void HandleRPCsTcq() {
    LOG(INFO) << "HandleRPCsTcq Start ...";
    for (int i = 0; i < 32; ++i) {
      ENQUEUE_REQUEST(Transmit, TrainerWorkerMessage, TrainerWorkerResponse,
                      tcq_, false);
    }
    ENQUEUE_REQUEST(Connect, ConnectRequest, ConnectResponse, tcq_, false);
    ENQUEUE_REQUEST(Heartbeat, HeartbeatRequest, HeartbeatResponse, tcq_,
                    false);

    // Request a StreamingEnqueue call.
    ServerBidirectionalStreamingCall<
        RpcBridgeAgentService, TrainerWorkerService::AsyncService,
        TrainerWorkerMessage, TrainerWorkerResponse>::
        EnqueueRequest(
            &agent_service_, tcq_.get(),
            &TrainerWorkerService::AsyncService::RequestStreamTransmit,
            &RpcBridgeAgentService::StreamTransmitHandler);

    void* tag;
    bool ok;

    LOG(INFO) << "HandleRPCsTcq Next...";
    while (tcq_->Next(&tag, &ok)) {
      if (FlDebugging()) {
        LOG(INFO) << "HandleRPCsTcq Next Event... " << _RpcTEventCnt++;
      }
      GrpcCallTag<RpcBridgeAgentService>* callback_tag =
          static_cast<GrpcCallTag<RpcBridgeAgentService>*>(tag);
      CHECK(callback_tag);
      callback_tag->OnCompleted(this, ok);
    }
    LOG(INFO) << "rpc_bridge_agent_service HandleRPCsTcq exit...";
  }

  void HandleRPCsDcq() {
    LOG(INFO) << "HandleRPCsDcq Start ...";
    for (int i = 0; i < 16; ++i) {
      ENQUEUE_REQUEST(LoadDataBlock, LoadDataBlockRequest, ResultStatus, dcq_,
                      false);
    }

    void* tag;
    bool ok;

    LOG(INFO) << "HandleRPCsDcq Next...";
    while (dcq_->Next(&tag, &ok)) {
      if (FlDebugging()) {
        LOG(INFO) << "HandleRPCsDcq Next Event... " << _RpcDEventCnt++;
      }
      UntypedCall<RpcBridgeAgentService>::Tag* callback_tag =
          static_cast<UntypedCall<RpcBridgeAgentService>::Tag*>(tag);
      CHECK(callback_tag);
      callback_tag->OnCompleted(this, ok);
    }
    LOG(INFO) << "rpc_bridge_data_load_service HandleRPCsDcq exit...";
  }

  void HandleRPCsLoop() {
    thread_t_.reset(Env::Default()->StartThread(ThreadOptions(),
                                                "rpc_bridge_agent_service",
                                                [this]() { HandleRPCsTcq(); }));

    thread_d_.reset(Env::Default()->StartThread(ThreadOptions(),
                                                "rpc_bridge_data_load_service",
                                                [this]() { HandleRPCsDcq(); }));
  }

 private:
  void Schedule(std::function<void()> f) {
    // Env::Default()->Schedule(std::move(f));
    LOG(FATAL) << "Invalid, NOT implemented";
  }

  template <class RequestMessage, class ResponseMessage>
  using UnaryCall =
      Call<RpcBridgeAgentService, TrainerWorkerService::AsyncService,
           RequestMessage, ResponseMessage>;

  void TransmitHandler(
      UnaryCall<TrainerWorkerMessage, TrainerWorkerResponse>* call) {
    if (FlDebugging()) {
      LOG(INFO) << "TransmitHandler Event... " << _RpcUnaryCallEventCnt++;
    }

    if (!LinkUp()) {
      LOG(ERROR) << "Drop Received:"
                 << " seq num " << call->request.seq_num() << ", msg_type "
                 << call->request.msg_case()
                 << " , link not ready, probably because not recv peer connect "
                    "request.";
      call->SendResponse(::grpc::Status::CANCELLED);
      ENQUEUE_REQUEST(Transmit, TrainerWorkerMessage, TrainerWorkerResponse,
                      tcq_, false);
      return;
    }

    bridge_mgr_->StateMove(RpcBridgeMgr::BrState::STARTED);

    if (call->request.seq_num() ==
        bridge_mgr_->StepStats()->CurrentRecvSeqNum()) {
      Status s = cache_->OnReceived(&call->request);
      if (s.ok()) {
        call->response.mutable_status()->set_result_code(ResultCode::SUCCESS);
        call->response.mutable_status()->clear_error_message();
        call->response.set_next_seq_num(
            bridge_mgr_->StepStats()->NextRecvSeqNum());
      } else {
        call->response.mutable_status()->set_result_code(
            ResultCode::UNKNOWN_ERROR);
        call->response.mutable_status()->set_error_message(s.error_message());
        call->response.set_next_seq_num(
            bridge_mgr_->StepStats()->CurrentRecvSeqNum());
      }
      if (FlDebugging()) {
        LOG(INFO) << "Resp msg_type " << call->request.msg_case()
                  << ", next seq num: "
                  << bridge_mgr_->StepStats()->CurrentRecvSeqNum();
      }
    } else {
      ResultCode ret_code = (call->request.seq_num() >
                             bridge_mgr_->StepStats()->CurrentRecvSeqNum())
                                ? (ResultCode::MESSAGE_MISSING)
                                : (ResultCode::MESSAGE_DUPLICATED);

      LOG(ERROR) << "Drop Received "
                 << "seq num " << call->request.seq_num() << ", msg_type "
                 << call->request.msg_case() << ", want seq num "
                 << bridge_mgr_->StepStats()->CurrentRecvSeqNum()
                 << ", ack err_code " << ret_code;

      call->response.mutable_status()->set_result_code(ret_code);
      // call->response.mutable_status()->set_error_message("reject");
      call->response.mutable_status()->clear_error_message();
      call->response.set_next_seq_num(
          bridge_mgr_->StepStats()->CurrentRecvSeqNum());
    }

    call->SendResponse(::grpc::Status::OK);
    ENQUEUE_REQUEST(Transmit, TrainerWorkerMessage, TrainerWorkerResponse, tcq_,
                    false);
  }

  template <class RequestMessage, class ResponseMessage>
  using StreamingCall =
      ServerBidirectionalStreamingCall<RpcBridgeAgentService,
                                       TrainerWorkerService::AsyncService,
                                       RequestMessage, ResponseMessage>;

  void StreamTransmitHandler(
      StreamingCall<TrainerWorkerMessage, TrainerWorkerResponse>* call) {
    if (FlDebugging()) {
      LOG(INFO) << "StreamTransmitHandler Event ... "
                << _RpcStreamingCallEventCnt++;
    }

    if (!LinkUp()) {
      LOG(ERROR) << "Drop Received:"
                 << " seq num " << call->request().seq_num() << ", msg_type "
                 << call->request().msg_case()
                 << " , link not ready, probably because not recv peer connect "
                    "request.";
      call->Finish(::grpc::Status::CANCELLED);
      return;
    }

    bridge_mgr_->StateMove(RpcBridgeMgr::BrState::STARTED);

    if (call->request().seq_num() ==
        bridge_mgr_->StepStats()->CurrentRecvSeqNum()) {
      Status s = cache_->OnReceived(&call->request());
      if (s.ok()) {
        call->mutable_response()->mutable_status()->set_result_code(
            ResultCode::SUCCESS);
        call->mutable_response()->mutable_status()->clear_error_message();
        call->mutable_response()->set_next_seq_num(
            bridge_mgr_->StepStats()->NextRecvSeqNum());
      } else {
        call->mutable_response()->mutable_status()->set_result_code(
            ResultCode::UNKNOWN_ERROR);
        call->mutable_response()->mutable_status()->set_error_message(
            s.error_message());
        call->mutable_response()->set_next_seq_num(
            bridge_mgr_->StepStats()->CurrentRecvSeqNum());
      }
      if (FlDebugging()) {
        LOG(INFO) << "Resp msg_type " << call->request().msg_case()
                  << ", next seq num: "
                  << bridge_mgr_->StepStats()->CurrentRecvSeqNum();
      }
    } else {
      ResultCode ret_code = (call->request().seq_num() >
                             bridge_mgr_->StepStats()->CurrentRecvSeqNum())
                                ? (ResultCode::MESSAGE_MISSING)
                                : (ResultCode::MESSAGE_DUPLICATED);

      LOG(ERROR) << "Drop Received "
                 << "seq num " << call->request().seq_num() << ", msg_type "
                 << call->request().msg_case() << ", want seq num "
                 << bridge_mgr_->StepStats()->CurrentRecvSeqNum()
                 << ", ack err_code " << ret_code;

      call->mutable_response()->mutable_status()->set_result_code(ret_code);
      // call->mutable_response()->mutable_status()->set_error_message("reject");
      call->mutable_response()->mutable_status()->clear_error_message();
      call->mutable_response()->set_next_seq_num(
          bridge_mgr_->StepStats()->CurrentRecvSeqNum());
    }

    call->SendResponse();
  }

  void LoadDataBlockHandler(
      UnaryCall<LoadDataBlockRequest, ResultStatus>* call) {
    if (FlDebugging()) {
      LOG(INFO) << "LoadDataBlockHandler Event...";
    }
    if (!LinkUp()) {
      LOG(ERROR)
          << "Drop LoadDataBlock request, "
             "link not ready, probably because not recv peer connect request.";
      call->SendResponse(::grpc::Status::CANCELLED);
      ENQUEUE_REQUEST(LoadDataBlock, LoadDataBlockRequest, ResultStatus, dcq_,
                      false);
      return;
    }

    bridge_mgr_->StateMove(RpcBridgeMgr::BrState::STARTED);

    if (bridge_mgr_->RoleDef() == RoleDef_Follower) {
      LOG(INFO) << " Handle DataBlock load ... " << _RpcDataLoadEventCnt++;

      Status s;
      std::string src_fname, out_fname;
      DcInterface* db_api = bridge_mgr_->dc_impl();

      bool end_of_blocks = false;
      do {
        if (!db_api) {
          LOG(ERROR) << "DbAgent not initialized.";
          s = errors::Aborted("DbAgent not initialized.");
          break;
        }

        FetchDataBlockRequest request;
        FetchDataBlockResponse response;
        request.set_request_id(call->request.block_id());
        if (call->request.block_id().empty()) {
          LOG(INFO) << "DataBlock Id empty, train files end.";
          end_of_blocks = true;
          // End handler
          break;
        }

        LOG(INFO) << "Fetch DataBlock: " << call->request.block_id()
                  << ", count " << call->request.count();

        // drop duplicated datablock
        if (call->request.count() < _RpcDataLoadNextId) {
          LOG(ERROR) << "Drop duplicated DataBlock " << call->request.block_id()
                     << ", count " << call->request.count()
                     << ", want next count " << _RpcDataLoadNextId;
          call->response.set_result_code(ResultCode::SUCCESS);
          call->response.clear_error_message();
          call->SendResponse(::grpc::Status::OK);
          ENQUEUE_REQUEST(LoadDataBlock, LoadDataBlockRequest, ResultStatus,
                          dcq_, false);
          return;
        }

        int retries = 0;
        int64 sleep_in_ms = 10 * 1000 * 1000;

        do {
          if (retries++ > 10) {
            s = errors::Aborted("Fetch DataBlock, already Try max times ... ");
            // End handler
            break;
          }
          // Fetch data block full path name.
          s = db_api->FetchDataBlock(&request, &response);
          if (!s.ok()) {
            LOG(ERROR) << "Fetch DataBlock failed: " << s.error_message();
            LOG(INFO) << "Sleeping for: " << sleep_in_ms;
            Env::Default()->SleepForMicroseconds(sleep_in_ms);
            continue;
          }

          if (response.status_code() == StatusCode::FINISHED) {
            LOG(INFO) << "DataBlock FINISHED... ";
            s = errors::Aborted("DataBlock FINISHED...");
            break;
          } else if (response.status_code() == StatusCode::ERROR_ABORTED) {
            LOG(ERROR) << "DataBlock ERROR_ABORTED... ";
            s = errors::Aborted("DataBlock ERROR_ABORTED...");
            break;
          } else if (response.status_code() == StatusCode::NOT_READY) {
            LOG(INFO) << "DataBlock not ready ... ";
            LOG(INFO) << "Sleeping for: " << sleep_in_ms;
            Env::Default()->SleepForMicroseconds(sleep_in_ms);
            continue;
          } else if (response.status_code() == StatusCode::OK) {
            //
            // next step
            //
          } else {
            LOG(ERROR) << "DataBlock request ABORTED, unknown code "
                       << response.status_code();
            s = errors::Aborted("DataBlock request ABORTED, unknown code");
            break;
          }

          // prepare file, may copy to local
          src_fname = response.db_info().data_path();
          int ret = PrepareFile(src_fname, &out_fname);
          if (ret) {
            LOG(ERROR) << "DataBlock download failed: " << src_fname;
            s = errors::Aborted("DataBlock download failed.");
            // End handler, failed.
            break;
          }

          // End handler, send response
          break;
        } while (true);

        // End handler, send response
      } while (false);

      if (s.ok()) {
        cache_->OnReceived((!end_of_blocks) ? out_fname : "", end_of_blocks);
        call->response.set_result_code(ResultCode::SUCCESS);
        call->response.clear_error_message();
        call->SendResponse(::grpc::Status::OK);
        _RpcDataLoadNextId++;
      } else {
        call->response.set_result_code(ResultCode::UNKNOWN_ERROR);
        call->response.set_error_message(s.error_message());
        call->SendResponse(::grpc::Status::OK);
      }
    } else {
      LOG(ERROR) << "LoadDataBlockHandler Event... cannot handle dataload ...";
      call->response.set_result_code(ResultCode::INVALID_REQUEST);
      call->response.set_error_message(
          "Not follower, cannot handle dataload request...");
      call->SendResponse(::grpc::Status::OK);
    }
    ENQUEUE_REQUEST(LoadDataBlock, LoadDataBlockRequest, ResultStatus, dcq_,
                    false);
  }

  void ConnectHandler(UnaryCall<ConnectRequest, ConnectResponse>* call) {
    if (FlDebugging()) {
      LOG(INFO) << "ConnectHandler Event...";
    }

    if (RpcBridgeMgr::BrState::NEW == bridge_mgr_->State()) {
      bool chk_ok = true;
      do {
        if ((call->request.app_id() != bridge_mgr_->AppID()) ||
            (call->request.worker_rank() != bridge_mgr_->RankID())) {
          LOG(ERROR) << "Reject connect request: "
                     << "appli_id: " << call->request.app_id()
                     << ", worker_rank: " << call->request.worker_rank()
                     << ", identifier: " << call->request.identifier();
          chk_ok = false;
          break;
        }
        cache_->OnReceived(&call->request);
        link_up_ = true;
      } while (false);

      call->response.set_app_id(bridge_mgr_->AppID());
      call->response.set_worker_rank(bridge_mgr_->RankID());
      call->SendResponse((chk_ok) ? (::grpc::Status::OK)
                                  : (::grpc::Status::CANCELLED));
      ENQUEUE_REQUEST(Connect, ConnectRequest, ConnectResponse, tcq_, false);
    } else {
      LOG(ERROR) << "****************************************** ";
      LOG(ERROR) << "RpcBridgeAgent not in IDLE state, abort ... ";
      LOG(ERROR) << "****************************************** ";
      link_up_ = false;
      call->SendResponse(::grpc::Status::CANCELLED);
      Env::Default()->SleepForMicroseconds(3 * 1000 * 1000);
      exit(138);
      ENQUEUE_REQUEST(Connect, ConnectRequest, ConnectResponse, tcq_, false);
    }
  }

  void HeartbeatHandler(UnaryCall<HeartbeatRequest, HeartbeatResponse>* call) {
    if (FlDebugging()) {
      LOG(INFO) << "HeartbeatHandler Event...";
    }
    call->response.set_app_id(bridge_mgr_->AppID());
    call->response.set_worker_rank(bridge_mgr_->RankID());
    call->response.set_current_iter_id(
        bridge_mgr_->StepStats()->CurrentIterId());

    call->SendResponse(::grpc::Status::OK);
    ENQUEUE_REQUEST(Heartbeat, HeartbeatRequest, HeartbeatResponse, tcq_,
                    false);
  }

 private:
  TrainerWorkerService::AsyncService agent_service_;
  std::unique_ptr<::grpc::ServerCompletionQueue>
      tcq_;  // for train result transmit
  std::unique_ptr<::grpc::ServerCompletionQueue> dcq_;  // for data load
  std::unique_ptr<Thread> thread_t_;
  std::unique_ptr<Thread> thread_d_;

  RpcBridgeRecvCache* cache_{nullptr};  // not owned
  RpcBridgeMgr* bridge_mgr_{nullptr};   // not owned
  mutex service_shutdown_mu_;
  bool is_shutdown_ GUARDED_BY(service_shutdown_mu_);
  bool link_up_{false};
  bool LinkUp() { return link_up_; }

  TF_DISALLOW_COPY_AND_ASSIGN(RpcBridgeAgentService);
};

std::unique_ptr<AsyncServiceInterface> NewRpcBridgeAgentService(
    ::grpc::ServerBuilder* builder, RpcBridgeRecvCache* service_cache,
    RpcBridgeMgr* bridge_mgr) {
  return std::unique_ptr<AsyncServiceInterface>(
      new RpcBridgeAgentService(builder, service_cache, bridge_mgr));
}
}  // namespace jdfl
