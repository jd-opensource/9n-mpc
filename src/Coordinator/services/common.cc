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

#include <stdlib.h>
#include <map>
#include <string>
#include <vector>
#include "services/common.h"
#include "common/util.h"
#include "common/fl_gflags.h"
#include "google/protobuf/util/json_util.h"

namespace jdfl {

#define CHECK_APPINFO_STRITEM(item) \
  do { \
    if (app_info.conf_info().item().empty()) { \
      LOG(ERROR) << "#item not exist in AppInfo."; \
      return false; \
    } \
  } while (0)
#define CHECK_APPINFO_INTITEM(item) \
  do { \
    if (0 == app_info.conf_info().item()) { \
      LOG(ERROR) << "#item not exist in AppInfo."; \
      return false; \
    } \
  } while (0)

bool CheckAppInfo(const AppInfo& app_info) {
  CHECK_APPINFO_STRITEM(model_uri);
  CHECK_APPINFO_STRITEM(data_source_name);
  // CHECK_APPINFO_INTITEM(worker_num);
  // CHECK_APPINFO_INTITEM(datacentor_num);
  CHECK_APPINFO_STRITEM(train_data_start);
  CHECK_APPINFO_STRITEM(train_data_end);
  CHECK_APPINFO_INTITEM(data_num_epoch);
  return true;
}

bool CheckK8SParaInfo(const AppInfo& app_info) {
  CHECK_APPINFO_INTITEM(worker_num);
  CHECK_APPINFO_STRITEM(train_data_start);
  CHECK_APPINFO_STRITEM(train_data_end);
  CHECK_APPINFO_STRITEM(data_source_name);
  CHECK_APPINFO_INTITEM(data_num_epoch);
  return true;
}

int32_t CheckRegisterNum(const std::string& app_id, ::jdfl::AppInfo* app_info) {
  assert(app_info);
  if (!GetAppInfoFromRedis(app_id, app_info)) {
    LOG(ERROR) << "thread, get redis fail, app_id is : " << app_id;
    return -2;
  }
  // check if registered all the ip
  const ::jdfl::ConfInfo& conf_info = app_info->conf_info();
  auto local_status = conf_info.local_status();
  auto remote_status = conf_info.remote_status();
  if (local_status == ::jdfl::SHUTDOWN || local_status == ::jdfl::FINISH ||
      remote_status == ::jdfl::SHUTDOWN || remote_status == ::jdfl::FINISH) {
    LOG(ERROR) << "finish or shutdown. "
               << "local_status: " << local_status
               << " remote_status:" << remote_status;
    return -2;
  }
  int total_num = 0;
  if (0 != conf_info.worker_num()) {
    total_num = conf_info.worker_num();
  } else {
    total_num = conf_info.datacentor_num();
  }
  if (total_num != app_info->pair_infos_size()) {
    LOG(ERROR) << "Registration completed num: " << app_info->pair_infos_size()
               << " total num: " << total_num << " app_id: " << app_id;
    return -1;
  }
  LOG(INFO) << "Registration completed. app_id: "
            << app_id << " total_num: " << total_num;
  return total_num;
}

bool SetAppInfoToRedis(const ::jdfl::AppInfo& app_info) {
  // set AppInfo to redis.(e.g., role, app_id ...)
  const std::string &app_id = app_info.conf_info().app_id();
  std::string str_app_info;
  if (!common::MessageToJsonString(app_info, &str_app_info)) {
    LOG(ERROR) << "fail to serialize app_info to string. app id : " << app_id;
    return false;
  }
  auto redis_ptr = resource::Resource::Instance()->redis_resource();
  if (!redis_ptr->Set(app_info.conf_info().app_id(), str_app_info)) {
    LOG(ERROR) << "fail to set AppInfo to redis. app_id : " << app_id;
    return false;
  }
  return true;
}

bool GetAppInfoFromRedis(const std::string &app_id,
                         ::jdfl::AppInfo *app_info) {
  assert(app_info);
  app_info->Clear();
  std::string value;
  auto redis_ptr = resource::Resource::Instance()->redis_resource();
  if (!redis_ptr->Get(app_id, &value)) {
    LOG(ERROR) << "get app fail! app_id: " << app_id;
    return false;
  }
  if (!common::JsonStringToMessage(value, app_info)) {
    LOG(ERROR) << "parse from string fail! app_id : " << app_id;
    return false;
  }
  return true;
}

bool RegisterCoodinator() {
  static const std::string scheduler_service("Scheduler");
  static const std::string app_service("StateSynService");
  auto redis_ptr = resource::Resource::Instance()->redis_resource();
  std::vector<std::string> keys = {scheduler_service,
                                   app_service};
  std::vector<std::string> values(2, FLAGS_coordinator_domain);
  if (!redis_ptr->Mset(keys, values)) {
    return false;
  }
  return true;
}

bool StartK8S(const std::string& app_id) {
  std::string str_app_info;
  auto redis_ptr = resource::Resource::Instance()->redis_resource();
  if (!redis_ptr->Get(app_id, &str_app_info)) {
    LOG(ERROR) << "fail to set AppInfo to redis.";
    return false;
  }
  ::jdfl::AppInfo app_info;
  if (!common::JsonStringToMessage(str_app_info, &app_info)) {
    LOG(ERROR) << "fail to parse str_app_info. app_id: " << app_id;
    return false;
  }
  return StartK8SFromAppInfo(app_info);
}

std::string GetTaskId(const std::string &str) {
  size_t h = std::hash<std::string>()(str);
  std::string re = std::to_string(h);
  int i = 31;
  while (i > 0) {
    i--;
    if (h > 0) {
      h = h / 10;
      continue;
    }
    re.append("0");
  }
  return re;
}

bool StartK8SFromAppInfo(const ::jdfl::AppInfo &app_info) {
  if (!CheckK8SParaInfo(app_info)) {
    LOG(ERROR) << "Check k8s para info fail!";
    return false;
  }
  const ::jdfl::ConfInfo& conf_info = app_info.conf_info();
  const std::string &app_id = conf_info.app_id();
  std::string task_id = GetTaskId(app_id);
  if (conf_info.role() == ::jdfl::ConfInfo::FOLLOWER) {
    task_id.append("0");
  } else {
    task_id.append("1");
  }
  std::ostringstream os;
  os << "python ../ResourceManager/create_job.py " << conf_info.role() << " "
     << task_id << " " << app_id << " " << conf_info.worker_num()
     << " " << conf_info.train_data_start() << " "
     << conf_info.train_data_end() << " " << conf_info.data_source_name()
     << " " << conf_info.data_num_epoch();
  LOG(INFO) << "command: " << os.str();
  LOG(INFO) << "start to exec k8s";
  int status = system(os.str().c_str());
  if (0 != status) {
    LOG(ERROR) << "start k8s status: " << status;
    return false;
  }
  return true;
}

int CheckK8S(const std::string& app_id) {
  std::string task_id = GetTaskId(app_id);
  ::jdfl::AppInfo app_info;
  GetAppInfoFromRedis(app_id, &app_info);
  if (app_info.conf_info().role() == ::jdfl::ConfInfo::FOLLOWER) {
    task_id.append("0");
  } else {
    task_id.append("1");
  }
  LOG(INFO) << "CheckK8S() app_id: " << app_id << " task_id: " << task_id;
  std::ostringstream os;
  os << "python ../ResourceManager/check_job.py "
     << app_info.conf_info().role() << " " << task_id;
  int status = system(os.str().c_str());
  if (WIFEXITED(status)) {
    return WEXITSTATUS(status);
  }
  return -1;
}

bool StopK8S(const std::string& app_id) {
  std::string task_id = GetTaskId(app_id);

  ::jdfl::AppInfo app_info;
  GetAppInfoFromRedis(app_id, &app_info);
  if (app_info.conf_info().role() == ::jdfl::ConfInfo::FOLLOWER) {
    task_id.append("0");
  } else {
    task_id.append("1");
  }

  std::ostringstream os;
  os << "python ../ResourceManager/delete_job.py "
     << app_info.conf_info().role() << " " << task_id;
  LOG(INFO) << "command: " << os.str();
  int res = system(os.str().c_str());
  if (0 != res) {
    LOG(ERROR) << "k8s res: " << res;
    return false;
  }
  return true;
}

bool DeleteAppInfoInRedis(const std::string &app_id) {
  ::jdfl::AppInfo app_info;
  if (!GetAppInfoFromRedis(app_id, &app_info)) {
    return false;
  }

  auto redis_ptr = resource::Resource::Instance()->redis_resource();
  // del app info
  redis_ptr->Del(app_id);
  // del uuid
  std::vector<std::string> keys;
  keys.reserve(20);
  for (const auto &pair_info : app_info.pair_infos()) {
    for (const auto &service : pair_info.service_pair()) {
      keys.push_back(service.local_uuid());
    }
  }
  redis_ptr->Del(keys);
  return true;
}

bool GetJsonConfFromRedis(const std::string& model_uri,
                          const std::string& version,
                          ::jdfl::AppInfo *app_info) {
  assert(app_info);
  app_info->Clear();
  auto redis_ptr = resource::Resource::Instance()->redis_resource();
  std::string value;
  std::string key = model_uri + "-" + version;
  if (!redis_ptr->Get(key, &value)) {
    LOG(ERROR) << "get app fail! key: " << key;
    return false;
  }
  LOG(INFO) << "conf json string: " << value;
  if (!common::JsonStringToMessage(value, app_info)) {
    LOG(ERROR) << "parse from string fail! key : " << key;
    return false;
  }
  if (0 != model_uri.compare(app_info->conf_info().model_uri())) {
    LOG(ERROR) << "model_uri mismatch between json file and param."
               << "model_uri: " << model_uri;
    return false;
  }
  return true;
}

}  // namespace jdfl
