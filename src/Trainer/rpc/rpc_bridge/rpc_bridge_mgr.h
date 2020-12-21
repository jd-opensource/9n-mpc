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

#ifndef TENSORFLOW_CONTRIB_JDFL_RPC_RPC_BRIDGE_RPC_BRIDGE_MGR_H_
#define TENSORFLOW_CONTRIB_JDFL_RPC_RPC_BRIDGE_RPC_BRIDGE_MGR_H_

#include <memory>
#include <vector>
#include <string>

#include "grpcpp/grpcpp.h"
#include "grpcpp/security/credentials.h"
#include "grpcpp/server_builder.h"

#include "tensorflow/core/distributed_runtime/rpc/async_service_interface.h"
#include "tensorflow/core/distributed_runtime/rpc/grpc_channel.h"
#include "tensorflow/core/platform/env.h"
#include "tensorflow/core/platform/mutex.h"

#include "tensorflow/contrib/jdfl/rpc/rpc_bridge/fl_rpc_state.h"
#include "tensorflow/contrib/jdfl/rpc/rpc_bridge/fl_utils.h"
#include "tensorflow/contrib/jdfl/rpc/rpc_bridge/rpc_bridge_agent.h"
#include "tensorflow/contrib/jdfl/rpc/rpc_bridge/rpc_bridge_agent_service.h"
#include "tensorflow/contrib/jdfl/rpc/rpc_bridge/rpc_dc_agent.h"

using namespace ::tensorflow;

namespace jdfl {

static constexpr const char* const RoleDef_Leader = "leader";
static constexpr const char* const RoleDef_Follower = "follower";

// kind of service method ( Unary RPCs or Bidirectional streaming RPCs)
enum class KindOfServiceType {
  kUnary = 0,
  kBidiStreaming = 1,
};

struct Params {
  std::string appli_id;
  uint32_t rank_id;
  std::string role_def;
  KindOfServiceType service_type;
  std::vector<CallMeta> ctx_meta_conf;
};

class RunStepStats {
 public:
  RunStepStats()
      : iter_id(0), seq_num(0), next_seq_num(1), next_recv_seq_num(0) {}

  int64_t NextIterId() {
    mutex_lock l(mu_);
    iter_id++;
    return iter_id;
  }

  int64_t CurrentIterId() {
    mutex_lock l(mu_);
    return iter_id;
  }

  int64_t SetIterId(int64_t sync_id) {
    mutex_lock l(mu_);
    iter_id = sync_id;
    return iter_id;
  }

  int64_t NextSeqNum() {
    mutex_lock l(mu_);
    return next_seq_num;
  }

  int64_t CurrentSeqNum() {
    mutex_lock l(mu_);
    return seq_num;
  }

  int64_t CommitToNextSeqNum() {
    mutex_lock l(mu_);
    seq_num = next_seq_num;
    next_seq_num++;
    return seq_num;
  }

  int64_t CurrentRecvSeqNum() {
    mutex_lock l(mu_);
    return next_recv_seq_num;
  }

  int64_t NextRecvSeqNum() {
    mutex_lock l(mu_);
    return ++next_recv_seq_num;
  }

 private:
  mutex mu_;
  int64_t iter_id;
  int64_t seq_num; /* sequence number */
  int64_t next_seq_num;
  int64_t next_recv_seq_num;
};

class RpcBridgeMgr {
 public:
  enum class BrState { NEW, STARTED, STOPPED };

  static RpcBridgeMgr* Singleton() {
    static RpcBridgeMgr* instance = new RpcBridgeMgr;
    return instance;
  }

  virtual ~RpcBridgeMgr() { Stop();}

  Status Stop();

  Status InitServer(const std::string& server_def, const Params& params);
  Status InitRpcChannel(const std::string& channel_type,
                        const std::string& target_addr);

  BridgeInterface* bridge_impl() const { return bridge_impl_; }
  DcInterface* dc_impl() const { return dc_impl_; }

  std::string AppID() const { return appli_id_; }
  uint32_t RankID() const { return rank_id_; }
  std::string RoleDef() const { return role_def_; }
  KindOfServiceType ServiceType() const { return service_type_; }
  const std::vector<CallMeta>& ContexMeta() const { return ctx_meta_; }
  std::string Identifier() const { return identifier_; }

  RunStepStats* StepStats() { return step_stats_.get(); }

  BrState State() const { return state_; }
  BrState StateMove(BrState to_state) {
    mutex_lock l(mu_);
    BrState old = state_;
    state_ = to_state;
    return old;
  }

 private:
  RpcBridgeMgr() {}

  Env* env_;

  std::string bound_ip_;
  int bound_port_ = 0;

  std::string appli_id_;
  uint32_t rank_id_;
  std::string role_def_;
  KindOfServiceType service_type_;
  std::vector<CallMeta> ctx_meta_;
  std::string identifier_;

  mutex mu_;

  BrState state_ = BrState::NEW;

  bool service_initialized_ = false;
  bool train_channel_initialized_ = false;
  bool data_channel_initialized_ = false;

  ::grpc::ServerBuilder builder;
  ::grpc::CompletionQueue bridge_cq_;
  ::grpc::CompletionQueue dc_cq_;

  BridgeInterface* bridge_impl_ = nullptr;
  DcInterface* dc_impl_ = nullptr;
  std::unique_ptr<AsyncServiceInterface> bridge_service_;
  std::unique_ptr<::grpc::Server> server_;

  RpcBridgeRecvCache service_cache_;

  std::unique_ptr<RunStepStats> step_stats_;
};
}  // namespace jdfl

#endif  // TENSORFLOW_CONTRIB_JDFL_RPC_RPC_BRIDGE_RPC_BRIDGE_MGR_H_
