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

#ifndef SRC_COORDINATOR_SERVICES_CHECK_APP_STATUS_H_
#define SRC_COORDINATOR_SERVICES_CHECK_APP_STATUS_H_

#include <set>
#include <vector>
#include <string>
#include <memory>
#include "common/util.h"

namespace jdfl {

class CheckAppStatus {
 public:
  static CheckAppStatus* Instance() {
    return instance_.get();
  }

  static void InitInstance() {
    if (nullptr == instance_.get())
      instance_.reset(new CheckAppStatus());
  }

  void DoCheckAppStatus();
  void AddAppId(const std::string& app_id);
  void DeleteAppId(std::vector<std::string> app_id_arr);

 private:
  CheckAppStatus() {
    app_id_set_.clear();
    pthread_rwlock_init(&rw_lock_, NULL);
  }
  static std::shared_ptr<CheckAppStatus> instance_;
  pthread_rwlock_t rw_lock_;
  std::set<std::string> app_id_set_;
};

void RunCheckAppStatus();

}  // namespace jdfl

#endif  // SRC_COORDINATOR_SERVICES_CHECK_APP_STATUS_H_
