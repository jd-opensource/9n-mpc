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

#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <cstdint>
#include <iostream>
#include <sstream>
#include <string>
#include <unordered_map>
#include <memory>

#include "tensorflow/contrib/jdfl/rpc/rpc_bridge/fl_utils.h"
#include "tensorflow/core/platform/env.h"
#include "tensorflow/core/util/env_var.h"

using namespace ::tensorflow;
using ::tensorflow::strings::StrAppend;

namespace jdfl {

static constexpr const char* const FILES_TMP_DIR = "_tmp/";

static constexpr const char* const FILE_GET_CMD = "file_get.sh";

static constexpr const char* const ENV_FILE_GET_CMD = "_FILE_GET_CMD";

static constexpr const char* const ENV_FILE_CLEAN_CMD = "_FILE_CLEAN_CMD";

namespace {

bool io_direct = false;

std::string ParseEnvConfig(const std::string& env,
                           const std::string& default_val) {
  std::string val;
  Status s = ReadStringFromEnvVar(env, default_val, &val);
  if (!s.ok()) {
    val = default_val;
  }
  LOG(INFO) << "get from env: [" << val << "]";
  return val;
}

int32_t LogLevelEnvChk() {
  const char* dval = getenv("_FL_DEBUG");
  if (!dval) {
    return 0;
  }
  std::string log_level(dval);
  std::istringstream ss(log_level);
  uint32_t level;
  if (!(ss >> level)) {
    level = 0;
  }
  return level;
}

bool GetCWD(string* dir) {
  size_t len = 512;
  std::unique_ptr<char[]> a(new char[len]);
  while (true) {
    char* p = getcwd(a.get(), len);
    if (p != nullptr) {
      *dir = p;
      return true;
    } else if (errno == ERANGE) {
      len += len;
      a.reset(new char[len]);
    } else {
      return false;
    }
  }
}

std::string ParseFileTmpDir() {
  std::string cwd;
  if (GetCWD(&cwd)) {
    StrAppend(&cwd, "/", FILES_TMP_DIR);
  } else {
    cwd = "./";
    StrAppend(&cwd, FILES_TMP_DIR);
  }
  LOG(INFO) << "Files cache dir: " << cwd;
  return cwd;
}
}  // namespace

int PrepareFile(const string& file_src, std::string* out_fname) {
  static std::string cmd_path = ParseEnvConfig(ENV_FILE_GET_CMD, "");

  int pos = file_src.find_last_of('/');
  std::string file = file_src.substr(pos + 1);
  if (file.empty()) {
    LOG(ERROR) << "invalid file: " << file_src;
    return -1;
  }

  if (!cmd_path.empty()) {
    std::string fname;
    do {
      uint64_t tp = Env::Default()->NowMicros();
      fname = file + "-" + std::to_string(tp);
    } while (Env::Default()->FileExists(LocalFileDir() + "/" + fname).ok());

    std::ostringstream run_cmd;
    run_cmd << "sh " << cmd_path << " " << file_src << " "
            << LocalFileDir() + "/" << fname;

    LOG(INFO) << "PrepareFile: " << run_cmd.str();
    int ret = system(run_cmd.str().c_str());
    if (!ret) {
      *out_fname = LocalFileDir() + "/" + fname;
    }
    return ret;
  } else {
    io_direct = true;
    *out_fname = file_src;
    LOG(INFO) << "PrepareFile: " << *out_fname;
    return 0;
  }
}

int CleanFile(const std::string& fname) {
  static std::string cmd_path = ParseEnvConfig(ENV_FILE_CLEAN_CMD, "");

  int ret = 0;
  if (cmd_path.empty()) {
    if (!io_direct) {
      // default action
      if (access(fname.c_str(), F_OK) == 0) {
        ret = remove(fname.c_str());
        LOG(INFO) << "remove [" << fname << "] with exit code : " << ret;
      } else {
        LOG(ERROR) << "file [" << fname << "] not exist, do nothing.";
        ret = -1;
      }
    }
  } else {
    // run env provided script
    std::ostringstream run_cmd;
    run_cmd << "sh " << cmd_path << " " << fname;
    LOG(INFO) << "CleanFile: " << run_cmd.str();
    ret = system(run_cmd.str().c_str());
  }

  return ret;
}

const std::string& LocalFileDir() {
  static std::string tmp_dir = ParseFileTmpDir();
  return tmp_dir;
}

int32_t FlDebugging() {
  static int32_t log_level = LogLevelEnvChk();
  return log_level;
}
}  // namespace jdfl
