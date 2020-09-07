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


REGISTER_OP("FlTensorRecv")
    .Output("output: T")
    .SetIsStateful()
    .Attr("T: {float, int64} = DT_FLOAT")
    .Attr("max_retries: int = -1")
    .Attr("timeout_in_ms: int = 0")
    .Attr("datamsg_rname: string = 'output:0'")  // recv tensor name
    .Attr("datamsg_type: string = '/FlDataMessage'");

REGISTER_OP("FlTensorRecvWithGradBp")
    .Output("output: float")
    .SetIsStateful()
    .Attr("max_retries: int = -1")
    .Attr("timeout_in_ms: int = 0")
    .Attr("datamsg_rname: string = 'output:0'")  // recv tensor name
    .Attr("datamsg_type: string = '/FlDataMessage'")
    .Attr("grad_sname: string = 'grad:0'");  // the registered gradient send
                                            // tensor name

REGISTER_OP("FlTensorRecvWithFakeInput")
    .Input("input_fake: float")
    .Output("output: float")
    .SetIsStateful()
    .Attr("max_retries: int = -1")
    .Attr("timeout_in_ms: int = 0")
    .Attr("datamsg_rname: string = 'output:0'")  // recv tensor name
    .Attr("datamsg_type: string = '/FlDataMessage'")
    .Attr("grad_sname: string = 'grad:0'");  // the registered gradient send
                                            // tensor name

REGISTER_OP("FlGradRecv")
    .Output("output: float")
    .SetIsStateful()
    .Attr("max_retries: int = -1")
    .Attr("timeout_in_ms: int = 0")
    .Attr("datamsg_rname: string = 'grad:0'")  // recv tensor name
    .Attr("datamsg_type: string = '/FlDataMessage'");

REGISTER_OP("FlTrainStart")
    .Input("input_proto: string")
    .Output("output: string")
    .SetIsStateful()
    .Attr("max_retries: int = -1")
    .Attr("timeout_in_ms: int = 0")
    .Attr("datamsg_type: string = '/FlTrainStart'");

REGISTER_OP("FlTrainFollow")
    .Output("status_code: int32")
    .Output("status_message: string")
    .SetIsStateful()
    .Attr("max_retries: int = 3")
    .Attr("timeout_in_ms: int = 0")
    .Attr("datamsg_type: string = '/FlTrainStart'");

REGISTER_OP("FlTensorSend")
    .Input("input: T")
    .Output("output: T")
    .SetIsStateful()
    .Attr("T: {float, int64} = DT_INT64")
    .Attr("max_retries: int = -1")
    .Attr("timeout_in_ms: int = 0")
    .Attr("datamsg_sname: string = 'output:0'")  // sent tensor name
    .Attr("datamsg_type: string = '/FlDataMessage'");

REGISTER_OP("FlGradBackpropRequest")
    .Input("input: float")
    .Output("output: float")
    .SetIsStateful()
    .Attr("max_retries: int = -1")
    .Attr("timeout_in_ms: int = 0")
    .Attr("datamsg_sname: string = 'grad:0'")  // sent tensor name
    .Attr("datamsg_type: string = '/FlDataMessage'");

REGISTER_OP("FlTensorSendRecv")
    .Input("input: float")
    .Output("output: float")
    .SetIsStateful()
    .Attr("max_retries: int = -1")
    .Attr("timeout_in_ms: int = 0")
    .Attr("datamsg_sname: string = 'output:0'")  // sent tensor name
    .Attr("datamsg_rname: string = 'grad:0'")    // recv tensor name
    .Attr("datamsg_type: string = '/FlDataMessage'");

REGISTER_OP("FlTrainStepCommit")
    .SetIsStateful()
    .Attr("max_retries: int = -1")
    .Attr("timeout_in_ms: int = 0")
    .Attr("datamsg_type: string = '/FlStepCommit'");
