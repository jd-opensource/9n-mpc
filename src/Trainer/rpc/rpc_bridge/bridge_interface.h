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

#ifndef TENSORFLOW_CONTRIB_JDFL_RPC_RPC_BRIDGE_BRIDGE_INTERFACE_H_
#define TENSORFLOW_CONTRIB_JDFL_RPC_RPC_BRIDGE_BRIDGE_INTERFACE_H_

#include <string>
#include <functional>
#include <unordered_map>

#include "tensorflow/core/distributed_runtime/call_options.h"
#include "tensorflow/core/lib/core/notification.h"
#include "tensorflow/core/lib/core/status.h"
#include "tensorflow/core/lib/core/threadpool.h"
#include "tensorflow/core/platform/mutex.h"
#include "tensorflow/core/platform/types.h"

#include "tensorflow/contrib/jdfl/rpc/proto/bridge_agent.pb.h"

using namespace ::tensorflow;

namespace jdfl {

typedef std::function<void(const Status&)> StatusCallback;

class BridgeInterface {
 public:
  virtual void RequestWriteLock() {}

  virtual void RequestWriteCompletedUnlock() {}

  virtual void RequestTrainStartAsync(const TrainerWorkerMessage* request,
                                      TrainerWorkerResponse* response,
                                      StatusCallback done) = 0;

  virtual void RequestTrainCommitAsync(const TrainerWorkerMessage* request,
                                       TrainerWorkerResponse* response,
                                       StatusCallback done) = 0;

  virtual void RequestDateTransmitAsync(const TrainerWorkerMessage* request,
                                        TrainerWorkerResponse* response,
                                        StatusCallback done) = 0;

  virtual void RequestGradBpAsync(const TrainerWorkerMessage* request,
                                  TrainerWorkerResponse* response,
                                  StatusCallback done) = 0;

  virtual void HeartbeatChkAsync(const HeartbeatRequest* request,
                                 HeartbeatResponse* response,
                                 StatusCallback done) = 0;

  virtual void RequestConnectAsync(const ConnectRequest* request,
                                   ConnectResponse* response,
                                   StatusCallback done) = 0;

  virtual void RequestLoadDataBlockAsync(const LoadDataBlockRequest* request,
                                         ResultStatus* response,
                                         StatusCallback done) = 0;

  virtual Status QueryResult(int64 request_id, const std::string& slot_key,
                             const std::string& content_key,
                             TrainerWorkerMessage* result) = 0;

  virtual Status WaitPeerReady(int64 request_id, const std::string& slot_key,
                               ConnectRequest* peer) = 0;

  virtual Status QueryReadyFile(int64 request_id, const std::string& slot_key,
                                std::string* result, bool* end_of_files) = 0;

  Status HeartbeatChk() {
    Status ret;
    Notification n;
    HeartbeatRequest request;
    HeartbeatResponse response;
    HeartbeatChkAsync(&request, &response, [&ret, &n](const Status& s) {
      ret = s;
      n.Notify();
    });
    n.WaitForNotification();
    return ret;
  }

  Status RequestHeartbeat(const HeartbeatRequest* request,
                          HeartbeatResponse* response) {
    return CallAndWait(&ME::HeartbeatChkAsync, request, response);
  }

  Status RequestConnect(const ConnectRequest* request,
                        ConnectResponse* response) {
    return CallAndWait(&ME::RequestConnectAsync, request, response);
  }

  Status RequestTrainStart(const TrainerWorkerMessage* request,
                           TrainerWorkerResponse* response) {
    return CallAndWait(&ME::RequestTrainStartAsync, request, response);
  }

  Status RequestTrainCommit(const TrainerWorkerMessage* request,
                            TrainerWorkerResponse* response) {
    return CallAndWait(&ME::RequestTrainCommitAsync, request, response);
  }

  Status RequestDataTransmit(const TrainerWorkerMessage* request,
                             TrainerWorkerResponse* response) {
    return CallAndWait(&ME::RequestDateTransmitAsync, request, response);
  }

  Status RequestGradBp(const TrainerWorkerMessage* request,
                       TrainerWorkerResponse* response) {
    return CallAndWait(&ME::RequestGradBpAsync, request, response);
  }

  Status RequestLoadDataBlock(const LoadDataBlockRequest* request,
                              ResultStatus* response) {
    return CallAndWait(&ME::RequestLoadDataBlockAsync, request, response);
  }

  virtual ~BridgeInterface() {}

 private:
  typedef BridgeInterface ME;

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

}  // namespace jdfl

#endif  // TENSORFLOW_CONTRIB_JDFL_RPC_RPC_BRIDGE_BRIDGE_INTERFACE_H_
