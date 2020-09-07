# Copyright 2020 The 9nFL Authors. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import os
import sys
import time
import logging
import threading
import six
import tensorflow.compat.v1 as tf
from tensorflow.python.framework import ops
from tensorflow.compat.v1 import data
from tensorflow.python.framework import dtypes
from tensorflow.python.framework import tensor_spec
from tensorflow.python.data.ops.dataset_ops import UnaryDataset

_fl_ops_so = None
_fl_ops_so_lock = threading.Lock()

dir_path = os.path.dirname(os.path.realpath(__file__))

#os.environ["_FILE_GET_CMD"] = dir_path + '/file_get.sh'

def load_fl_ops_lib():

    global _fl_ops_so
    # Use double-checked locking to avoid taking lock unnecessarily.
    if _fl_ops_so:
        return _fl_ops_so
    
    _fl_ops_so_lock.acquire()
    
    try:
        if _fl_ops_so:
          return _fl_ops_so
      
        _fl_ops_so = tf.load_op_library(dir_path + "/_fl_ops.so")
        return _fl_ops_so
    
    finally:
        _fl_ops_so_lock.release()


@ops.RegisterGradient("FlTensorRecvWithFakeInput")
def _RegisterGradient(op, grad):
    ops_lib = load_fl_ops_lib()
    grad_sname = op.get_attr("grad_sname")
    grad = ops_lib.fl_grad_backprop_request(grad, datamsg_sname=grad_sname)
    # unnecessary
    return tf.reduce_min(grad)

@ops.RegisterGradient("FlTensorRecvWithGradBp")
def _RegisterGradient(op, grad):
    ops_lib = load_fl_ops_lib()
    grad_sname = op.get_attr("grad_sname")
    return ops_lib.fl_grad_backprop_request(grad, datamsg_sname=grad_sname)


class FlGrpcFetchDataset(data.Dataset):

    def __init__(self, role):
  
        self._role_def = tf.convert_to_tensor(
            role, dtype=tf.string, name="role_def")
        self._ops_lib = load_fl_ops_lib()
        super(FlGrpcFetchDataset, self).__init__()
  
    def _inputs(self):
        return []
  
    def _as_variant_tensor(self):
        return self._ops_lib.fl_grpc_fetch_dataset(
            self._role_def)
  
    @property
    def output_classes(self):
        return tf.Tensor
  
    @property
    def output_shapes(self):
        return (tf.TensorShape([]))
  
    @property
    def output_types(self):
        return tf.string


class FlTFRecordDataset(UnaryDataset):

    def __init__(self, input_dataset, compression_type, buffer_size):
  
        self._input_dataset = input_dataset
        self._compression_type = ops.convert_to_tensor(
            compression_type, dtype=tf.string, name="compression_type")
        self._buffer_size = ops.convert_to_tensor(
            buffer_size, dtype=tf.int64, name="buffer_size")
       
        self._ops_lib = load_fl_ops_lib()
       
        variant_tensor = self._ops_lib.fl_tf_record_dataset(
            input_dataset._variant_tensor,  
            compression_type=self._compression_type,
            buffer_size=self._buffer_size)
    
        super(FlTFRecordDataset, self).__init__(input_dataset, variant_tensor)
  
    @property
    def element_spec(self):
        return self._input_dataset.element_spec


class FlTextLineDataset(UnaryDataset):

    def __init__(self, input_dataset, compression_type, buffer_size):
  
        self._input_dataset = input_dataset
        self._compression_type = ops.convert_to_tensor(
            compression_type, dtype=tf.string, name="compression_type")
        self._buffer_size = ops.convert_to_tensor(
            buffer_size, dtype=tf.int64, name="buffer_size")
       
        self._ops_lib = load_fl_ops_lib()
       
        variant_tensor = self._ops_lib.fl_text_line_dataset(
            input_dataset._variant_tensor,  
            compression_type=self._compression_type,
            buffer_size=self._buffer_size)
    
        super(FlTextLineDataset, self).__init__(input_dataset, variant_tensor)
  
    @property
    def element_spec(self):
        return self._input_dataset.element_spec


