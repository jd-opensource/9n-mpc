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

#include "common/fl_gflags.h"

DEFINE_int32(port, 6666, "TCP Port of this server");
DEFINE_string(platform, "", "platform name.");

// lock
DEFINE_int32(lock_timeout_s, 1, "lock timeout");
DEFINE_int32(lock_times, 20, "lock times");
// coordinator domain
DEFINE_string(coordinator_domain, "", "coordinator_domain or ip and port");
// proxy domain
DEFINE_string(proxy_domain, "", "proxy_domain or ip and port");
// wait for registered
DEFINE_int32(wait_registered_time_ms, 1000, "");
// redis
DEFINE_string(redis_hostname, "127.0.0.1", "redis hostname");
DEFINE_int32(redis_port, 6379, "redis port");
