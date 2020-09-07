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

#include <utility>

#include "grpcpp/generic/generic_stub.h"
#include "grpcpp/grpcpp.h"

#include "tensorflow/contrib/jdfl/rpc/rpc_bridge/fl_rpc_state.h"

#include "tensorflow/core/distributed_runtime/rpc/grpc_client_cq_tag.h"
#include "tensorflow/core/distributed_runtime/rpc/grpc_state.h"
#include "tensorflow/core/lib/core/errors.h"
#include "tensorflow/core/lib/core/status.h"
#include "tensorflow/core/lib/core/threadpool.h"
#include "tensorflow/core/lib/strings/str_util.h"
#include "tensorflow/core/platform/logging.h"
#include "tensorflow/core/platform/tracing.h"

#include "tensorflow/contrib/jdfl/rpc/proto/dc_agent.pb.h"
#include "tensorflow/contrib/jdfl/rpc/rpc_bridge/rpc_bridge_mgr.h"
#include "tensorflow/contrib/jdfl/rpc/rpc_bridge/rpc_dc_agent.h"

using namespace ::tensorflow;

namespace jdfl {

const char* RpcDcAgentMethodName(RpcDcAgentMethod id) {
  switch (id) {
    case RpcDcAgentMethod::kFetchDataBlock:
      return "/jdfl.DataBlockQueryService/QueryDataBlock";
  }
  // Shouldn't be reached.
  LOG(ERROR) << "Invalid id: this line shouldn't be reached.";
  return "invalid id";
}

const int kFetchWaitTime = (10 * 1000 * 1000);

class RpcDcAgent : public DcInterface {
 public:
  explicit RpcDcAgent(SharedGrpcChannelPtr channel,
                      ::grpc::CompletionQueue* completion_queue,
                      RpcBridgeMgr* bridge_mgr)
      : channel_(std::move(channel)),
        stub_(channel_),
        cq_(completion_queue),
        bridge_mgr_(bridge_mgr),
        fetch_rpcmethod_(Method(RpcDcAgentMethod::kFetchDataBlock)) {
    polling_thread_ =
        Env::Default()->StartThread(ThreadOptions(), "rpc_dc_agent", [this]() {
          void* tag;
          bool ok;
          LOG(INFO) << "rpc_dc_agent start.";
          while (cq_->Next(&tag, &ok)) {
            if (FlDebugging()) {
              LOG(INFO) << "DcAgent Next ...";
            }
            GrpcClientCQTag* callback_tag = static_cast<GrpcClientCQTag*>(tag);
            callback_tag->OnCompleted(ok);
          }
          LOG(INFO) << "rpc_dc_agent thread exit.";
        });
  }

  ~RpcDcAgent() override {
    if (polling_thread_) {
      delete polling_thread_;
    }
  }

  void FetchDataBlockAsync(const FetchDataBlockRequest* request,
                           FetchDataBlockResponse* response,
                           StatusCallback done) override {
    IssueRequest(request, response, fetch_rpcmethod_, std::move(done));
  }

 private:
  // Utility method for issuing a generic asynchronous request. The
  // given callback, `done`, will be called when the RPC completes.
  template <class Response>
  void IssueRequest(const protobuf::Message* request, Response* response,
                    const ::grpc::string& method, StatusCallback done,
                    CallOptions* call_opts = nullptr, int max_retries = 1) {
    if (FlDebugging()) {
      LOG(INFO) << "IssueRequest: " << method;
    }
    auto tag = new RPCState<Response>(&stub_, cq_, method, *request, response,
                                      std::move(done), call_opts, nullptr, true,
                                      kFetchWaitTime, max_retries);
    if (FlDebugging()) {
      LOG(INFO) << "IssueRequest: " << method << ", tag " << tag;
    }
  }

  // Helper function for initializing the RpcMethod objects below.
  const char* Method(RpcDcAgentMethod id) { return RpcDcAgentMethodName(id); }

  SharedGrpcChannelPtr channel_;
  ::grpc::GenericStub stub_;
  ::grpc::CompletionQueue* cq_;

  RpcBridgeMgr* bridge_mgr_;

  const ::grpc::string fetch_rpcmethod_;

  Thread* polling_thread_{nullptr};

  TF_DISALLOW_COPY_AND_ASSIGN(RpcDcAgent);
};

DcInterface* NewRpcDcAgent(SharedGrpcChannelPtr channel,
                           ::grpc::CompletionQueue* completion_queue,
                           RpcBridgeMgr* bridge_mgr) {
  return new RpcDcAgent(std::move(channel), completion_queue, bridge_mgr);
}

}  // namespace jdfl
