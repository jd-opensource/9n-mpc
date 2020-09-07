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
from contextlib import contextmanager
import time
import traceback
import tensorflow.compat.v1 as tf
import threading
from DataJoin.config import Data_Block_Suffix, Data_Block_Meta_Suffix


@contextmanager
def tf_record_iterator_factory(data_path):
    tf_iterator = None
    try:
        tf_iterator = tf.io.tf_record_iterator(data_path)
        yield tf_iterator
    except Exception as e:
        logging.error("build tf_record_iterator Failed file_path:{0}".format(data_path))
        traceback.print_exc(str(e))
    if tf_iterator is not None:
        del tf_iterator


class ProcessorManager(object):
    def __init__(self, impl_processor_name, impl_processor,
                 impl_condition, impl_time_span):
        self._impl_processor_name = impl_processor_name
        self._lock = threading.Lock()
        self._impl_time_span = impl_time_span
        self._impl_processor = impl_processor
        assert self._impl_time_span > 0, "impl time span is invalid:{0}".format(impl_time_span)
        self._condition = threading.Condition(self._lock)
        self._impl_condition = impl_condition
        self._para_tuple = tuple()
        self._pass_impl_processor = False
        self._inactive_status = False
        self._para_dict = dict()
        self._threading = None

    def enable_processor(self):
        with self._condition:
            self._condition.notify()
            self._pass_impl_processor = False

    def active_processor(self):
        with self._lock:
            assert self._threading is None, 'processor {0} is in active'.format(self._impl_processor_name)
            assert not self._inactive_status, 'processor {0} is in inactive'.format(self._impl_processor_name)
            self._threading = threading.Thread(target=self.implementor,
                                               name=self._impl_processor_name)
            self._threading.start()

    def is_inactive(self):
        with self._lock:
            return self._inactive_status

    def build_impl_processor_parameter(self, *args, **kwargs):
        with self._lock:
            self._para_tuple = args
            self._para_dict = kwargs

    def inactive_processor(self):
        thread = None
        with self._lock:
            if self._threading is not None and not self._inactive_status:
                self._inactive_status = True
                self._condition.notify()
                thread = self._threading
                self._threading = None
        if thread is not None:
            thread.join()

    def entry_impl_processor(self):
        with self._lock:
            if self._pass_impl_processor:
                return True
        return not self._impl_condition()

    def implementor(self):
        impl_count = 0
        while not self.is_inactive():
            impl_time_point = time.time()
            while self.entry_impl_processor():
                with self._lock:
                    if self._inactive_status:
                        return
                    if self._impl_time_span is None:
                        self._condition.wait()
                    else:
                        wait_time_span = (self._impl_time_span -
                                          (time.time() - impl_time_point))
                        if wait_time_span > 0:
                            self._condition.wait(wait_time_span)
                        else:
                            self._pass_impl_processor = False
                            impl_time_point = time.time()
            try:
                with self._lock:
                    self._pass_impl_processor = self._impl_time_span is not None
                parameter = self.acquire_impl_processor_parameter()
                self._impl_processor(*(parameter[0]), **(parameter[1]))
            except Exception as e:
                logging.error("processor: %s implement %d rounds with exception:%s",
                              self._impl_processor_name, impl_count, e)
            else:
                logging.info("processor: %s implement %d round", self._impl_processor_name, impl_count)
            impl_count += 1

    def acquire_impl_processor_parameter(self):
        with self._lock:
            parameter = (self._para_tuple, self._para_dict)
            self._para_tuple = tuple()
            self._para_dict = dict()
            return parameter


def partition_id_wrap(partition_id):
    return 'partition_{:04}'.format(partition_id)


def data_block_meta_file_name_wrap(data_source_name,
                                   partition_id,
                                   data_block_index):
    return '{}.{}.{:08}{}'.format(
        data_source_name, partition_id_wrap(partition_id),
        data_block_index, Data_Block_Meta_Suffix
    )


def block_id_wrap(data_source_name, meta):
    return '{}.{}.{:08}.{}-{}'.format(
        data_source_name, partition_id_wrap(meta.partition_id),
        meta.data_block_index, meta.start_time, meta.end_time
    )


def data_block_file_name_wrap(data_source_name, meta):
    block_id = block_id_wrap(data_source_name, meta)
    return '{}{}'.format(block_id, Data_Block_Suffix)
