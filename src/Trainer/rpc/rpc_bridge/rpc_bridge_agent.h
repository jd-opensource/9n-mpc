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

#ifndef TENSORFLOW_CONTRIB_JDFL_RPC_RPC_BRIDGE_RPC_BRIDGE_AGENT_H_
#define TENSORFLOW_CONTRIB_JDFL_RPC_RPC_BRIDGE_RPC_BRIDGE_AGENT_H_

#include <deque>
#include <iostream>
#include <sstream>
#include <string>
#include <unordered_map>

#include "tensorflow/core/distributed_runtime/rpc/grpc_util.h"

#include "tensorflow/contrib/jdfl/rpc/proto/bridge_agent.pb.h"
#include "tensorflow/contrib/jdfl/rpc/rpc_bridge/bridge_interface.h"
#include "tensorflow/contrib/jdfl/rpc/rpc_bridge/fl_utils.h"
#include "tensorflow/contrib/jdfl/rpc/rpc_bridge/rpc_bridge_agent_service.h"

using namespace ::tensorflow;

namespace grpc {
class CompletionQueue;
}

namespace jdfl {

extern const char* const FL_Key_Prefetch;
extern const char* const FL_Key_TrainStart;
// extern const char* const FL_Key_TrainStartCommit;
extern const char* const FL_Key_StepCommit;
extern const char* const FL_Key_DataMessage;
extern const char* const FL_Key_Connect;
extern const char* const FL_Key_Heartbeat;
extern const char* const FL_Key_LoadDatablock;

class RpcBridgeRecvCache;
class RpcBridgeMgr;

BridgeInterface* NewRpcBridgeAgent(SharedGrpcChannelPtr channel,
                                   ::grpc::CompletionQueue* completion_queue,
                                   RpcBridgeRecvCache* service_cache,
                                   RpcBridgeMgr* bridge_mgr);

class RpcBridgeRecvCache {
 public:
  using RecvFinishCB = std::function<void(const Status& status)>;

  bool QueryResult(int64 request_id, const std::string& slot_key,
                   const std::string& content_key, TrainerWorkerMessage* result,
                   int64 timeout_in_us, RecvFinishCB cb);
  bool QueryReadyFile(int64 request_id, const std::string& slot_key,
                      std::string* result, bool* end_of_files,
                      int64 timeout_in_us, RecvFinishCB cb);
  bool WaitPeerReady(int64 request_id, const std::string& slot_key,
                     ConnectRequest* peer, int64 timeout_in_us,
                     RecvFinishCB cb);

  Status OnReceived(const TrainerWorkerMessage* recvdata);
  Status OnReceived(const std::string& fname, bool end_of_files);
  Status OnReceived(const ConnectRequest* peerdata);

  void EraseRequestId(int64 request_id, const std::string& slot_key);
  void CleanEntriesForKey(const std::string& slot_key);

 private:
  enum class State {
    IDLE = 0,
    WAIT = 1,
    ACTIVE = 2,
  };

  struct RecvCacheEntry {
    State state = State::IDLE;
    int64 step_id = -1;
    // Tensor tensor;
    TrainerWorkerMessage* rptr{nullptr};
    TrainerWorkerMessage rdata;
    Status recv_status;

    void FinishRecv(RecvFinishCB& cb) const { cb(recv_status); }
    RecvFinishCB callback_;
  };

  struct TrainFilesCache {
    State state = State::IDLE;
    int64 step_id = -1;
    int64 recv_count = 0;

    RecvFinishCB callback_;

    std::deque<string> q_files;
  };

  struct PeerInfo {
    State state = State::IDLE;

    RecvFinishCB callback_;

    bool peer_connected_{false};
    ConnectRequest rdata;
  };

  mutex mu_;
  std::unordered_map<std::string,
                     std::unordered_map<std::string, RecvCacheEntry>>
      recv_cache_;
  uint32_t logcnt_{0};

  mutex q_mu_;
  TrainFilesCache files_ready_cache_;

  mutex connect_mu_;
  PeerInfo peer_info_;
};
}  // namespace jdfl

#endif  // TENSORFLOW_CONTRIB_JDFL_RPC_RPC_BRIDGE_RPC_BRIDGE_AGENT_H_
