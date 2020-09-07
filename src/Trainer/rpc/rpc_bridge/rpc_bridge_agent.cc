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

#include <functional>
#include <utility>
#include <memory>
#include <queue>

#include "grpcpp/generic/generic_stub.h"
#include "grpcpp/grpcpp.h"

#include "tensorflow/core/distributed_runtime/rpc/grpc_client_cq_tag.h"
#include "tensorflow/core/distributed_runtime/rpc/grpc_state.h"
#include "tensorflow/core/lib/core/errors.h"
#include "tensorflow/core/lib/core/status.h"
#include "tensorflow/core/lib/core/threadpool.h"
#include "tensorflow/core/lib/strings/str_util.h"
#include "tensorflow/core/platform/logging.h"
#include "tensorflow/core/platform/tracing.h"
#include "tensorflow/core/util/env_var.h"

#include "tensorflow/contrib/jdfl/rpc/rpc_bridge/fl_rpc_state.h"
#include "tensorflow/contrib/jdfl/rpc/rpc_bridge/rpc_bridge_agent.h"
#include "tensorflow/contrib/jdfl/rpc/rpc_bridge/rpc_bridge_agent_service.h"
#include "tensorflow/contrib/jdfl/rpc/rpc_bridge/rpc_bridge_mgr.h"

using namespace ::tensorflow;

namespace jdfl {

const char* RpcAgentMethodName(RpcBridgeAgentMethod id) {
  switch (id) {
    case RpcBridgeAgentMethod::kTransmit:
      return "/jdfl.TrainerWorkerService/Transmit";
    case RpcBridgeAgentMethod::kStreamTransmit:
      return "/jdfl.TrainerWorkerService/StreamTransmit";
    case RpcBridgeAgentMethod::kLoadDataBlock:
      return "/jdfl.TrainerWorkerService/LoadDataBlock";
    case RpcBridgeAgentMethod::kConnect:
      return "/jdfl.TrainerWorkerService/Connect";
    case RpcBridgeAgentMethod::kHeartbeat:
      return "/jdfl.TrainerWorkerService/Heartbeat";
  }
  // Shouldn't be reached.
  LOG(ERROR) << "Invalid id: this line shouldn't be reached.";
  return "invalid id";
}

const char* const FL_Key_Prefetch = "/FlPrefetch";
const char* const FL_Key_TrainStart = "/FlTrainStart";
// const char* const FL_Key_TrainStartCommit = "/FlTrainStartCommit";
const char* const FL_Key_StepCommit = "/FlStepCommit";
const char* const FL_Key_DataMessage = "/FlDataMessage";
const char* const FL_Key_Connect = "/FlConnect";
const char* const FL_Key_Heartbeat = "/FlHeartbeat";
const char* const FL_Key_LoadDatablock = "/FlLoadDatablock";

std::string RpcAgentMethodRecvKey(BridgeMethodIndex id) {
  switch (id) {
    case BridgeMethodIndex::kRpcPrefetch:
      return FL_Key_Prefetch;
    case BridgeMethodIndex::kRpcTrainStart:
      return FL_Key_TrainStart;
    case BridgeMethodIndex::kRpcStepCommit:
      return FL_Key_StepCommit;
    case BridgeMethodIndex::kRpcDataMessage:
      return FL_Key_DataMessage;
    case BridgeMethodIndex::kRpcLoadDataBlock:
      return FL_Key_LoadDatablock;
    case BridgeMethodIndex::kRpcConnect:
      return FL_Key_Connect;
    case BridgeMethodIndex::kRpcHeartbeat:
      return FL_Key_Heartbeat;
  }
  // Shouldn't be reached.
  LOG(ERROR) << "Invalid id: this line shouldn't be reached.";
  return "invalid id";
}

const int kMaxWorkerRpcRetries = 1;

const int kQueryResultWaitTime = (1 * 1000 * 1000);

static int64 _RpcEventCnt = 0;

class RpcBridgeAgent : public BridgeInterface {
 public:
  explicit RpcBridgeAgent(SharedGrpcChannelPtr channel,
                          ::grpc::CompletionQueue* completion_queue,
                          RpcBridgeRecvCache* service_cache,
                          RpcBridgeMgr* bridge_mgr)
      : channel_(std::move(channel)),
        stub_(channel_),
        cq_(completion_queue),
        bridge_cache_(service_cache),
        bridge_mgr_(bridge_mgr),
        fl_transmit_rpcmethod_(Method(RpcBridgeAgentMethod::kTransmit)),
        fl_streamtransmit_rpcmethod_(
            Method(RpcBridgeAgentMethod::kStreamTransmit)),
        fl_loaddatablock_rpcmethod_(
            Method(RpcBridgeAgentMethod::kLoadDataBlock)),
        fl_connect_rpcmethod_(Method(RpcBridgeAgentMethod::kConnect)),
        fl_heartbeat_rpcmethod_(Method(RpcBridgeAgentMethod::kHeartbeat)) {
    bool result;
    ReadBoolFromEnvVar("_CLIENT_STREAMING_SYNC_WRITE", false, &result);
    streaming_sync_write_ = result;
    LOG(INFO) << "ENV _CLIENT_STREAMING_SYNC_WRITE: " << streaming_sync_write_;

    auto it_and_bool = dispatchers_.emplace(
        std::piecewise_construct, std::forward_as_tuple(0),
        std::forward_as_tuple(&stub_, cq_, fl_streamtransmit_rpcmethod_,
                              bridge_mgr_->ContexMeta()));

    polling_thread_.reset(Env::Default()->StartThread(
        ThreadOptions(), "rpc_bridge_agent", [this]() {
          void* tag;
          bool ok;
          LOG(INFO) << "rpc_bridge_agent start.";
          while (cq_->Next(&tag, &ok)) {
            if (FlDebugging()) {
              LOG(INFO) << "BridgeAgent Next event ..." << _RpcEventCnt++;
            }
            GrpcClientCQTag* callback_tag = static_cast<GrpcClientCQTag*>(tag);
            callback_tag->OnCompleted(ok);
          }
          LOG(INFO) << "rpc_bridge_agent thread exit.";
        }));

    retry_chk_thread_.reset(
        Env::Default()->StartThread(ThreadOptions(), "rpc_retry_chk", [this]() {
          LOG(INFO) << "rpc_retry_chk start.";

          int64 sleep_in_ms = 1 * 1000 * 1000;

          while (!is_shutdown_) {
            auto it = dispatchers_.find(0);
            if (it == dispatchers_.end()) {
              LOG(ERROR) << "FlStreamingRPCDispatcher not created";
              break;
            }

            do {
              if (!msg_reexchanges_.empty()) {
                LOG(INFO) << "Resend start, re-exchanges Q size: "
                          << msg_reexchanges_.size();
                break;
              }

              Notification n;
              {
                mutex_lock l(dispatch_mu_);
                retry_cb_ = [&n](const Status& s) { n.Notify(); };
              }

              n.WaitForNotification();
              retry_mu_.lock();
              retry_cb_ = nullptr;
              retry_mu_.unlock();
              if (is_shutdown_) {
                break;
              }

              {
                retry_mu_.lock();
                DumpQueue(msg_reexchanges_);
                retry_mu_.unlock();
              }

              {
                mutex_lock l(dispatch_mu_);
                it->second.ResetCall();
                LOG(INFO) << "Resend start, re-exchanges Q size: "
                          << msg_reexchanges_.size();
              }
            } while (false);

            while (!msg_reexchanges_.empty()) {
              retry_mu_.lock();
              if (msg_reexchanges_.empty()) {
                LOG(WARNING) << "reexchanges empty";
                retry_mu_.unlock();
                break;
              }
              auto elem = msg_reexchanges_.top();
              msg_reexchanges_.pop();
              auto req_ptr = elem.request_ptr_;
              auto resp_ptr = elem.response_ptr_;
              retry_mu_.unlock();

              Status s;
              for (;;) {
                Notification nn;
                Status ret;
                LOG(INFO) << "Try Resend: msg_type " << req_ptr->msg_case()
                          << ", seq " << req_ptr->seq_num() << ", request "
                          << req_ptr << ", response " << resp_ptr;
                {
                  mutex_lock l(dispatch_mu_);
                  s = it->second.SendNextRequest(
                      *req_ptr, resp_ptr,
                      [&ret, &nn, sleep_in_ms](const Status& st) {
                        ret = st;
                        nn.Notify();
                      });
                }

                if (!s.ok()) {
                  // enqueue failed. retry
                  LOG(WARNING) << "Enqueue failed: " << s.ToString();
                  LOG(INFO) << "Sleeping for: " << sleep_in_ms;
                  Env::Default()->SleepForMicroseconds(sleep_in_ms);
                  {
                    mutex_lock l(dispatch_mu_);
                    it->second.ResetCall();
                  }
                  continue;
                }

                nn.WaitForNotification();
                if (!ret.ok()) {
                  // cq event failed. retry
                  LOG(WARNING)
                      << "Resend, CQ event notify failed: " << ret.ToString();
                  LOG(INFO) << "Sleeping for: " << sleep_in_ms;
                  Env::Default()->SleepForMicroseconds(sleep_in_ms);
                  {
                    mutex_lock l(dispatch_mu_);
                    it->second.ResetCall();
                  }
                  continue;
                }

                LOG(INFO) << "Resend OK: msg_type " << req_ptr->msg_case()
                          << ", seq " << req_ptr->seq_num() << ", request "
                          << req_ptr << ", response " << resp_ptr;

                if ((resp_ptr->mutable_status()->result_code() !=
                     ResultCode::SUCCESS) ||
                    !(resp_ptr->mutable_status()->error_message().empty())) {
                  LOG(INFO) << "response ret_code: "
                            << resp_ptr->mutable_status()->result_code()
                            << ", err_info: "
                            << resp_ptr->mutable_status()->error_message();
                }

                // TODO(wyw):
                //  deadlock ???
                if ((resp_ptr->mutable_status()->result_code() ==
                     ResultCode::SUCCESS) ||
                    ((resp_ptr->mutable_status()->result_code() ==
                      ResultCode::MESSAGE_DUPLICATED))) {
                  delete req_ptr;
                  delete resp_ptr;
                  LOG(INFO) << "reexchanges queue remain size: "
                            << msg_reexchanges_.size();
                  break;
                }

                // continue;
              }
              // resend next msg
            }

            if (msg_reexchanges_.size()) {
              LOG(ERROR) << "*** BUG: Unexpected reexchanges size, "
                         << msg_reexchanges_.size();
            }

            LOG(INFO) << "Channel state move to kActive";
            channel_state_ = ChannelState::kActive;
          }
          LOG(INFO) << "rpc_retry_chk thread exit.";
        }));
  }

  ~RpcBridgeAgent() override {
    mutex_lock l(dispatch_mu_);
    is_shutdown_ = true;
    channel_state_ = ChannelState::kInactive;

    LOG(WARNING) << "Clean reexchanges cache.";
    retry_mu_.lock();
    while (!msg_reexchanges_.empty()) {
      auto elem = msg_reexchanges_.top();
      msg_reexchanges_.pop();
      delete elem.request_ptr_;
      delete elem.response_ptr_;
    }
    if (retry_cb_) {
      retry_cb_(Status::OK());
    }
    retry_mu_.unlock();
    LOG(WARNING) << "Clean reexchanges cache Done.";

    LOG(INFO) << "~RpcBridgeAgent() done.";
  }

  bool StreamingCall() {
    return (bridge_mgr_->ServiceType() == KindOfServiceType::kBidiStreaming);
  }

  bool CallSync() { return (streaming_sync_write_); }

  void RequestWriteLock() override { request_write_mu_.lock(); }

  void RequestWriteCompletedUnlock() override { request_write_mu_.unlock(); }

  void TrainStartInternal() {
    bridge_mgr_->StateMove(RpcBridgeMgr::BrState::STARTED);
  }

  template <typename T>
  void DumpQueue(const T& q) {
    T dup = q;
    LOG(INFO) << "**** Dump retry queue: ";
    while (!dup.empty()) {
      auto elem = dup.top();
      dup.pop();
      LOG(INFO) << "request " << elem.request_ptr_ << ", seq " << elem.seq_num_
                << "(" << elem.request_ptr_->seq_num() << ")"
                << ", msg_type " << elem.request_ptr_->msg_case()
                << ", response " << elem.response_ptr_;
    }
  }

  void RequestTrainStartAsync(const TrainerWorkerMessage* request,
                              TrainerWorkerResponse* response,
                              StatusCallback done) override {
    TrainStartInternal();
    if (StreamingCall()) {
      if (!CallSync()) {
        Status s = StreamingRequestEnqueue(request, response);
        if (!s.ok()) {
          LOG(WARNING) << "TrainStart Enqueue failed: " << s.ToString();
        }
        done(s);
      } else {
        Notification n;
        Status status;
        StreamingIssueRequest(
            request, response, [this, &n, &status](const Status& s) {
              if (!s.ok()) {
                channel_state_ = ChannelState::kInactive;
                LOG(WARNING) << "TrainStart failed, err_info: " << s.ToString();
                LOG(WARNING) << "ChannelState move to kInactive";
              }
              status.Update(s);
              n.Notify();
            });
        n.WaitForNotification();
        done(status);
      }
    } else {
      IssueRequest(request, response, fl_transmit_rpcmethod_, std::move(done));
    }
  }

  void RequestTrainCommitAsync(const TrainerWorkerMessage* request,
                               TrainerWorkerResponse* response,
                               StatusCallback done) override {
    TrainStartInternal();
    if (StreamingCall()) {
      if (!CallSync()) {
        Status s = StreamingRequestEnqueue(request, response);
        if (!s.ok()) {
          LOG(WARNING) << "TrainCommit Enqueue failed: " << s.ToString();
        }
        done(s);
      } else {
        Notification n;
        Status status;
        StreamingIssueRequest(request, response, [this, &n,
                                                  &status](const Status& s) {
          if (!s.ok()) {
            channel_state_ = ChannelState::kInactive;
            LOG(WARNING) << "TrainCommit failed:, err_info: " << s.ToString();
            LOG(WARNING) << "ChannelState move to kInactive";
          }
          status.Update(s);
          n.Notify();
        });
        n.WaitForNotification();
        done(status);
      }
    } else {
      IssueRequest(request, response, fl_transmit_rpcmethod_, std::move(done));
    }
  }

  Status QueryResult(int64 request_id, const std::string& slot_key,
                     const std::string& content_key,
                     TrainerWorkerMessage* result) override {
    Status ret;
    bridge_cache_->QueryResult(request_id, slot_key, content_key, result,
                               kQueryResultWaitTime,
                               [&ret](const Status& s) { ret = s; });
    return ret;
  }

  Status WaitPeerReady(int64 request_id, const std::string& slot_key,
                       ConnectRequest* peer) {
    Status ret;
    bridge_cache_->WaitPeerReady(0, slot_key, peer, kQueryResultWaitTime,
                                 [&ret](const Status& s) { ret = s; });
    return ret;
  }

  Status QueryReadyFile(int64 request_id, const std::string& slot_key,
                        std::string* result, bool* end_of_files) override {
    Status ret;
    bridge_cache_->QueryReadyFile(0, slot_key, result, end_of_files,
                                  kQueryResultWaitTime * 3,
                                  [&ret](const Status& s) { ret = s; });
    return ret;
  }

  void HeartbeatChkAsync(const HeartbeatRequest* request,
                         HeartbeatResponse* response, StatusCallback done) {
    IssueRequest(request, response, fl_heartbeat_rpcmethod_, std::move(done));
  }

  void RequestGradBpAsync(const TrainerWorkerMessage* request,
                          TrainerWorkerResponse* response,
                          StatusCallback done) override {
    if (StreamingCall()) {
      if (!CallSync()) {
        Status s = StreamingRequestEnqueue(request, response);
        if (!s.ok()) {
          LOG(WARNING) << "GradBp Enqueue failed: " << s.ToString();
        }
        done(s);
      } else {
        Notification n;
        Status status;
        StreamingIssueRequest(
            request, response, [this, &n, &status](const Status& s) {
              if (!s.ok()) {
                channel_state_ = ChannelState::kInactive;
                LOG(WARNING) << "GradBp failed, err_info: " << s.ToString();
                LOG(WARNING) << "ChannelState move to kInactive";
              }
              status.Update(s);
              n.Notify();
            });
        n.WaitForNotification();
        done(status);
      }
    } else {
      IssueRequest(request, response, fl_transmit_rpcmethod_, std::move(done));
    }
  }

  void RequestDateTransmitAsync(const TrainerWorkerMessage* request,
                                TrainerWorkerResponse* response,
                                StatusCallback done) override {
    TrainStartInternal();
    if (StreamingCall()) {
      if (!CallSync()) {
        Status s = StreamingRequestEnqueue(request, response);
        if (!s.ok()) {
          LOG(WARNING) << "DateTransmit Enqueue failed: " << s.ToString();
        }
        done(s);
      } else {
        Notification n;
        Status status;
        StreamingIssueRequest(request, response, [this, &n,
                                                  &status](const Status& s) {
          if (!s.ok()) {
            channel_state_ = ChannelState::kInactive;
            LOG(WARNING) << "DateTransmit failed, err_info: " << s.ToString();
            LOG(WARNING) << "ChannelState move to kInactive";
          }
          status.Update(s);
          n.Notify();
        });
        n.WaitForNotification();
        done(status);
      }
    } else {
      IssueRequest(request, response, fl_transmit_rpcmethod_, std::move(done));
    }
  }

  void RequestConnectAsync(const ConnectRequest* request,
                           ConnectResponse* response,
                           StatusCallback done) override {
    IssueRequest(request, response, fl_connect_rpcmethod_, std::move(done));
  }

  void RequestLoadDataBlockAsync(const LoadDataBlockRequest* request,
                                 ResultStatus* response,
                                 StatusCallback done) override {
    TrainStartInternal();
    IssueRequest(request, response, fl_loaddatablock_rpcmethod_,
                 std::move(done));
  }

 private:
  template <class Response>
  void IssueRequest(const protobuf::Message* request, Response* response,
                    const ::grpc::string& method, StatusCallback done,
                    CallOptions* call_opts = nullptr,
                    int max_retries = kMaxWorkerRpcRetries) {
    if (FlDebugging()) {
      LOG(INFO) << "IssueRequest: " << method;
    }
    auto tag = new FlRPCState<Response>(
        &stub_, cq_, bridge_mgr_->ContexMeta(), method, *request, response,
        std::move(done), call_opts, nullptr, true, kQueryResultWaitTime,
        max_retries);
    if (FlDebugging()) {
      LOG(INFO) << "IssueRequest: " << method << ", tag " << tag;
    }
  }

  Status StreamingRequestEnqueue(const TrainerWorkerMessage* request,
                                 TrainerWorkerResponse* unused_response) {
    mutex_lock l(dispatch_mu_);
    Status s;

    if (channel_state_ != ChannelState::kActive) {
      // TODO(wyw): insert queue instead of return error
      s = errors::Unavailable("rpc channel not active");
      return s;
    }

    auto it = dispatchers_.find(0);
    if (it == dispatchers_.end()) {
      s = errors::Unavailable("FlStreamingRPCDispatcher not created");
      return s;
    }

    // TODO(wyw): Optimize to avoid copy.
    TrainerWorkerMessage* copy_req = new TrainerWorkerMessage;
    *copy_req = *request;
    // no wait response
    TrainerWorkerResponse* resp_ptr = new TrainerWorkerResponse;

    retry_mu_.lock();
    msg_exchanges_.push_back(resp_ptr);
    msg_exchanges_cache_[resp_ptr] = copy_req;
    retry_mu_.unlock();

    if (FlDebugging() > 49) {
      LOG(INFO) << "Streaming request " << copy_req << ", msg_type "
                << copy_req->msg_case() << ", seq num " << copy_req->seq_num()
                << ", response " << resp_ptr;
    }

    s = it->second.SendNextRequest(*copy_req, resp_ptr, [this, resp_ptr](
                                                            const Status& s) {
      retry_mu_.lock();
      auto it = msg_exchanges_cache_.find(resp_ptr);
      if (it == msg_exchanges_cache_.end()) {
        LOG(ERROR) << "*** BUG : Unexpected missing cache entry for request "
                   << resp_ptr;
        delete resp_ptr;
        retry_mu_.unlock();
        return;
      }

      auto req_ptr = it->second;
      msg_exchanges_cache_.erase(resp_ptr);
      auto e = msg_exchanges_.front();
      msg_exchanges_.pop_front();
      retry_mu_.unlock();

      if (s.ok()) {
        if (FlDebugging() > 49) {
          LOG(INFO) << "gRPC Send OK: seq " << req_ptr->seq_num()
                    << ", deque pop " << e << ", request " << req_ptr
                    << ", response " << resp_ptr;
        }

        if ((resp_ptr->mutable_status()->result_code() !=
             ResultCode::SUCCESS) ||
            !(resp_ptr->mutable_status()->error_message().empty())) {
          LOG(INFO) << "response ret_code: "
                    << resp_ptr->mutable_status()->result_code()
                    << ", err_info: "
                    << resp_ptr->mutable_status()->error_message();
        }

        if (resp_ptr->mutable_status()->result_code() == ResultCode::SUCCESS) {
          if (FlDebugging() > 49) {
            LOG(INFO) << "Resp OK. seq " << req_ptr->seq_num();
          }
          delete req_ptr;
          delete resp_ptr;
        } else if (resp_ptr->mutable_status()->result_code() ==
                   ResultCode::MESSAGE_DUPLICATED) {
          LOG(INFO) << "Resp duplicated. seq " << req_ptr->seq_num();
          delete req_ptr;
          delete resp_ptr;
        } else {
          // TODO(wyw): what error ???
          //  deadlock ???
          LOG(INFO) << "Resp err, retry... seq " << req_ptr->seq_num();

          // Disable Request, wait retry finish
          channel_state_ = ChannelState::kInactive;

          // Add to retry queue
          retry_mu_.lock();
          msg_reexchanges_.emplace(req_ptr->seq_num(), req_ptr, resp_ptr);
          retry_mu_.unlock();
        }

        retry_mu_.lock();
        // last msg in exchanges queue
        if (msg_exchanges_.empty() && !(msg_reexchanges_.empty())) {
          LOG(INFO) << "trigger re-send.";
          if (retry_cb_) {
            retry_cb_(s);
          }
        }
        retry_mu_.unlock();

      } else {
        LOG(WARNING) << "Send failed, retry later, err_info: " << s.ToString();

        // Disable Request, wait channel recover
        channel_state_ = ChannelState::kInactive;

        if (!is_shutdown_) {
          LOG(INFO) << "Add to retry queue, request " << req_ptr
                    << ", msg_type " << req_ptr->msg_case() << ", seq "
                    << req_ptr->seq_num() << ", response " << resp_ptr;

          // Add to retry queue
          retry_mu_.lock();
          msg_reexchanges_.emplace(req_ptr->seq_num(), req_ptr, resp_ptr);

          // last msg in exchanges queue
          if (msg_exchanges_.empty()) {
            LOG(INFO) << "trigger re-send. reexchange size "
                      << msg_reexchanges_.size();
            if (retry_cb_) {
              retry_cb_(s);
            }
          }
          retry_mu_.unlock();

        } else {
          LOG(ERROR) << "Send failed, no retry, request " << req_ptr
                     << ", msg_type " << req_ptr->msg_case() << ", seq "
                     << req_ptr->seq_num() << ", response " << resp_ptr;
          delete req_ptr;
          delete resp_ptr;
        }
      }

      if (FlDebugging() > 49) {
        LOG(INFO) << "exchanges deque remain size: " << msg_exchanges_.size()
                  << ", exchanges cache remain size: "
                  << msg_exchanges_cache_.size();
      }
    });

    if (!s.ok()) {
      retry_mu_.lock();
      auto it = msg_exchanges_cache_.find(resp_ptr);
      if (it == msg_exchanges_cache_.end()) {
        LOG(ERROR) << "*** BUG: Unexpected missing cache entry for request "
                   << resp_ptr;
        retry_mu_.unlock();
        return s;
      }

      auto req_ptr = it->second;
      msg_exchanges_cache_.erase(resp_ptr);
      auto e = msg_exchanges_.back();
      msg_exchanges_.pop_back();
      retry_mu_.unlock();

      LOG(WARNING) << "Send failed, " << s.ToString();
      LOG(WARNING) << "request " << req_ptr << ", msg_type "
                   << req_ptr->msg_case() << ", seq num " << req_ptr->seq_num()
                   << ", response " << resp_ptr;
      delete req_ptr;
      delete resp_ptr;
    }
    // TODO(wyw): fill response?
    return s;
  }

  Status StreamingIssueRequest(const TrainerWorkerMessage* request,
                               TrainerWorkerResponse* response,
                               StatusCallback done) {
    mutex_lock l(dispatch_mu_);
    Status s;

    auto it = dispatchers_.find(0);
    if (it == dispatchers_.end()) {
      s = errors::Unavailable("FlStreamingRPCDispatcher not created");
      done(s);
      return Status::OK();
    }

    // registry CallBack may change channel state
    if (channel_state_ != ChannelState::kActive) {
      LOG(WARNING) << "StreamingIssueRequest: rpc channel not active, reset "
                      "and retry ...";
      it->second.ResetCall();
      channel_state_ = ChannelState::kActive;
    }

    it->second.SendNextRequest(*request, response, std::move(done));
    return Status::OK();
  }

  // Helper function for initializing the RpcMethod objects below.
  const char* Method(RpcBridgeAgentMethod id) { return RpcAgentMethodName(id); }

  SharedGrpcChannelPtr channel_;
  ::grpc::GenericStub stub_;
  ::grpc::CompletionQueue* cq_;

  RpcBridgeRecvCache* bridge_cache_;
  RpcBridgeMgr* bridge_mgr_;

  const ::grpc::string fl_transmit_rpcmethod_;
  const ::grpc::string fl_streamtransmit_rpcmethod_;
  const ::grpc::string fl_loaddatablock_rpcmethod_;
  const ::grpc::string fl_connect_rpcmethod_;
  const ::grpc::string fl_heartbeat_rpcmethod_;

  bool streaming_sync_write_{false};

  mutable mutex request_write_mu_;

  std::unique_ptr<Thread> polling_thread_;
  std::unique_ptr<Thread> retry_chk_thread_;

  std::unordered_map<uint64, FlStreamingRPCDispatcher<TrainerWorkerResponse>>
      dispatchers_;

  mutable mutex dispatch_mu_;

  using RetryCB = std::function<void(const Status& status)>;
  enum class ChannelState {
    kActive,
    kInactive,
  };

  ChannelState channel_state_{ChannelState::kActive};

  bool is_shutdown_{false};

  struct ExchangeEntry {
    ExchangeEntry(int64_t seq, TrainerWorkerMessage* s,
                  TrainerWorkerResponse* r)
        : seq_num_(seq), request_ptr_(s), response_ptr_(r) {}
    int64_t seq_num_;
    TrainerWorkerMessage* request_ptr_;
    TrainerWorkerResponse* response_ptr_;
    inline bool operator<(const ExchangeEntry& rhs) const {
      return this->seq_num_ > rhs.seq_num_;
    }
  };

  // for rpc call failed retry
  mutable mutex retry_mu_;
  std::deque<TrainerWorkerResponse*> msg_exchanges_;
  std::unordered_map<TrainerWorkerResponse*, TrainerWorkerMessage*>
      msg_exchanges_cache_;
  std::priority_queue<ExchangeEntry> msg_reexchanges_;

  RetryCB retry_cb_;

  TF_DISALLOW_COPY_AND_ASSIGN(RpcBridgeAgent);
};

BridgeInterface* NewRpcBridgeAgent(SharedGrpcChannelPtr channel,
                                   ::grpc::CompletionQueue* completion_queue,
                                   RpcBridgeRecvCache* service_cache,
                                   RpcBridgeMgr* bridge_mgr) {
  return new RpcBridgeAgent(std::move(channel), completion_queue, service_cache,
                            bridge_mgr);
}

bool RpcBridgeRecvCache::QueryResult(int64 request_id,
                                     const std::string& slot_key,
                                     const std::string& content_key,
                                     TrainerWorkerMessage* request,
                                     int64 timeout_in_us, RecvFinishCB cb) {
  if (FlDebugging()) {
    LOG(INFO) << "QueryResult Start ... " << slot_key << "(" << content_key
              << ")";
  }

  mu_.lock();

  RecvCacheEntry& entry = recv_cache_[slot_key][content_key];

  if (entry.state == State::ACTIVE) {
    if (FlDebugging()) {
      LOG(INFO) << "result cached " << slot_key << "(" << content_key << ")";
    }

    *request = entry.rdata;
    entry.state = State::IDLE;
    entry.FinishRecv(cb);
    mu_.unlock();
    return true;
  } else {
    if (FlDebugging()) {
      LOG(INFO) << "No cache entry for " << slot_key << "(" << content_key
                << ")"
                << ", wait result...";
    }
    entry.state = State::WAIT;
    Notification n;
    entry.callback_ = [&n](const Status& s) { n.Notify(); };
    mu_.unlock();

    bool ok = WaitForNotificationWithTimeout(&n, timeout_in_us);
    mu_.lock();
    if (ok || (entry.state == State::ACTIVE)) {
      if (FlDebugging()) {
        LOG(INFO) << "Result Recv triggered: " << slot_key << "(" << content_key
                  << ")";
      }
      *request = entry.rdata;
      cb(Status::OK());
    } else {
      LOG(INFO) << "Wait(" << timeout_in_us << ") timeout " << slot_key << "("
                << content_key << ")";
      Status s = errors::Aborted("Timeout");
      cb(s);
    }
    entry.state = State::IDLE;
    entry.callback_ = nullptr;
    mu_.unlock();
    return ok;
  }
}

bool RpcBridgeRecvCache::WaitPeerReady(int64 request_id,
                                       const std::string& slot_key,
                                       ConnectRequest* peer,
                                       int64 timeout_in_us, RecvFinishCB cb) {
  if (FlDebugging()) {
    LOG(INFO) << "WaitPeerReady ... " << slot_key;
  }

  connect_mu_.lock();

  PeerInfo& worker = peer_info_;

  if (worker.peer_connected_) {
    *peer = worker.rdata;
    cb(Status::OK());
    connect_mu_.unlock();
    return true;
  } else {
    if (FlDebugging()) {
      LOG(INFO) << "Wait peer connected ...";
    }
    worker.state = State::WAIT;
    Notification n;
    worker.callback_ = [&n](const Status& s) { n.Notify(); };
    connect_mu_.unlock();

    bool ok = WaitForNotificationWithTimeout(&n, timeout_in_us);
    connect_mu_.lock();
    if (ok || (worker.state == State::ACTIVE)) {
      LOG(INFO) << "Peer Connect triggered...";
      *peer = worker.rdata;
      cb(Status::OK());
    } else {
      LOG(INFO) << "Wait Peer(" << timeout_in_us << ") timeout ";
      Status s = errors::Aborted("Timeout");
      cb(s);
    }
    worker.state = State::IDLE;
    worker.callback_ = nullptr;
    connect_mu_.unlock();
    return ok;
  }
}

bool RpcBridgeRecvCache::QueryReadyFile(int64 request_id,
                                        const std::string& slot_key,
                                        std::string* result, bool* end_of_files,
                                        int64 timeout_in_us, RecvFinishCB cb) {
  if (FlDebugging()) {
    LOG(INFO) << "QueryReadyFile  ... " << slot_key;
  }

  int retries = 0;

  do {
    q_mu_.lock();

    TrainFilesCache& fq_cache = files_ready_cache_;

    if (!fq_cache.q_files.empty()) {
      std::string fname = fq_cache.q_files.front();
      fq_cache.q_files.pop_front();
      *result = fname;
      cb(Status::OK());
      *end_of_files = false;
      q_mu_.unlock();
      return true;
    }

    if (fq_cache.state == State::ACTIVE) {
      cb(Status::OK());
      *result = "";
      *end_of_files = true;
      LOG(INFO) << "End of train.";
      q_mu_.unlock();
      return false;
    }

    if (retries++ > 1) {
      Status s = errors::Aborted("Already Try max times ... ");
      cb(s);
      *result = "";
      *end_of_files = false;
      q_mu_.unlock();
      return false;
    }

    if (FlDebugging()) {
      LOG(INFO) << "No cache for " << slot_key << ", wait ...";
    }
    fq_cache.state = State::WAIT;
    Notification n;
    fq_cache.callback_ = [&n](const Status& s) { n.Notify(); };
    q_mu_.unlock();

    bool ok = WaitForNotificationWithTimeout(&n, timeout_in_us);
    q_mu_.lock();
    fq_cache.callback_ = nullptr;
    if (!ok) {
      LOG(INFO) << "Wait(" << timeout_in_us << ") timeout " << slot_key;
    }
    q_mu_.unlock();
    // re-check
  } while (true);

  return false;
}

Status RpcBridgeRecvCache::OnReceived(const TrainerWorkerMessage* recvdata) {
  mutex_lock m(mu_);

  std::string slot_key;
  std::string content_key;
  bool wait_consumer = false;

  switch (recvdata->msg_case()) {
    case TrainerWorkerMessage::kPrefetch:
      slot_key = RpcAgentMethodRecvKey(BridgeMethodIndex::kRpcPrefetch);
      content_key = slot_key;
      LOG(ERROR) << "Prefetch msg Handler NOT implemented";
      break;
    case TrainerWorkerMessage::kStart:
      slot_key = RpcAgentMethodRecvKey(BridgeMethodIndex::kRpcTrainStart);
      content_key = slot_key;
      break;
    case TrainerWorkerMessage::kCommit:
      slot_key = RpcAgentMethodRecvKey(BridgeMethodIndex::kRpcStepCommit);
      content_key = slot_key;
      break;
    case TrainerWorkerMessage::kData:
      slot_key = RpcAgentMethodRecvKey(BridgeMethodIndex::kRpcDataMessage);
      content_key = recvdata->data().name();
      wait_consumer = true;
      break;
    default:
      LOG(ERROR) << "Invalid msg case:" << recvdata->msg_case();
      return errors::InvalidArgument("Invalid msg case:", recvdata->msg_case());
  }

  if ((logcnt_++ < 100) || FlDebugging()) {
    LOG(INFO) << "OnReceived " << slot_key << ", seq num "
              << recvdata->seq_num() << ", msg_type " << recvdata->msg_case()
              << ", msg_data " << content_key;
  }

  RecvCacheEntry* entry = nullptr;
  for (auto iter = recv_cache_[slot_key].begin();
       iter != recv_cache_[slot_key].end(); ++iter) {
    if (iter->first == content_key) {
      entry = &(iter->second);
      break;
    }
  }
  if (!entry) {
    LOG(INFO) << slot_key << " add new entry " << content_key;
    entry = &(recv_cache_[slot_key][content_key]);
  }

  if ((wait_consumer) && (entry->state == State::ACTIVE)) {
    LOG(ERROR) << "OnReceived reject, " << slot_key << ", seq num "
               << recvdata->seq_num() << ", msg_type " << recvdata->msg_case()
               << ", msg_data " << content_key;
    return errors::Aborted("Reject.");
  } else {
    entry->recv_status = Status::OK();
    entry->rdata = *recvdata;
    bool cb = (entry->state == State::WAIT) ? true : false;
    entry->state = State::ACTIVE;
    if (cb && entry->callback_) {
      entry->callback_(entry->recv_status);
    }
  }
  return Status::OK();
}

Status RpcBridgeRecvCache::OnReceived(const std::string& fname,
                                      bool end_of_files) {
  mutex_lock m(q_mu_);

  if (!end_of_files) {
    files_ready_cache_.q_files.push_back(fname);
    files_ready_cache_.recv_count++;
    LOG(INFO) << "Train file " << fname << " ready,"
              << " Q size: " << files_ready_cache_.q_files.size() << ", count "
              << files_ready_cache_.recv_count;
  } else {
    LOG(INFO) << "End of train files.";
    files_ready_cache_.state = State::ACTIVE;
  }
  if (files_ready_cache_.callback_) {
    files_ready_cache_.callback_(Status::OK());
  }

  return Status::OK();
}

Status RpcBridgeRecvCache::OnReceived(const ConnectRequest* peerdata) {
  mutex_lock m(connect_mu_);

  LOG(INFO) << "Recv ConnectRequest: "
            << "appli_id: " << peerdata->app_id()
            << ", worker_rank: " << peerdata->worker_rank()
            << ", identifier: " << peerdata->identifier();

  if (!peer_info_.peer_connected_) {
    peer_info_.peer_connected_ = true;
    peer_info_.rdata = *peerdata;
    peer_info_.state = State::ACTIVE;

    if (peer_info_.callback_) {
      peer_info_.callback_(Status::OK());
    }
  } else {
    LOG(INFO) << "Duplicated connected request.";
  }
  return Status::OK();
}

void RpcBridgeRecvCache::EraseRequestId(int64 request_id,
                                        const std::string& slot_key) {
  mutex_lock m(mu_);
  // recv_cache_.erase(slot_key);
}

void RpcBridgeRecvCache::CleanEntriesForKey(const std::string& slot_key) {
  mutex_lock m(mu_);
  // recv_cache_.erase(slot_key);
  // Remove all cache entries whose step id is the given step_id
  // for (auto it = recv_cache_.begin(), last = recv_cache_.end();
  //     it != last;) {
  //  if (it->second.step_id == step_id) {
  //    it = recv_cache_.erase(it);
  //  } else {
  //    ++it;
  //  }
  //}
}

}  // namespace jdfl
