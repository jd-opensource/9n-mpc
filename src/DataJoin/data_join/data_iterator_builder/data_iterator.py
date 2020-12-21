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


class MetaClass(type):
    _registry = {}

    def __new__(cls, cls_name, bases, dct):
        build_class = super(MetaClass, cls).__new__(cls, cls_name, bases, dct)
        cls._registry[build_class.iterator_name()] = build_class
        return build_class

    def __init__(self, name, bases, dct):
        pass

    def build(cls, options):
        raw_data_iter_name = options.raw_data_iter
        if raw_data_iter_name in cls._registry:
            return cls._registry[raw_data_iter_name](options)
        logging.fatal("raw data iter name :%s is unknown", raw_data_iter_name)
        os._exit(-1)
        return None


class DataIterator(object, metaclass=MetaClass):
    class DataItemParser(object):
        @property
        def record(self):
            raise NotImplementedError(
                "base data item parser:DataItemParser record not implement for record"
            )

        @property
        def event_time(self):
            raise NotImplementedError(
                "base data item parser:DataItemParser record not implement for event_time"
            )

        @property
        def example_id(self):
            raise NotImplementedError(
                "base data item parser:DataItemParser record not implement for example_id"
            )

    def __init__(self, options):
        self._data_iterator = None
        self._data_path = None
        self._data_item = None
        self._options = options

    def reset_data_iterator(self, data_path=None):
        self._data_item = None
        self._data_iterator = None
        self._data_path = None
        self._data_iterator, self._data_item = self._reset_data_iterator(data_path)
        self._data_path = data_path
        return self._data_item

    @classmethod
    def iterator_name(cls):
        return 'BASE_DATA_ITERATOR'

    def __iter__(self):
        return self

    def __next__(self):
        try:
            self._data_item = self._visit_next_item()
        except StopIteration:
            logging.info("file path %s stop iterator", self._data_path)
            raise
        return self._data_item

    def _visit_next_item(self):
        raise NotImplementedError(
            "base class {0} not implement for visiting next item ".format(
                DataIterator.iterator_name())
        )

    def _reset_data_iterator(self):
        raise NotImplementedError(
            "base class {0} not implement for resetting data iterator ".format(
                DataIterator.iterator_name())
        )

