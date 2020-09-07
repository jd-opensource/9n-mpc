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

#ifndef SRC_COORDINATOR_SERVICES_COMMON_H_
#define SRC_COORDINATOR_SERVICES_COMMON_H_

#include <grpcpp/grpcpp.h>
#include <gflags/gflags.h>
#include <iostream>
#include <memory>
#include <string>
#include "glog/logging.h"

#include "proto/internal_service.grpc.pb.h"
#include "proto/external_service.grpc.pb.h"
#include "common/fl_gflags.h"
#include "resource/resource.h"

namespace jdfl {

bool GetJsonConfFromRedis(const std::string& model_uri,
                          const std::string& version,
                          ::jdfl::AppInfo *app_info);
bool RegisterCoodinator();

bool StartK8S(const std::string& app_id);
bool StartK8SFromAppInfo(const ::jdfl::AppInfo &app_info);
bool StopK8S(const std::string& app_id);
int CheckK8S(const std::string& app_id);
bool CheckAppInfo(const AppInfo& app_info);
bool SetAppInfoToRedis(const ::jdfl::AppInfo& app_info);
bool DeleteAppInfoInRedis(const std::string &app_id);
bool GetAppInfoFromRedis(const std::string& app_id,
                         ::jdfl::AppInfo* app_info);
int32_t CheckRegisterNum(const std::string& app_id, ::jdfl::AppInfo* app_info);
}  // namespace jdfl

#endif  // SRC_COORDINATOR_SERVICES_COMMON_H_
