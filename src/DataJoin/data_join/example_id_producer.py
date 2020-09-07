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
from DataJoin.common import data_join_service_pb2 as data_join_pb
from DataJoin.utils.process_manager import ProcessorManager
from DataJoin.config import sync_example_id_nums
from DataJoin.data_join.raw_data_loader import InitRawDataLoading


class ExampleIdProducer(object):

    def __init__(self, peer_client, raw_data_dir, partition_id,
                 rank_id, raw_data_options, mode, init_raw_data_loading_object):
        self._lock = threading.Lock()
        self._peer_client = peer_client
        self._raw_data_dir = raw_data_dir
        self._rank_id = rank_id
        self._mode = mode
        self._raw_data_options = raw_data_options
        self._init_loading = init_raw_data_loading_object
        self._partition_id = partition_id
        self._processor_start = False
        self._processor_routine = dict()

    def start_processors(self):
        with self._lock:
            if not self._processor_start:
                self._processor_routine.update(example_id_sender_processor=ProcessorManager(
                    'example_id_sender_processor',
                    self._send_example_id_processor,
                    self._impl_send_example_id_factor, 6))

                for key, processor in self._processor_routine.items():
                    processor.active_processor()
                self._processor_start = True
                self._enable_example_id_sender_processor()

    def stop_processors(self):
        wait_stop = True
        with self._lock:
            if self._processor_start:
                wait_stop = True
                self._processor_start = False
        if wait_stop:
            for processor in self._processor_routine.values():
                processor.inactive_processor()

    def _enable_example_id_sender_processor(self):
        self._processor_routine['example_id_sender_processor'].enable_processor()

    def _send_example_id_processor(self, init_loading):
        if not init_loading.follower_finished:
            with self._impl_example_id_sender(init_loading) as sender:
                init_loading.follower_finished = sender()
        if init_loading.partition_finished:
            self._finish_send_example_id_to_consumer(init_loading)

    def _impl_send_example_id_factor(self):
        with self._lock:
            if self._init_loading is not None:
                self._processor_routine['example_id_sender_processor'].build_impl_processor_parameter(
                    self._init_loading
                )
            return self._init_loading is not None

    @contextmanager
    def _impl_example_id_sender(self, init_loading):
        init_loading.acquire_stale_with_sender()

        def sender():
            next_index, follower_finished = \
                self._start_notify_consumer_to_sync_partition(init_loading)
            if follower_finished:
                return True
            examples_list = []
            for (key, example) in init_loading.item_dict.items():
                examples_list.append(example)
                if len(examples_list) > sync_example_id_nums:
                    self._send_example_ids_to_consumer(examples_list, init_loading)
                    examples_list = []
            if len(examples_list) >= 0:
                self._send_example_ids_to_consumer(examples_list, init_loading, True)
            init_loading.partition_finished = True
            return False

        yield sender
        init_loading.release_stale_with_sender()

    def _start_notify_consumer_to_sync_partition(self, init_loading):
        example_producer_request = data_join_pb.StartPartitionRequest(
            rank_id=self._rank_id,
            partition_id=init_loading.partition_id
        )
        example_consumer_response = self._peer_client.StartPartition(example_producer_request)
        if example_consumer_response.status.code != 0:
            raise RuntimeError(
                "call example consumer for starting to send partition_id Failed :for " \
                "partition_id: %s, error_msg :%s" % (
                    init_loading.partition_id, example_consumer_response.status.error_message)
            )
        return example_consumer_response.next_index, example_consumer_response.finished

    def _send_example_ids_to_consumer(self, examples, init_loading, finished=False):
        send_examples = data_join_pb.SyncContent(
            lite_example_ids=data_join_pb.LiteExampleIds(
                partition_id=init_loading.partition_id,
                begin_index=0,
                finished=finished
            )
        )
        if len(examples) > 0:
            for exam in examples:
                send_examples.lite_example_ids.example_id.append(exam.example_id)
                send_examples.lite_example_ids.event_time.append(exam.event_time)
        request = data_join_pb.SyncPartitionRequest(
            rank_id=self._rank_id,
            partition_id=init_loading.partition_id,
            compressed=False,
            content_bytes=send_examples.SerializeToString()
        )
        response = self._peer_client.SyncPartition(request)
        if response.code != 0:
            raise RuntimeError(
                "Example Id send {} example ids Failed," \
                "error msg {}".format(len(examples), response.error_message)
            )

    def _finish_send_example_id_to_consumer(self, init_loading):
        if not init_loading.follower_finished:
            logging.info("notified  example id consumer send example  has been finished")
            request = data_join_pb.FinishPartitionRequest(
                rank_id=self._rank_id,
                partition_id=init_loading.partition_id
            )
            response = self._peer_client.FinishPartition(request)
            if response.status.code != 0:
                raise RuntimeError(
                    "notify example id consumer finish partition Failed" \
                    "error msg: {}".format(response.status.error_message)
                )
            init_loading.follower_finished = response.finished
        if not init_loading.follower_finished:
            logging.info("Example id Consumer is still appending example id into queue " \
                         "for partition_id %d ", init_loading.partition_id)
            return False

        logging.info("Example id Consumer has finished append example id into queue " \
                     "for partition_id %d ", init_loading.partition_id)
        return True
