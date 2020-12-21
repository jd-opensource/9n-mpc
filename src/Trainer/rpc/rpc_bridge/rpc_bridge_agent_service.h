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

#ifndef TENSORFLOW_CONTRIB_JDFL_RPC_RPC_BRIDGE_RPC_BRIDGE_AGENT_SERVICE_H_
#define TENSORFLOW_CONTRIB_JDFL_RPC_RPC_BRIDGE_RPC_BRIDGE_AGENT_SERVICE_H_

#include <memory>
#include <unordered_map>
#include <string>

#include "grpcpp/impl/codegen/async_stream.h"
#include "grpcpp/impl/codegen/async_unary_call.h"
#include "grpcpp/impl/codegen/proto_utils.h"
#include "grpcpp/impl/codegen/rpc_method.h"
#include "grpcpp/impl/codegen/service_type.h"
#include "grpcpp/impl/codegen/status.h"
#include "grpcpp/impl/codegen/stub_options.h"
#include "grpcpp/impl/codegen/sync_stream.h"
#include "grpcpp/server_builder.h"
#include "grpcpp/support/byte_buffer.h"

#include "tensorflow/core/distributed_runtime/rpc/async_service_interface.h"

#include "tensorflow/contrib/jdfl/rpc/proto/bridge_agent.grpc.pb.h"
#include "tensorflow/contrib/jdfl/rpc/proto/bridge_agent.pb.h"
#include "tensorflow/contrib/jdfl/rpc/rpc_bridge/rpc_bridge_agent.h"

namespace jdfl {

class RpcBridgeRecvCache;
class RpcBridgeMgr;

enum class RpcBridgeAgentMethod {
  kTransmit,
  kStreamTransmit,
  kLoadDataBlock,
  kConnect,
  kHeartbeat,
};

static const int kRpcNumAgentMethods =
    static_cast<int>(RpcBridgeAgentMethod::kHeartbeat) + 1;

const char* RpcAgentMethodName(RpcBridgeAgentMethod id);

enum class BridgeMethodIndex {
  kRpcPrefetch = 0,
  kRpcTrainStart = 1,
  kRpcStepCommit = 2,
  kRpcDataMessage = 3,
  kRpcLoadDataBlock = 4,
  kRpcConnect = 5,
  kRpcHeartbeat = 6,
};

std::string RpcAgentMethodRecvKey(BridgeMethodIndex id);

std::unique_ptr<AsyncServiceInterface> NewRpcBridgeAgentService(
    ::grpc::ServerBuilder* builder, RpcBridgeRecvCache* service_cache,
    RpcBridgeMgr* bridge_mgr);

}  // namespace jdfl

#endif  // TENSORFLOW_CONTRIB_JDFL_RPC_RPC_BRIDGE_RPC_BRIDGE_AGENT_SERVICE_H_
