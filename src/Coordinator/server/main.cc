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

#include <grpcpp/grpcpp.h>
#include <gflags/gflags.h>
#include <iostream>
#include <memory>
#include <thread>
#include <string>

#include "glog/logging.h"
#include "proto/internal_service.grpc.pb.h"
#include "proto/external_service.grpc.pb.h"
#include "common/fl_gflags.h"
#include "services/check_app_status.h"
#include "services/internal_service_impl.h"
#include "services/external_service_impl.h"
#include "resource/resource.h"

int main(int argc, char* argv[]) {
  // Parse gflags. We recommend you to use gflags as well.
  google::ParseCommandLineFlags(&argc, &argv, true);
  const char * server_name = argv[0];
  google::InitGoogleLogging(server_name);

  // resource
  if (!resource::Resource::InitInstance()) {
    LOG(ERROR) << "Init resource fail!";
    return -1;
  }

  // register coordinator
  if (!jdfl::RegisterCoodinator()) {
    LOG(ERROR) << "register coordinator fail!";
    return -1;
  }

  jdfl::CheckAppStatus::InitInstance();
  std::thread th(jdfl::RunCheckAppStatus);
  th.detach();

  // server
  grpc::ServerBuilder builder;
  ::jdfl::InternalServiceImpl internal_service;
  ::jdfl::StartApplicationImpl start_application_service;
  ::fl::SchedulerServiceImpl scheduler_service;
  ::fl::StateSynServiceImpl state_syn_service;
  builder.AddListeningPort("0.0.0.0:" + std::to_string(FLAGS_port),
                           grpc::InsecureServerCredentials());
  builder.RegisterService(&internal_service);
  builder.RegisterService(&start_application_service);
  builder.RegisterService(&scheduler_service);
  builder.RegisterService(&state_syn_service);
  std::unique_ptr<grpc::Server> server(builder.BuildAndStart());
  server->Wait();
  return 0;
}
