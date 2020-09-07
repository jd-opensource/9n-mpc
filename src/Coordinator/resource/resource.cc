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

#include <memory>
#include <string>
#include "resource/resource.h"

namespace resource {

std::shared_ptr<Resource> Resource::instance_;

bool Resource::Init() {
  // db
  hiredis_executor_.reset(new HiRedisExecutor());
  // 1.5s
  struct timeval timeout = { 1, 500000 };
  if (!hiredis_executor_->Init(
    FLAGS_redis_hostname, FLAGS_redis_port, timeout)) {
    LOG(ERROR) << "redis init fail!";
    return false;
  }

  // coordinator
  coordinator_executor_.reset(new CoordinatorExecutor());
  if (!coordinator_executor_->Init()) {
    LOG(ERROR) << "coordinator init fail!";
    return false;
  }


  return true;
}

bool Resource::InitInstance() {
  if (nullptr == instance_.get()) {
    instance_.reset(new Resource());
    return instance_->Init();
  }
  return true;
}

}  // namespace resource
