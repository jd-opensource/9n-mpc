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

#ifndef SRC_COORDINATOR_SERVICES_EXTERNAL_SERVICE_IMPL_H_
#define SRC_COORDINATOR_SERVICES_EXTERNAL_SERVICE_IMPL_H_

#include <grpcpp/grpcpp.h>
#include <gflags/gflags.h>
#include <iostream>
#include <memory>
#include <string>
#include "glog/logging.h"

#include "proto/external_service.grpc.pb.h"
#include "proto/internal_service.grpc.pb.h"
#include "common/fl_gflags.h"
#include "services/common.h"
#include "resource/resource.h"

namespace fl {

class SchedulerServiceImpl final :
    public ::fedlearner::common::Scheduler::Service {
 public:
  grpc::Status SubmitTrain(
    grpc::ServerContext* context,
    const ::fedlearner::common::TrainRequest* request,
    ::fedlearner::common::Status* response) override;
 private:
  bool CheckModelInfo(const ::fedlearner::common::TrainRequest &request,
                      const ::jdfl::AppInfo &app_info);
};

struct TaskInfo {
  ::fedlearner::common::AppSynRequest request;
  ::jdfl::AppInfo app_info;
};

class StateSynServiceImpl final :
    public ::fedlearner::common::StateSynService::Service {
 public:
  grpc::Status Syn(
    grpc::ServerContext* context,
    const ::fedlearner::common::AppSynRequest* request,
    ::fedlearner::common::Status* response) override;
 private:
  static void WaitForServiceRegistered(TaskInfo *task);
};

}  // namespace fl

#endif  // SRC_COORDINATOR_SERVICES_EXTERNAL_SERVICE_IMPL_H_
