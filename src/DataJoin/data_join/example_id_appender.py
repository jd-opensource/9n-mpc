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


class AppendExamplesManager(object):
    def __init__(self, queue, partition_id):
        self._lock = threading.Lock()
        self._queue = queue
        self._partition_id = partition_id
        self._send_example_finished = False
        self._next_example_index = 0

    def get_next_example_index(self):
        with self._lock:
            return self._next_example_index

    def append_batch_examples_into_queue(self, batch_examples):
        with self._lock:
            assert batch_examples, "batch_examples is None"
            assert batch_examples.partition_id == self._partition_id, \
                "the partition id of  example batch mismatch with " \
                "partition id of examples appending into queue : {} != {}".format(
                    self._partition_id, batch_examples.partition_id
                )
            self._next_example_index += len(batch_examples.example_id) - 1
            self._queue.put(batch_examples)
            return True

    def finish_send_examples(self):
        with self._lock:
            self._send_example_finished = True

    def is_send_example_finished(self):
        with self._lock:
            return self._send_example_finished

    def need_append_into_queue(self):
        with self._lock:
            if not self._queue.empty():
                return True
            return False



