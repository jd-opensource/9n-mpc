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

from DataJoin.common import data_center_service_pb2
import logging
import threading
from DataJoin.db.redis_manager import RedisManage


class DataBlockMetaManage(object):
    def __init__(self, current_batch: int = 1, max_batch: int = 2):
        super(DataBlockMetaManage, self).__init__()
        self._current_batch = current_batch
        self._max_batch = max_batch
        self._data_response = None
        self._not_found_status = data_center_service_pb2.DataBlockStatus.Value("NOT_FOUND")
        self._not_ready_status = data_center_service_pb2.DataBlockStatus.Value("NOT_READY")
        self._aborted_status = data_center_service_pb2.DataBlockStatus.Value("ABORTED")
        self._finished_status = data_center_service_pb2.DataBlockStatus.Value("FINISHED")
        self._success_status = data_center_service_pb2.DataBlockStatus.Value("OK")

    def check_result_status(self, **current_data):
        logging.info("current data block meta result is :%s" % current_data)
        if current_data.get("create_status", "") == 1:
            error_message = "block_id: {0} not ready !".format(current_data.get("block_id"))
            self._data_response = data_center_service_pb2.DataBlockResponse(
                data_block_status=self._not_ready_status,
                error_message=error_message)
        elif current_data.get("create_status", "") == 3:
            error_message = "block_id: {0} is ABORTED !".format(current_data.get("block_id"))
            self._data_response = data_center_service_pb2.DataBlockResponse(data_block_status=self._aborted_status,
                                                                            error_message=error_message)
        else:
            pass
        return self._data_response

    def check_result_null(self, **kwargs):
        logging.info("query data block meta result is :%s" % kwargs.get("data", []))
        if not kwargs.get("data", []):
            if self._current_batch <= self._max_batch:
                error_message = "query data block not found !"
                self._data_response = data_center_service_pb2.DataBlockResponse(
                    data_block_status=self._not_found_status,
                    error_message=error_message)
            else:
                error_message = "query block_id finished !"
                self._data_response = data_center_service_pb2.DataBlockResponse(
                    data_block_status=self._finished_status,
                    error_message=error_message)
        return self._data_response


class ReidsHandle(object):
    def __init__(self):
        self._lock = threading.Lock()
        self._redis_cli = RedisManage()

    def redis_set(self, data_block_redis):
        global key
        key = "data.block"
        with self._lock:
            self._redis_cli.set(key, data_block_redis)

    def redis_get(self):
        global key
        key = "data.block"
        with self._lock:
            status, data_block_redis = self._redis_cli.get(key)
            return status, data_block_redis

    def redis_delete(self):
        global key
        key = "data.block"
        with self._lock:
            self._redis_cli.delete(key)
