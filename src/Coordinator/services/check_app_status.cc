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

#include <thread>
#include "common/util.h"
#include "services/invoke_module.h"
#include "services/check_app_status.h"

namespace jdfl {

std::shared_ptr<CheckAppStatus> CheckAppStatus::instance_;

void CheckAppStatus::DoCheckAppStatus() {
  ::jdfl::AppInfo app_info;
  std::vector<std::string> app_id_arr;
  do {
    app_id_arr.clear();
    std::this_thread::sleep_for(std::chrono::milliseconds(60 * 1000));
    do {
      common::ReadLockGuard r_lock(&rw_lock_);
      for (auto it = app_id_set_.begin(); it != app_id_set_.end(); ++it) {
        ::fedlearner::common::Status reply;
        grpc::ClientContext context;
        ::fedlearner::common::AppSynRequest request;

        const std::string& app_id = *it;
        app_info.Clear();
        if (!GetAppInfoFromRedis(app_id, &app_info)) {
          LOG(ERROR) << "get redis fail, app_id is : " + app_id;
          continue;
        }
        LOG(INFO) << "CheckK8S(app_id) app_id: " << app_id;
        int res = ::jdfl::CheckK8S(app_id);
        LOG(INFO) << "res: " << res;
        if (0 == res) {
          LOG(INFO) << "runing.";
          continue;
        } else if (1 == res) {
          app_id_arr.push_back(app_id);
          LOG(INFO) << "status check: app_id: " << app_id << " finish.";
          app_info.mutable_conf_info()->set_local_status(::jdfl::FINISH);
          request.set_ctrl_flag(::fedlearner::common::FINISH);
        } else if (2 == res) {
          app_id_arr.push_back(app_id);
          LOG(ERROR) << "status check: app_id: " << app_id << " fail.";
          app_info.mutable_conf_info()->set_local_status(::jdfl::SHUTDOWN);
          request.set_ctrl_flag(::fedlearner::common::SHUTDOWN);
        } else {
          app_id_arr.push_back(app_id);
          LOG(ERROR) << "status check: app_id: " << app_id << " not exist.";
          app_info.mutable_conf_info()->set_local_status(::jdfl::SHUTDOWN);
          request.set_ctrl_flag(::fedlearner::common::SHUTDOWN);
        }
        SetAppInfoToRedis(app_info);

        request.set_app_id(app_id);
        jdfl::InvokeModule invoke_module(app_info);
        grpc::Status status = invoke_module.InvokeSyn(
            &context, request, &reply);
        if (!status.ok()) {
          LOG(ERROR) << "fail to send app status. app_id: "
                     << app_id << ", status: "
                     << status.error_message();
          return;
        }
        if (0 != reply.status()) {
          LOG(ERROR) << "fail to send local uuid to remote."
                     << reply.err_msg();
        }
      }
    } while (false);
    DeleteAppId(app_id_arr);
  } while (true);
}

void CheckAppStatus::DeleteAppId(std::vector<std::string> app_id_arr) {
  common::WriteLockGuard w_lock(&rw_lock_);
  for (uint32_t i = 0; i < app_id_arr.size(); ++i) {
    std::set<std::string>::iterator it = app_id_set_.find(app_id_arr[i]);
    if (it != app_id_set_.end()) {
      app_id_set_.erase(it);
    }
  }
}

void CheckAppStatus::AddAppId(const std::string& app_id) {
  common::WriteLockGuard w_lock(&rw_lock_);
  app_id_set_.insert(app_id);
}

void RunCheckAppStatus() {
  CheckAppStatus::Instance()->DoCheckAppStatus();
}

}  // namespace jdfl
