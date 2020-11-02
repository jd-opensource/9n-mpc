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

#include <assert.h>
#include <memory>
#include <string>
#include "resource/hiredis_executor.h"
#include "common/util.h"

namespace resource {

bool HiRedisExecutor::Get(const std::string &key,
                        std::string *value) {
  assert(value);
  static const std::string get_str("GET ");
  std::string command(get_str);
  std::lock_guard<std::mutex> lock(mtx_);
  redisReply *reply = static_cast<redisReply*>(
    redisCommand(redis_ctx_, command.append(key).c_str()));
  if (!IsString(*reply) || !reply->str) {
    LOG(ERROR) << "Get key fail! key: " << key;
    freeReplyObject(reply);
    return false;
  }
  value->assign(reply->str, reply->len);
  freeReplyObject(reply);
  return true;
}

bool HiRedisExecutor::Set(const std::string &key,
                        const std::string &value) {
  static const std::string set_str("SET ");
  std::string command(set_str);
  command.append(key).append(" ").append(value);
  std::lock_guard<std::mutex> lock(mtx_);
  redisReply *reply = static_cast<redisReply*>(
    redisCommand(redis_ctx_, command.c_str()));
  bool ret = CheckReply(reply);
  freeReplyObject(reply);
  if (!ret) {
    LOG(ERROR) << "Set key fail! key: " << key << " , value : " << value;
    return false;
  }
  return true;
}

bool HiRedisExecutor::Mset(const std::vector<std::string> &keys,
    const std::vector<std::string> &values) {
  static const std::string set_str("MSET");
  if (keys.size() != values.size()) {
    LOG(ERROR) << "Mset keys num not equal value size!";
    return false;
  }
  std::string command(set_str);
  for (int i = 0; i < keys.size(); ++i) {
    command.append(" ").append(keys[i]).append(" ").append(values[i]);
  }
  std::lock_guard<std::mutex> lock(mtx_);
  redisReply *reply = static_cast<redisReply*>(
    redisCommand(redis_ctx_, command.c_str()));
  bool ret = CheckReply(reply);
  freeReplyObject(reply);
  if (!ret) {
    LOG(ERROR) << "MSet key fail!";
    return false;
  }
  return true;
}

bool HiRedisExecutor::SetEx(const std::string &key,
    const std::string &value, int time_s) {
  std::string tmp_value(value);
  static const std::string ex_str(" EX ");
  tmp_value.append(ex_str).append(std::to_string(time_s));
  return this->Set(key, tmp_value.c_str());
}

bool HiRedisExecutor::Lock(const std::string &str, int lock_timeout_s) {
  std::string value;
  if (this->Get(lock_str_ + str, &value)) {
    LOG(INFO) << "Lock fail, has been locked! key " << str;
    return false;
  }
  if (!this->SetEx(lock_str_ + str, lock_str_, lock_timeout_s)) {
    LOG(ERROR) << "DistributedLock to lock redis fail! key: " << str;
    return false;
  }
  return true;
}

void HiRedisExecutor::Unlock(const std::string &str) {
  this->Del(lock_str_ + str);
}

void HiRedisExecutor::Del(const std::string& key) {
  static const std::string del_str("DEL ");
  std::string command(del_str);
  std::lock_guard<std::mutex> lock(mtx_);
  redisReply *reply = static_cast<redisReply*>(
    redisCommand(redis_ctx_, command.append(key).c_str()));
  freeReplyObject(reply);
}

void HiRedisExecutor::Del(const std::vector<std::string> &keys) {
  std::string keys_str;
  for (const auto &key : keys) {
    keys_str.append(key);
  }
  this->Del(keys_str);
}

bool HiRedisExecutor::Init(
    const std::string &hostname, int port,
    const struct timeval &timeout) {
  redis_ctx_ = redisConnectWithTimeout(
    hostname.c_str(), port, timeout);
  if (redis_ctx_ == NULL || redis_ctx_->err) {
    if (redis_ctx_) {
      LOG(ERROR) << "Connection error: " << redis_ctx_->errstr;
      redisFree(redis_ctx_);
    } else {
      LOG(ERROR) << "Connection error: can't allocate redis context";
    }
    return false;
  }
  return true;
}

bool HiRedisExecutor::CheckReply(redisReply *reply) {
  if (!IsStatus(*reply) || !reply->str) {
    LOG(ERROR) << "reply.type = " << reply->type;
    return false;
  }
  if (strcmp(reply->str, "OK") != 0) {
    LOG(ERROR) << "reply.str = " << reply->str;
    return false;
  }
  return true;
}

}  // namespace resource
