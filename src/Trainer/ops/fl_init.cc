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

REGISTER_OP("FlBridgeServerInit")
    .Input("server_address: string")
    .Input("appli_id: string")
    .Input("rank_id: int32")
    .Input("role_def: string")
    .Input("rpc_service_type: int32")  // kinds of service method(Unary or
                                       // Bidirectional streaming RPCs)
    .Input("contex_metadata: string")
    .SetIsStateful()
    .Attr("config_proto: string = ''");

REGISTER_OP("FlRpcChannelInit")
    .Input("target_address: string")
    .SetIsStateful()
    .Attr("channel_type: {'TRAIN', 'DATA'}")
    .Attr("config_proto: string = ''");
