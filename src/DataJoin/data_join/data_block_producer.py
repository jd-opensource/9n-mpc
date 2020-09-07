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
from contextlib import contextmanager
import zlib
from DataJoin.common import data_join_service_pb2 as data_join_pb
from DataJoin.utils.process_manager import ProcessorManager
from DataJoin.data_join.data_joiner_builder.data_joiner import DataJoiner
from DataJoin.data_join.data_joiner_builder.memory_data_joiner import MemoryDataJoiner


class DataBlockProducer(object):
    class DataJoinerWrapper(object):
        def __init__(self,
                     example_joiner_options, raw_data_options, data_block_dir,
                     data_source_name, raw_data_dir, partition_id, mode, queue):
            self.data_joiner = DataJoiner.build_data_joiner(example_joiner_options,
                                                            raw_data_options,
                                                            data_block_dir,
                                                            data_source_name,
                                                            raw_data_dir,
                                                            partition_id,
                                                            mode, queue)
            self.next_data_block_index = 0
            self.data_block_producer_finished = False

        def get_next_data_block_meta(self):
            assert self.next_data_block_index >= 0
            return self.data_joiner.acquire_data_block_meta_by_index(
                self.next_data_block_index
            )

        def __getattr__(self, attribute):
            return getattr(self.data_joiner, attribute)

    def __init__(self, peer_client, rank_id, raw_data_dir,
                 raw_data_options, example_joiner_options,
                 partition_id, queue, mode, data_block_dir,
                 data_source_name):
        self._lock = threading.Lock()
        self._data_source_name = data_source_name
        self._data_block_dir = data_block_dir
        self._peer_client = peer_client
        self._raw_data_dir = raw_data_dir
        self._rank_id = rank_id
        self._partition_id = partition_id
        self._mode = mode
        self._raw_data_options = raw_data_options
        self._queue = queue
        self._data_join_wrap = None
        self._example_joiner_options = example_joiner_options
        self._processor_start = False
        self._processor_routine = dict()

    def start_processors(self):
        with self._lock:
            if not self._processor_start:
                self._processor_routine.update(
                    build_data_joiner=ProcessorManager(
                        'build_data_joiner',
                        self._build_data_joiner_processor,
                        self._impl_build_data_joiner_factor, 6),
                    data_joiner=ProcessorManager(
                        'data_joiner',
                        self._data_join_processor,
                        self._impl_data_join_factor, 5),
                    data_block_meta_sender=ProcessorManager(
                        'data_block_meta_sender',
                        self._send_data_block_meta_processor,
                        self._impl_send_data_block_meta_factor, 5)
                )

                for processor in self._processor_routine.values():
                    processor.active_processor()
                self._processor_start = True

    def _build_data_joiner_processor(self):
        data_join_wrap = DataBlockProducer.DataJoinerWrapper(
            self._example_joiner_options, self._raw_data_options,
            self._data_block_dir,
            self._data_source_name, self._raw_data_dir,
            self._partition_id, self._mode, self._queue
        )
        with self._lock:
            assert self._data_join_wrap is None
            self._data_join_wrap = data_join_wrap
            self._enable_data_join_processor()
            self._enable_data_block_meta_sender()

    def _enable_build_data_joiner_processor(self):
        self._data_join_wrap = None
        self._processor_routine.get('build_data_joiner').enable_processor()

    def stop_processors(self):
        wait_stop = True
        with self._lock:
            if self._processor_start:
                wait_stop = True
                self._processor_start = False
        if wait_stop:
            for processor in self._processor_routine.values():
                processor.inactive_processor()

    def _enable_data_join_processor(self):
        self._processor_routine.get('data_joiner').enable_processor()

    def _data_join_processor(self, data_join_wrap):
        assert isinstance(data_join_wrap, DataBlockProducer.DataJoinerWrapper)
        if data_join_wrap.data_join_switch():
            with data_join_wrap.data_joiner_factory() as joiner:
                for data_block_meta in joiner:
                    if data_block_meta is None:
                        continue
                    self._enable_data_block_meta_sender()

    def _impl_data_join_factor(self):
        with self._lock:
            if self._data_join_wrap is not None:
                self._processor_routine.get('data_joiner').build_impl_processor_parameter(self._data_join_wrap)
            return self._data_join_wrap is not None

    def _enable_data_block_meta_sender(self):
        self._processor_routine.get('data_block_meta_sender').enable_processor()

    def _impl_build_data_joiner_factor(self):
        with self._lock:
            return self._data_join_wrap is None

    def _send_data_block_meta_processor(self, data_join_wrap):
        assert isinstance(data_join_wrap, DataBlockProducer.DataJoinerWrapper)
        joined_finished = False
        if not data_join_wrap.data_block_producer_finished:
            with self._send_data_block_meta_executor(data_join_wrap) as send_executor:
                joined_finished = send_executor()
        if joined_finished or data_join_wrap.data_block_producer_finished:
            self.notify_consumer_finish_send_meta(data_join_wrap)

    def _send_data_block_meta_to_consumer(self, data_block_meta):
        str_data_block_meta = data_join_pb.SyncContent(data_block_meta=data_block_meta).SerializeToString()
        request = data_join_pb.SyncPartitionRequest(
            rank_id=self._rank_id,
            partition_id=self._partition_id,
            content_bytes=str_data_block_meta,
            compressed=False
        )
        if len(str_data_block_meta) > (2 << 20):
            compressed_data_block_meta = zlib.compress(str_data_block_meta, 5)
            if len(compressed_data_block_meta) < len(str_data_block_meta) * 0.8:
                request.content_bytes = compressed_data_block_meta
                request.compressed = True
        peer_response = self._peer_client.SyncPartition(request)
        if peer_response.code != 0:
            raise RuntimeError(
                "data block producer call data block consumer for sending"
                " data block meta Failed {} data_block_index: {}," \
                "error msg {}".format(data_block_meta.block_id,
                                      data_block_meta.data_block_index,
                                      peer_response.error_message)
            )

    def _impl_send_data_block_meta_factor(self):
        with self._lock:
            if self._data_join_wrap is not None:
                self._processor_routine.get('data_block_meta_sender').build_impl_processor_parameter(
                    self._data_join_wrap
                )
            return self._data_join_wrap is not None

    def _sync_data_block_meta_sender_status(self, data_join_wrap):
        assert isinstance(data_join_wrap, DataBlockProducer.DataJoinerWrapper)
        data_block_resquest = data_join_pb.StartPartitionRequest(
            rank_id=self._rank_id,
            partition_id=data_join_wrap._partition_id
        )
        data_block_response = self._peer_client.StartPartition(data_block_resquest)
        if data_block_response.status.code != 0:
            raise RuntimeError(
                "Failed to call data block consumer for syncing data block meta sender status "
                "for partition_id {}, error msg {}".format(data_join_wrap._partition_id,
                                                           data_block_response.status.error_message)
            )
        return data_block_response.next_index, data_block_response.finished

    @contextmanager
    def _send_data_block_meta_executor(self, data_join_wrap):
        assert isinstance(data_join_wrap, DataBlockProducer.DataJoinerWrapper)

        def send_executor():
            data_join_wrap.next_data_block_index, data_join_wrap.data_block_producer_finished = \
                self._sync_data_block_meta_sender_status(data_join_wrap)
            join_state = False
            while not data_join_wrap.data_block_producer_finished:
                logging.info("Next Data Block Index : %s" % data_join_wrap.next_data_block_index)
                join_finished, meta = data_join_wrap.get_next_data_block_meta()
                if meta is None:
                    break
                logging.info("Send Data Block Meta : %s" % meta.block_id)
                self._send_data_block_meta_to_consumer(meta)
                data_join_wrap.next_data_block_index += 1
            return join_state

        yield send_executor

    def notify_consumer_finish_send_meta(self, data_join_wrap):
        assert isinstance(data_join_wrap, DataBlockProducer.DataJoinerWrapper)
        assert data_join_wrap.is_data_joiner_finished()
        if not data_join_wrap.data_block_producer_finished:
            request = data_join_pb.FinishPartitionRequest(
                rank_id=self._rank_id,
                partition_id=data_join_wrap._partition_id
            )
            response = self._peer_client.FinishPartition(request)
            if response.status.code != 0:
                raise RuntimeError(
                    "Data block producer call Data block consumer for finishing partition Failed " \
                    "error msg : {}".format(response.status.error_message)
                )
            data_join_wrap.data_block_producer_finished = response.finished

        if not data_join_wrap.data_block_producer_finished:
            logging.info(
                "Need to wait reason: data block is still producing for partition_id %s " % data_join_wrap._partition_id)
            return False
        logging.info("data block producing has been finished for partition_id %s" % data_join_wrap._partition_id)
        return True
