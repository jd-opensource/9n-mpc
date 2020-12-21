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
#include "resource/coordinator_executor.h"

namespace resource {

bool CoordinatorExecutor::Init() {
  channel_ = grpc::CreateChannel(
    FLAGS_proxy_domain, grpc::InsecureChannelCredentials());
  if (!channel_) {
    LOG(ERROR) << "Init coordinator fail!";
    return false;
  }
  return true;
}

}  // namespace resource
