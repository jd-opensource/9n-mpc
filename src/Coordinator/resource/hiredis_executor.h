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

#ifndef RESOURCE_HIREDIS_EXECUTOR_H
#define RESOURCE_HIREDIS_EXECUTOR_H

#include <vector>
#include <memory>
#include <string>
#include <mutex>
#include "glog/logging.h"
#include "common/fl_gflags.h"
#include "hiredis/hiredis.h"

namespace resource {

class HiRedisExecutor {
 public:
  HiRedisExecutor() {}
  virtual ~HiRedisExecutor() {
    redisFree(redis_ctx_);
  }

  virtual bool Init(
    const std::string &hostname, int port, const struct timeval &timeout);

  bool Lock(const std::string &str, int lock_timeout_s);
  void Unlock(const std::string &str);

  bool Get(const std::string &key, std::string *value);
  bool Set(const std::string &key, const std::string &value);
  bool Mset(const std::vector<std::string> &keys,
            const std::vector<std::string> &values);
  bool SetEx(const std::string &key, const std::string &value, int time_s);
  void Del(const std::string &key);
  void Del(const std::vector<std::string> &keys);

  bool IsString(const redisReply &reply) {
    return reply.type == REDIS_REPLY_STRING;
  }

  bool IsStatus(const redisReply &reply) {
    return reply.type == REDIS_REPLY_STATUS;
  }
 private:
  bool CheckReply(redisReply *reply);
  redisContext *redis_ctx_ = nullptr;
  const std::string lock_str_ = "fl_lock_";

  std::mutex mtx_;
};

}  // namespace resource

#endif
