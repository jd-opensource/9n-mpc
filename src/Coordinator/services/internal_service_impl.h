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

#ifndef SRC_COORDINATOR_SERVICES_INTERNAL_SERVICE_IMPL_H_
#define SRC_COORDINATOR_SERVICES_INTERNAL_SERVICE_IMPL_H_

#include <grpcpp/grpcpp.h>
#include <gflags/gflags.h>
#include <iostream>
#include <memory>
#include <string>
#include "glog/logging.h"

#include "proto/internal_service.grpc.pb.h"
#include "common/fl_gflags.h"
#include "services/common.h"
#include "resource/resource.h"

namespace jdfl {

class StartApplicationImpl final : public StartApplication::Service {
 public:
  StartApplicationImpl() {}
  ~StartApplicationImpl() {}

  grpc::Status StartApplication(
    grpc::ServerContext* context,
    const ::jdfl::ModelURI* request,
    ::jdfl::Status* response) override;
};

class InternalServiceImpl final : public PairService::Service {
 public:
  InternalServiceImpl() {}
  virtual ~InternalServiceImpl() {}

  grpc::Status RegisterUUID(
    grpc::ServerContext* context,
    const ::jdfl::Request* request,
    ::jdfl::Status* response) override;

  grpc::Status GetPairInfo(
    grpc::ServerContext* context,
    const ::jdfl::Request* request,
    ::jdfl::PairInfoResponse* response) override;

 private:
  bool ReplaceUUIDForNewIp(
    const ::jdfl::Request &request, ::jdfl::AppInfo *app_info);
};

}  // namespace jdfl

#endif  // SRC_COORDINATOR_SERVICES_INTERNAL_SERVICE_IMPL_H_
