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

import grpc
import unittest

from DataJoin.common import data_join_service_pb2_grpc as data_join_service_grpc
from DataJoin.common import data_join_service_pb2 as data_join_pb
import logging


class TestDataCenter(unittest.TestCase):
    def setUp(self):
        self.channel = grpc.insecure_channel('')
        self.client = data_join_service_grpc.DataJoinServiceStub(self.channel)

    def test_start_partition(self):
        logging.info('Client StartPartition Start To Request Service')
        example_producer_request = data_join_pb.StartPartitionRequest(
            rank_id=0,
            partition_id=0
        )
        response = self.client.StartPartition(example_producer_request)
        logging.info("client received next_index: %s" % response.next_index)
        logging.info("client received status: %s" % response.finished)


if __name__ == '__main__':
    unittest.main()
