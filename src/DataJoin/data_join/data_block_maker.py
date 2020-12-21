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

import logging
import os
import uuid
import tensorflow.compat.v1 as tf
from google.protobuf import text_format
from tensorflow.compat.v1 import gfile
from DataJoin.common import data_join_service_pb2 as data_join_pb
from DataJoin.utils.process_manager import tf_record_iterator_factory, data_block_meta_file_name_wrap, \
    block_id_wrap, data_block_file_name_wrap, partition_id_wrap
from DataJoin.utils.base import get_host_ip
import requests
from DataJoin.config import HEADERS, HTTP_SERVICE_PORT, removed_items_nums_from_buffer

host_ip = get_host_ip()
mode = os.environ.get("MODE", None)


def save_data_block_info(meta_path, block_path):
    action = getattr(requests, 'POST'.lower(), None)
    data = {'dfs_data_block_meta': meta_path, 'dfs_data_block': block_path}
    url = "http://{0}:{1}/v1/parse/data/block/meta".format(str(host_ip), HTTP_SERVICE_PORT)
    response = action(url=url, json=data, headers=HEADERS)
    res = response.json()
    logging.info('request result is :%s' % res)


class DataBlockMaker(object):
    tmp_file_path_counter = 0

    def __init__(self, data_block_dir_name, data_source_name, partition_id,
                 data_block_index, example_num_threshold=None):
        self._data_source_name = data_source_name
        self._data_block_manager = None
        self._saved_example_num = 0
        self._partition_id = partition_id
        self._data_block_meta = data_join_pb.DataBlockMeta()
        self._data_block_meta.partition_id = partition_id
        self._data_block_meta.data_block_index = data_block_index
        self._data_block_meta.follower_restart_index = 0
        self._example_num_threshold = example_num_threshold
        self._data_block_dir_name = data_block_dir_name
        self._tmp_file_path = self._make_tmp_file_path()
        self._tf_record_writer = tf.io.TFRecordWriter(self._tmp_file_path)

    def build_data_block_manager(self, data_block_manager):
        self._data_block_manager = data_block_manager

    def save(self, data_record, example_id, event_time):
        self._tf_record_writer.write(data_record)
        self._data_block_meta.example_ids.append(example_id)
        if self._saved_example_num == 0:
            self._data_block_meta.start_time = event_time
            self._data_block_meta.end_time = event_time
        else:
            if event_time < self._data_block_meta.start_time:
                self._data_block_meta.start_time = event_time
            if event_time > self._data_block_meta.end_time:
                self._data_block_meta.end_time = event_time

        self._saved_example_num += 1

    def init_maker_by_input_meta(self, data_block_meta):
        self._partition_id = data_block_meta.partition_id
        self._example_num_threshold = None
        self._data_block_meta = data_block_meta

    def set_restart_data_join_index(self, restart_data_join_index):
        self._data_block_meta.follower_restart_index = restart_data_join_index

    def is_data_block_exceed_threshold(self):
        if (self._example_num_threshold is not None and
                len(self._data_block_meta.example_ids) >=
                self._example_num_threshold):
            return True
        return False

    def save_data_record(self, record):
        self._tf_record_writer.write(record)
        self._saved_example_num += 1

    def _make_tmp_file_path(self):
        tmp_file_name = str(uuid.uuid1()) + '-{}.tmp'.format(self.tmp_file_path_counter)
        self.tmp_file_path_counter += 1
        return os.path.join(self._obtain_data_block_dir(), tmp_file_name)

    def _make_data_block_meta(self):
        meta_file_path_tmp = self._make_tmp_file_path()
        with tf.io.TFRecordWriter(meta_file_path_tmp) as meta_writer:
            meta_writer.write(text_format.MessageToString(self._data_block_meta).encode())
        if self._data_block_manager is not None:
            meta_file_path = self._data_block_manager.update_data_block_meta(
                meta_file_path_tmp, self._data_block_meta
            )
        else:
            meta_file_name = data_block_meta_file_name_wrap(self._data_source_name,
                                                            self._partition_id,
                                                            self._data_block_meta.data_block_index)
            meta_file_path = os.path.join(self._obtain_data_block_dir(), meta_file_name)
            gfile.Rename(meta_file_path_tmp, meta_file_path)
        return meta_file_path

    def data_block_finalizer(self):
        assert self._saved_example_num == len(self._data_block_meta.example_ids)
        self._tf_record_writer.close()
        if len(self._data_block_meta.example_ids) > 0:
            self._data_block_meta.block_id = block_id_wrap(self._data_source_name,
                                                           self._data_block_meta)
            data_block_path = os.path.join(
                self._obtain_data_block_dir(),
                data_block_file_name_wrap(
                    self._data_source_name,
                    self._data_block_meta
                )
            )
            gfile.Rename(self._tmp_file_path, data_block_path, True)
            meta_path = self._make_data_block_meta()
            if mode == "distribute":
                save_data_block_info(meta_path, data_block_path)
            return self._data_block_meta
        gfile.Remove(self._tmp_file_path)
        return None

    def __del__(self):
        if self._tf_record_writer is not None:
            del self._tf_record_writer

    def _obtain_data_block_dir(self):
        return os.path.join(
            self._data_block_dir_name, partition_id_wrap(self._partition_id)
        )
