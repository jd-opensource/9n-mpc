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

#include <chrono>
#include <cstring>
#include <limits>
#include <memory>
#include <vector>

#include "grpc/support/alloc.h"
#include "grpcpp/grpcpp.h"
#include "grpcpp/security/credentials.h"
#include "grpcpp/server_builder.h"

#include "tensorflow/core/distributed_runtime/rpc/async_service_interface.h"
#include "tensorflow/core/distributed_runtime/rpc/grpc_channel.h"

#include "tensorflow/core/lib/strings/strcat.h"
#include "tensorflow/core/platform/env.h"
#include "tensorflow/core/platform/mem.h"

#include "tensorflow/contrib/jdfl/rpc/rpc_bridge/rpc_bridge_agent_service.h"
#include "tensorflow/contrib/jdfl/rpc/rpc_bridge/rpc_bridge_mgr.h"

using namespace ::tensorflow;

namespace jdfl {

typedef std::shared_ptr<::grpc::Channel> ChannelPtr;

ChannelPtr CreateChannelForTarget(const string& target) {
  ::grpc::ChannelArguments args;
  args.SetInt(GRPC_ARG_MAX_MESSAGE_LENGTH, std::numeric_limits<int32>::max());

  // Set a standard backoff timeout of 1s instead of the
  // (sometimes default) 20s.
  args.SetInt("grpc.testing.fixed_reconnect_backoff_ms", 3000);
  return ::grpc::CreateCustomChannel(
      target, ::grpc::InsecureChannelCredentials(), args);
}

Status RpcBridgeMgr::InitServer(const std::string& server_def,
                                const Params& params) {
  mutex_lock l(mu_);
  if (service_initialized_) {
    LOG(INFO) << "gRPC server already initialized. target(" << server_def
              << ")";
    return Status::OK();
  }

  appli_id_ = params.appli_id;
  rank_id_ = params.rank_id;
  role_def_ = params.role_def;
  service_type_ = params.service_type;
  ctx_meta_ = params.ctx_meta_conf;
  uint64_t tp = std::chrono::duration_cast<std::chrono::milliseconds>(
                    std::chrono::steady_clock::now().time_since_epoch())
                    .count();
  identifier_ = std::to_string(tp);

  step_stats_.reset(new RunStepStats());

  LOG(INFO) << "Create gRPC server: " << server_def;
  ::grpc::ServerBuilder builder;
  builder.AddListeningPort(server_def, grpc::InsecureServerCredentials());

  bridge_service_ = NewRpcBridgeAgentService(&builder, &service_cache_, this);
  server_ = builder.BuildAndStart();
  if (!server_) {
    LOG(ERROR) << "Could not start gRPC server: " << server_def;
    return errors::Unknown("Could not start gRPC server:", server_def);
  }
  LOG(INFO) << "Start HandleRPCsLoop thread: " << server_def;
  bridge_service_->HandleRPCsLoop();

  service_initialized_ = true;
  return Status::OK();
}

Status RpcBridgeMgr::InitRpcChannel(const std::string& channel_type,
                                    const std::string& target_addr) {
  mutex_lock l(mu_);

  if (channel_type == "TRAIN") {
    if (train_channel_initialized_) {
      LOG(INFO) << "TRAIN Channel already initialized. target(" << target_addr
                << ")";
      return Status::OK();
    }

    LOG(INFO) << "Create Channel (target: " << target_addr << ")";
    auto channel = CreateChannelForTarget(target_addr);
    if (!channel) {
      LOG(ERROR) << "Could not create gRPC Channel: " << target_addr;
      return errors::Unknown("Could not create gRPC Channel");
    }
    bridge_impl_ =
        NewRpcBridgeAgent(channel, &bridge_cq_, &service_cache_, this);
    LOG(INFO) << "Create Channel done. (" << target_addr << ")";

    train_channel_initialized_ = true;
    return Status::OK();
  } else if (channel_type == "DATA") {
    if (data_channel_initialized_) {
      LOG(INFO) << "DATA Channel already initialized. target(" << target_addr
                << ")";
      return Status::OK();
    }
    //  init datacenter channel
    LOG(INFO) << "Create DC Channel (target: " << target_addr << ")";
    auto dc_channel = CreateChannelForTarget(target_addr);
    if (!dc_channel) {
      LOG(ERROR) << "Could not create DC gRPC Channel: " << target_addr;
      return errors::Unknown("Could not create DC gRPC Channel");
    }
    dc_impl_ = NewRpcDcAgent(dc_channel, &dc_cq_, this);
    LOG(INFO) << "Create DC Channel done. (" << target_addr << ")";

    data_channel_initialized_ = true;
    return Status::OK();
  } else {
    LOG(ERROR) << "Channel type invalid: " << channel_type;
    return errors::Unknown("Channel type invalid: ", channel_type);
  }
}

Status RpcBridgeMgr::Stop() {
  mutex_lock l(mu_);
  LOG(INFO) << "RpcBridge Server request stopped.";

  if (server_) {
    server_->Shutdown();
  }
  bridge_cq_.Shutdown();
  dc_cq_.Shutdown();
  if (bridge_service_) {
    bridge_service_->Shutdown();
  }
  if (bridge_impl_) {
    delete bridge_impl_;
    bridge_impl_ = nullptr;
  }
  if (dc_impl_) {
    delete dc_impl_;
    dc_impl_ = nullptr;
  }

  return Status::OK();
}

}  // namespace jdfl
