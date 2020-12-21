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

#ifndef TENSORFLOW_CONTRIB_JDFL_RPC_RPC_BRIDGE_RPC_DC_AGENT_H_
#define TENSORFLOW_CONTRIB_JDFL_RPC_RPC_BRIDGE_RPC_DC_AGENT_H_

#include <iostream>
#include <sstream>
#include <string>
#include <unordered_map>

#include "tensorflow/core/distributed_runtime/call_options.h"
#include "tensorflow/core/lib/core/notification.h"
#include "tensorflow/core/lib/core/status.h"
#include "tensorflow/core/lib/core/threadpool.h"
#include "tensorflow/core/platform/mutex.h"
#include "tensorflow/core/platform/types.h"

#include "tensorflow/core/distributed_runtime/rpc/grpc_util.h"

#include "tensorflow/contrib/jdfl/rpc/proto/dc_agent.pb.h"

using namespace ::tensorflow;

namespace grpc {
class CompletionQueue;
}

namespace jdfl {

class RpcBridgeMgr;

typedef std::function<void(const Status&)> StatusCallback;

enum class RpcDcAgentMethod {
  kFetchDataBlock,
};

class DcInterface {
 public:
  virtual void FetchDataBlockAsync(const FetchDataBlockRequest* request,
                                   FetchDataBlockResponse* response,
                                   StatusCallback done) = 0;

  Status FetchDataBlock(const FetchDataBlockRequest* request,
                        FetchDataBlockResponse* response) {
    return CallAndWait(&ME::FetchDataBlockAsync, request, response);
  }

  virtual ~DcInterface() {}

 private:
  typedef DcInterface ME;

  template <typename Method, typename Req, typename Resp>
  Status CallAndWait(Method func, const Req* req, Resp* resp) {
    Status ret;
    Notification n;
    (this->*func)(req, resp, [&ret, &n](const Status& s) {
      ret = s;
      n.Notify();
    });
    n.WaitForNotification();
    return ret;
  }

  template <typename Method, typename Req, typename Resp>
  Status CallAndWaitWithOptions(Method func, const Req* req, Resp* resp) {
    CallOptions call_opts;
    Status ret;
    Notification n;
    (this->*func)(&call_opts, req, resp, [&ret, &n](const Status& s) {
      ret = s;
      n.Notify();
    });
    n.WaitForNotification();
    return ret;
  }
};

DcInterface* NewRpcDcAgent(SharedGrpcChannelPtr channel,
                           ::grpc::CompletionQueue* completion_queue,
                           RpcBridgeMgr* bridge_mgr);
}  // namespace jdfl

#endif  // TENSORFLOW_CONTRIB_JDFL_RPC_RPC_BRIDGE_RPC_DC_AGENT_H_
