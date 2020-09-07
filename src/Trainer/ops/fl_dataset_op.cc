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

using ::tensorflow::shape_inference::ScalarShape;
using ::tensorflow::shape_inference::InferenceContext;
using ::tensorflow::shape_inference::ShapeHandle;

REGISTER_OP("FlGrpcFetchDataset")
    .Input("role_def: string")
    .Output("handle: variant")
    .Attr("data_source: string = ''")
    .Attr("max_retries: int = -1")
    .Attr("timeout_in_ms: int = 0")
    .SetIsStateful()
    .SetShapeFn(ScalarShape);

REGISTER_OP("FlTextLineDataset")
    .Input("input_dataset: variant")
    .Input("compression_type: string")
    .Input("buffer_size: int64")
    .Output("handle: variant")
    .SetIsStateful()
    .SetShapeFn([](InferenceContext* c) {
      ShapeHandle unused;
      // `compression_type` could only be a scalar.
      TF_RETURN_IF_ERROR(c->WithRank(c->input(1), 0, &unused));
      // `buffer_size` could only be a scalar.
      TF_RETURN_IF_ERROR(c->WithRank(c->input(2), 0, &unused));
      return ScalarShape(c);
    });

REGISTER_OP("FlTFRecordDataset")
    .Input("input_dataset: variant")
    .Input("compression_type: string")
    .Input("buffer_size: int64")
    .Output("handle: variant")
    .Attr("file_type: string = ''")
    .SetIsStateful()
    .SetShapeFn(ScalarShape);
