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

from DataJoin.utils.process_manager import ProcessorManager
from DataJoin.data_join.data_block_saver import DataBlockSaver


class DataBlockConsumer(object):
    class DataBlockConsumerWrapper(object):
        def __init__(self, partition_id, init_raw_data_loading,
                     data_block_dir, data_source_name):
            self.data_block_saver_manager = DataBlockSaver(
                partition_id, init_raw_data_loading,
                data_block_dir, data_source_name
            )
            self.partition_id = partition_id

        def __getattr__(self, attr):
            return getattr(self.data_block_saver_manager, attr)

    def __init__(self, partition_id, raw_data_options, init_raw_data_loading_object,
                 data_block_dir, data_source_name):
        self._data_source_name = data_source_name
        self._lock = threading.Lock()
        self._init_loading = init_raw_data_loading_object
        self._raw_data_options = raw_data_options
        self._partition_id = partition_id
        self._data_block_dir = data_block_dir
        self._processor_routine_map = None
        self.data_block_consumer_wrap = DataBlockConsumer.DataBlockConsumerWrapper(
            partition_id, self._init_loading,
            self._data_block_dir, self._data_source_name
        )
        self._processor_started = False

    def fetch_partition_id(self):
        with self._lock:
            return self.data_block_consumer_wrap.partition_id

    def append_data_items_from_producer(self, req):
        assert req.HasField('data_block_meta'), \
            "the request must has filed :data_block_meta for DataBlockConsumer"
        with self._lock:
            self._is_synced_partition(req.data_block_meta.partition_id)
            return self.data_block_consumer_wrap.append_sent_data_block_meta_to_buffer(
                req.data_block_meta
            )

    def finish_partition_transmit(self, partition_id):
        with self._lock:
            self._is_synced_partition(partition_id)
            self.data_block_consumer_wrap.finish_sent_data_block_meta()
            return not self.data_block_consumer_wrap.is_need_save()

    def partition_syncer_to_consumer(self, partition_id):
        with self._lock:
            if self.data_block_consumer_wrap is not None and \
                    self.data_block_consumer_wrap.partition_id != partition_id:
                raise RuntimeError(
                    "partition {} does not match peer_partition_id:{} ".format(
                        self.data_block_consumer_wrap.partition_id, partition_id)
                )
            next_data_block_index = self.data_block_consumer_wrap. \
                fetch_next_data_block_index()
            is_meta_finished = self.data_block_consumer_wrap. \
                is_finish_sent_data_block_meta()
            return next_data_block_index, is_meta_finished

    def reset_consumer_wrap(self, partition_id):
        with self._lock:
            if not self._is_synced_partition(partition_id):
                return
            if not self.data_block_consumer_wrap. \
                    is_finish_sent_data_block_meta() or \
                    self.data_block_consumer_wrap.is_need_save():
                raise RuntimeError("partition {} is consuming "
                                   "data block meta ".format(partition_id))
            self.data_block_consumer_wrap = None

    def start_processors(self):
        with self._lock:
            if not self._processor_started:
                assert self._processor_routine_map is None, \
                    "the data block consumer processor is not None" \
                    " when start processor"
                self._processor_routine_map = ProcessorManager(
                    'data_block_consumer',
                    self._data_block_consumer_processor,
                    self._data_block_consumer_processor_factor, 6
                )
                self._processor_routine_map.active_processor()
                self._processor_started = True

    def _data_block_consumer_processor_factor(self):
        with self._lock:
            if self.data_block_consumer_wrap is not None \
                    and self.data_block_consumer_wrap.is_need_save():
                self._processor_routine_map.build_impl_processor_parameter(self.data_block_consumer_wrap)
                return True
            return False

    def _is_synced_partition(self, partition_id):
        if partition_id != self.data_block_consumer_wrap.partition_id:
            raise RuntimeError(
                "partition id:{} does not match peer_partition_id {}".format(
                    self.data_block_consumer_wrap.partition_id, partition_id)
            )
        return True

    def _data_block_consumer_processor(self, data_block_consumer_wrap):
        with data_block_consumer_wrap.build_data_block_saver() as saver:
            saver()

    def stop_processors(self):
        saver_processor = None
        with self._lock:
            if self._processor_routine_map is not None:
                saver_processor = self._processor_routine_map
                self._processor_routine_map = None
        if saver_processor is not None:
            saver_processor.inactive_processor()
