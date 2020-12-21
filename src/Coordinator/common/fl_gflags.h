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

#ifndef FL_COMMON_FL_GFLAGS
#define FL_COMMON_FL_GFLAGS

#include "gflags/gflags.h"

DECLARE_int32(port);
DECLARE_string(platform);

DECLARE_int32(lock_timeout_s);
DECLARE_int32(lock_times);
// proxy
DECLARE_string(proxy_url);
// coordinator domain
DECLARE_string(coordinator_domain);
// proxy domain
DECLARE_string(proxy_domain);
// wait for registered
DECLARE_int32(wait_registered_time_ms);
// redis
DECLARE_string(redis_hostname);
DECLARE_int32(redis_port);


#endif
