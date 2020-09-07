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

#include "tensorflow/core/framework/common_shape_fns.h"
#include "tensorflow/core/framework/op.h"
#include "tensorflow/core/framework/shape_inference.h"

using namespace tensorflow;
using shape_inference::InferenceContext;
using shape_inference::ShapeHandle;

REGISTER_OP("FlChannelConnect")
    .Output("status_code: int32")
    .Output("status_message: string")
    .SetIsStateful()
    .Attr("config_proto: string = ''");

REGISTER_OP("FlWaitPeerReady")
    .Output("status_code: int32")
    .Output("status_message: string")
    .SetIsStateful()
    .Attr("config_proto: string = ''");

REGISTER_OP("FlChannelHeartbeat")
    .Output("status_code: int32")
    .Output("status_message: string")
    .SetIsStateful()
    .Attr("config_proto: string = ''");
