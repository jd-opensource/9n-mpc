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
# coding: utf-8

import threading
import logging
import os
from google.protobuf import text_format
from tensorflow.compat.v1 import gfile
from DataJoin.common import data_join_service_pb2 as data_join_pb
from DataJoin.utils.process_manager import tf_record_iterator_factory, data_block_meta_file_name_wrap, \
    block_id_wrap, data_block_file_name_wrap, partition_id_wrap
from DataJoin.config import HEADERS, HTTP_SERVICE_PORT, removed_items_nums_from_buffer,\
    data_block_index_threshold


class DataBlockManager(object):
    def __init__(self, partition_id, data_block_dir, data_source_name):
        self._lock = threading.Lock()
        self._partition_id = partition_id
        self._data_block_dir = data_block_dir
        self._data_source_name = data_source_name
        self._data_block_meta_memory_buffer = dict()
        self._saved_data_block_index = None
        self._saving_data_block_index = None
        self._create_data_block_dir_if_need()
        self._sync_saved_data_block_index()

    def acquire_produced_data_block_number(self):
        with self._lock:
            self._sync_saved_data_block_index()
            return self._saved_data_block_index + 1

    def acquire_data_block_meta_by_index(self, index):
        with self._lock:
            if index < 0:
                raise IndexError("{} index is not in range".format(index))
            self._sync_saved_data_block_index()
            return self._sync_data_block_meta(index)

    def update_data_block_meta(self, meta_file_path_tmp, data_block_meta):
        if not gfile.Exists(meta_file_path_tmp):
            raise RuntimeError("the tmp file does not existed {}".format(meta_file_path_tmp))
        with self._lock:
            if self._saving_data_block_index is not None:
                raise RuntimeError(
                    "data block of index {} is saving".format(self._saving_data_block_index)
                )
            data_block_index = data_block_meta.data_block_index
            if data_block_index != self._saved_data_block_index + 1:
                raise IndexError("the data_block_index must be consecutive")
            self._saving_data_block_index = data_block_index
            data_block_meta_path = self._acquire_data_block_meta_path(data_block_index)
            gfile.Rename(meta_file_path_tmp, data_block_meta_path)
            self._saving_data_block_index = None
            self._saved_data_block_index = data_block_index
            self._remove_item_from_data_block_memory_buffer()
            self._data_block_meta_memory_buffer[data_block_index] = data_block_meta
            return data_block_meta_path

    def _data_block_dir_wrap(self):
        return os.path.join(self._data_block_dir,
                            partition_id_wrap(self._partition_id))

    def _create_data_block_dir_if_need(self):
        data_block_dir_wrap = self._data_block_dir_wrap()
        if not gfile.Exists(data_block_dir_wrap):
            gfile.MakeDirs(data_block_dir_wrap)
        if not gfile.IsDirectory(data_block_dir_wrap):
            logging.fatal("%s must be directory", data_block_dir_wrap)
            os._exit(-1)

    def _sync_data_block_meta(self, data_block_index):
        if self._saved_data_block_index < 0 or data_block_index > self._saved_data_block_index:
            return None
        if data_block_index not in self._data_block_meta_memory_buffer:
            data_block_meta_file_path = self._acquire_data_block_meta_path(data_block_index)
            with tf_record_iterator_factory(data_block_meta_file_path) as record_iter:
                self._data_block_meta_memory_buffer[data_block_index] = \
                    text_format.Parse(next(record_iter), data_join_pb.DataBlockMeta())
            self._remove_item_from_data_block_memory_buffer()
        return self._data_block_meta_memory_buffer[data_block_index]

    def _sync_saved_data_block_index(self):
        if self._saved_data_block_index is None:
            assert self._saving_data_block_index is None, \
                "no data block index is saving when no saved index"
            low_index = 0
            high_index = data_block_index_threshold
            while low_index <= high_index:
                data_index = (low_index + high_index) // 2
                file_name = self._acquire_data_block_meta_path(data_index)
                if gfile.Exists(file_name):
                    low_index = data_index + 1
                else:
                    high_index = data_index - 1
            self._saved_data_block_index = high_index
        elif self._saving_data_block_index is not None:
            assert self._saving_data_block_index == self._saved_data_block_index + 1, \
                "the saving index should be next of saved index " \
                "{} != {} + 1".format(self._saving_data_block_index, self._saved_data_block_index)
            file_name = self._acquire_data_block_meta_path(self._saving_data_block_index)
            if not gfile.Exists(file_name):
                self._saving_data_block_index = None
            else:
                self._saved_data_block_index = self._saving_data_block_index
                self._saving_data_block_index = None

    def _remove_item_from_data_block_memory_buffer(self):
        while len(self._data_block_meta_memory_buffer) > removed_items_nums_from_buffer:
            self._data_block_meta_memory_buffer.popitem()

    def _acquire_data_block_meta_path(self, data_block_index):
        data_block_meta_file_name = data_block_meta_file_name_wrap(
            self._data_source_name,
            self._partition_id, data_block_index
        )
        return os.path.join(self._data_block_dir_wrap(), data_block_meta_file_name)
