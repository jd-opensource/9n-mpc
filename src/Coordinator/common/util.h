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

#ifndef COMMON_UTIL_H_
#define COMMON_UTIL_H_

#include <math.h>
#include <pthread.h>
#include <grpcpp/grpcpp.h>
#include <string>
#include <vector>
#include <fstream>
#include "glog/logging.h"
#include "google/protobuf/util/json_util.h"

namespace common {

#define NEW_BASIC_VAL(access, type, name)  \
  access: type name##_;      \
  public: const type & name() const { return name##_; } \
  public: void set_##name(const type & val) { name##_ = val; }

#define NEW_VAL(access, type, name)  \
  access: type name##_;      \
  public: const type & name() const { return name##_; } \
  public: type * mutable_##name() { return & name##_; }

inline void Trim(std::string *s) {
  if (s->empty()) {
      return;
  }
  s->erase(0, s->find_first_not_of(" "));
  s->erase(s->find_last_not_of(" ") + 1);
}

inline void Split(const std::string& str,
                  const std::string& sep,
                  std::vector<std::string>* values) {
  std::string tmp;
  std::string::size_type pos_begin = str.find_first_not_of(sep);
  std::string::size_type comma_pos = 0;

  while (pos_begin != std::string::npos) {
    comma_pos = str.find(sep, pos_begin);
    if (comma_pos != std::string::npos) {
      tmp = str.substr(pos_begin, comma_pos - pos_begin);
      pos_begin = comma_pos + sep.length();
    } else {
      tmp = str.substr(pos_begin);
      pos_begin = comma_pos;
    }
    if (!tmp.empty()) {
      values->push_back(tmp);
      tmp.clear();
    }
  }
}

inline grpc::Status ReturnErrorStatus(const std::string& err) {
  LOG(ERROR) << err;
  return grpc::Status(grpc::StatusCode::UNAVAILABLE, err);
}

inline std::string GetAppIdFromUUID(const std::string &uuid) {
  return uuid.substr(0, uuid.find('_'));
}

inline std::string GetServiceFromUUID(const std::string &uuid) {
  std::vector<std::string> vec;
  Split(uuid, "_", &vec);
  if (vec.size() < 2) return "";
  return vec[1];
}

inline bool ReadJsonFile(const std::string &file, std::string *lines) {
  assert(lines);
  std::ifstream in;
  in.open(file, std::ifstream::in);
  if (!in.is_open()) {
    LOG(ERROR) << "open json file " << file << " failed.";
    return false;
  }
  std::string line;
  while (std::getline(in, line)) {
    if (line.empty() || '#' == line[0]) continue;
    lines->append(line).append("\n");
  }
  in.close();
  return true;
}

inline bool JsonStringToMessage(const std::string& json_str,
                                google::protobuf::Message *message) {
  google::protobuf::util::Status status =
      google::protobuf::util::JsonStringToMessage(json_str, message,
                                google::protobuf::util::JsonParseOptions());
  if (!status.ok()) {
    LOG(ERROR) << "Fail to parse json string, json str is: "
               << json_str << ". reason is : " << status.error_message();
    return false;
  }
  return true;
}

inline bool MessageToJsonString(const google::protobuf::Message& message,
                                std::string* json_str) {
  google::protobuf::util::JsonOptions option;
  option.preserve_proto_field_names = true;
  google::protobuf::util::Status status =
      google::protobuf::util::MessageToJsonString(message, json_str, option);
  if (!status.ok()) {
    LOG(ERROR) << "Fail to convert message to json string"
               << ". reason is : " << status.error_message();
    return false;
  }
  return true;
}

inline bool JsonFileToMessage(
    const std::string &file,
    google::protobuf::Message *message,
    const google::protobuf::util::JsonParseOptions &options) {
  assert(message);
  std::string json_str;
  if (!ReadJsonFile(file, &json_str)) {
    LOG(ERROR) << "Fail to read json file, json str is: "
               << json_str;
    return false;
  }
  google::protobuf::util::Status status =
      google::protobuf::util::JsonStringToMessage(json_str, message, options);
  if (!status.ok()) {
    LOG(ERROR) << "Fail to parse json file, json str is: "
               << json_str << ". reason is : " << status.error_message();
    return false;
  }
  return true;
}

inline bool JsonFileToMessage(const std::string &file,
                              google::protobuf::Message *message) {
  return JsonFileToMessage(
    file, message, google::protobuf::util::JsonParseOptions());
}

class ReadLockGuard {
 public:
  explicit ReadLockGuard(pthread_rwlock_t *lock) : lock_(lock) {
    pthread_rwlock_rdlock(lock_);
  }

  ~ReadLockGuard() {
    pthread_rwlock_unlock(lock_);
  }

 private:
  pthread_rwlock_t *lock_ = nullptr;
};

class WriteLockGuard {
 public:
  explicit WriteLockGuard(pthread_rwlock_t* lock) : lock_(lock) {
    pthread_rwlock_wrlock(lock_);
  }

  ~WriteLockGuard() {
    pthread_rwlock_unlock(lock_);
  }

 private:
  pthread_rwlock_t *lock_ = nullptr;
};

}  //  namespace common

#endif
