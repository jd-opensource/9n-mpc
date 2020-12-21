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
import threading
import time
import os
from contextlib import contextmanager
from DataJoin.data_join.data_block_manager import DataBlockManager
from DataJoin.data_join.data_block_maker import DataBlockMaker
from DataJoin.data_join.raw_data_loader import RawDataLoader


class MetaClass(type):
    _registry = {}

    def __new__(cls, cls_name, bases, dct):
        build_class = super(MetaClass, cls).__new__(cls, cls_name, bases, dct)
        cls._registry[build_class.joiner_name()] = build_class
        return build_class

    def __init__(self, name, bases, dct):
        pass

    def build_data_joiner(cls, data_joiner_options, *args, **kwargs):
        data_joiner_name = data_joiner_options.example_joiner
        if data_joiner_name in cls._registry:
            return cls._registry[data_joiner_name](data_joiner_options, *args, **kwargs)
        logging.fatal("data joiner name :%s is unknown", data_joiner_name)
        os._exit(-1)
        return None


class DataJoiner(object, metaclass=MetaClass):
    def __init__(self, data_joiner_options, raw_data_options, data_block_dir, data_source_name,
                 raw_data_dir, partition_id, mode, queue):
        self._lock = threading.Lock()
        self._data_joiner_options = data_joiner_options
        self._raw_data_options = raw_data_options
        self._mode = mode
        self._leader_visitor = queue
        self._partition_id = partition_id
        self._data_block_dir = data_block_dir
        self._data_source_name = data_source_name
        self._follower_visitor = \
            RawDataLoader(raw_data_dir, self._raw_data_options, self._mode)
        self._data_block_manager = \
            DataBlockManager(partition_id, self._data_block_dir, self._data_source_name)

        self._data_block_maker = None
        self._follower_restart_index = 0
        self._data_joiner_finished = False
        self._data_block_produced_timestamp = time.time()

    @classmethod
    def joiner_name(cls):
        return 'BASE_DATA_JOINER'

    def acquire_data_block_meta_by_index(self, index):
        with self._lock:
            return self._data_joiner_finished, \
                   self._data_block_manager.acquire_data_block_meta_by_index(index)

    def acquire_produced_data_block_number(self):
        return self._data_block_manager.acquire_produced_data_block_number()

    def is_data_joiner_finished(self):
        with self._lock:
            return self._data_joiner_finished

    def data_join_switch(self):
        with self._lock:
            if self._follower_visitor.raw_data_stale:
                return True
            else:
                return False

    def _data_joiner_algo(self):
        raise NotImplementedError(
            "_data_joiner_algo not execute for base data joiner class: {0}".format(
                DataJoiner.joiner_name())
        )

    def _acquire_data_block_maker(self, is_create):
        if self._data_block_maker is None and is_create:
            data_block_index = \
                self._data_block_manager.acquire_produced_data_block_number()
            self._data_block_maker = DataBlockMaker(
                self._data_block_dir,
                self._data_source_name,
                self._partition_id,
                data_block_index,
                self._data_joiner_options.dump_data_block_threshold
            )
            self._data_block_maker.build_data_block_manager(
                self._data_block_manager
            )
            self._data_block_maker.set_restart_data_join_index(
                self._follower_restart_index
            )
        return self._data_block_maker

    @contextmanager
    def data_joiner_factory(self):
        yield self._data_joiner_algo()

    def _data_join_finalizer(self, is_data_joiner_finished):
        if self._data_block_maker is not None:
            data_block_meta = self._data_block_maker.data_block_finalizer()
            if is_data_joiner_finished:
                self._set_data_joiner_finished()
            self._reset_data_block_maker()
            self._update_data_block_produced_timestamp()
            return data_block_meta
        return None

    def _set_data_joiner_finished(self):
        with self._lock:
            self._data_joiner_finished = True

    def _data_block_finalizer_if_time_span(self):
        time_span = self._data_joiner_options.dump_data_block_time_span
        exec_time = time.time() - self._data_block_produced_timestamp
        return 0 < time_span <= exec_time

    def _reset_data_block_maker(self):
        maker = None
        with self._lock:
            maker = self._data_block_maker
            self._data_block_maker = None
        if maker is not None:
            del maker

    def _update_data_block_produced_timestamp(self):
        with self._lock:
            self._data_block_produced_timestamp = time.time()
