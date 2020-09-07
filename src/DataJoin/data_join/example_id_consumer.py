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
from DataJoin.data_join.example_id_appender import AppendExamplesManager


class ExampleIdConsumer(object):
    class ExampleIdAppender(object):
        def __init__(self, queue, partition_id):
            self.example_id_append_manager = \
                AppendExamplesManager(queue, partition_id)
            self.partition_id = partition_id

        def __getattr__(self, attr):
            return getattr(self.example_id_append_manager, attr)

    def __init__(self, partition_id, queue):
        self._lock = threading.Lock()
        self._queue = queue
        self._partition_id = partition_id
        self.example_appender = self.ExampleIdAppender(self._queue, self._partition_id)

    def partition_syncer_to_consumer(self, partition_id):
        with self._lock:
            if self._partition_id != partition_id:
                raise RuntimeError(
                    "partition id mismatch {} != {}".format(
                        self._partition_id, partition_id))
            next_example_index = self.example_appender.get_next_example_index()
            send_example_finished = self.example_appender.is_send_example_finished()
            return next_example_index, send_example_finished

    def reset_consumer_wrap(self, partition_id):
        with self._lock:
            if not self._is_synced_partition(partition_id):
                return
            if not self.example_appender.is_send_example_finished() or \
                    self.example_appender.need_append_into_queue():
                raise RuntimeError("partition {} is adding example to queue " \
                                   .format(partition_id))
            self._partition_id = None

    def fetch_partition_id(self):
        with self._lock:
            assert self._partition_id is not None
            return self._partition_id

    def append_data_items_from_producer(self, req):
        assert req.HasField('lite_example_ids'), \
            "req should has lite_example_ids for ExampleIdConsumer"
        with self._lock:
            self._is_synced_partition(req.lite_example_ids.partition_id)
            self.example_appender.append_batch_examples_into_queue(req.lite_example_ids)
            return True, self.example_appender.get_next_example_index()

    def _is_synced_partition(self, partition_id):
        if self._partition_id != partition_id:
            raise RuntimeError(
                "partition id:{} mismatch peer_partition_id {}".format(
                    self._partition_id, partition_id)
            )
        return True

    def finish_partition_transmit(self, partition_id):
        with self._lock:
            logging.info("example id sync follower has been notified finish send example")
            self._is_synced_partition(partition_id)
            self.example_appender.finish_send_examples()
            return not self.example_appender.need_append_into_queue()
