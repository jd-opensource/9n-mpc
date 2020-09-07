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
import zlib
from DataJoin.common import common_pb2 as data_join_common_pb
from DataJoin.common import data_join_service_pb2_grpc as data_join_service_grpc
from DataJoin.common import data_join_service_pb2 as data_join_pb
from DataJoin.data_join import example_id_producer, example_id_consumer, \
    data_block_producer, data_block_consumer
import multiprocessing
from functools import wraps
from DataJoin.data_join.raw_data_loader import InitRawDataLoading


def rank_id_wrap(f):
    @wraps(f)
    def check_rank_id(self, *args, **kwargs):
        assert isinstance(self, DataJoin), \
            "function type should be DataJoin, not be:{} ".format(type(self))
        assert self._rank_id == args[0].rank_id, \
            "rank_id :{} is not same with peer_rank_id: {} ".format(self._rank_id, args[0].rank_id)
        return f(self, *args, **kwargs)

    return check_rank_id


def partition_id_wrap(f):
    @wraps(f)
    def check_partition_id(self, *args, **kwargs):
        assert isinstance(self, DataJoin), \
            "function type should be DataJoin, not be:{}".format(type(self))
        partition_id = args[0].partition_id
        assert partition_id >= 0, \
            "partition id {} should not be negative".format(partition_id)
        assert self._partition_id == partition_id, \
            "partition_id :{} is not same with peer_partition_id: {} ".format(self._partition_id, partition_id)
        return f(self, *args, **kwargs)

    return check_partition_id


class DataJoin(data_join_service_grpc.DataJoinServiceServicer):
    def __init__(self, peer_client, rank_id, options_args, data_source):
        super(DataJoin, self).__init__()
        self._peer_client = peer_client
        self._data_source = data_source
        self._role = self._data_source.role
        self._partition_id = self._data_source.partition_id
        self._raw_data_dir = self._data_source.raw_data_dir
        self._data_block_dir = self._data_source.data_block_dir
        self._data_source_name = self._data_source.data_source_name
        self._consumer_process = None
        self._rank_id = rank_id
        self._mode = self._data_source.mode
        self._producer_process = None
        self._init_raw_data_loading = InitRawDataLoading(self._raw_data_dir, options_args.raw_data_options,
                                                         self._partition_id, self._mode)
        self._init_data_join_processor(options_args)

    def start_data_join_processor(self):
        self._producer_process.start_processors()
        if self._role == data_join_common_pb.FLRole.Leader:
            self._consumer_process.start_processors()

    def stop_data_join_processor(self):
        self._producer_process.stop_processors()
        if self._role == data_join_common_pb.FLRole.Leader:
            self._consumer_process.stop_processors()

    def _init_data_join_processor(self, options_args):
        if self._role == data_join_common_pb.FLRole.Leader:
            self._producer_process = example_id_producer.ExampleIdProducer(
                self._peer_client, self._raw_data_dir, self._partition_id,
                self._rank_id, options_args.raw_data_options, self._mode, self._init_raw_data_loading
            )
            self._consumer_process = data_block_consumer.DataBlockConsumer(
                self._partition_id, options_args.raw_data_options,
                self._init_raw_data_loading, self._data_block_dir,
                self._data_source_name
            )
        else:
            assert self._role == data_join_common_pb.FLRole.Follower, \
                "if role not leader, should be Follower"
            follower_data_queue = multiprocessing.Queue(-1)
            self._producer_process = data_block_producer.DataBlockProducer(
                self._peer_client, self._rank_id, self._raw_data_dir,
                options_args.raw_data_options, options_args.example_joiner_options
                , self._partition_id, follower_data_queue, self._mode,
                self._data_block_dir, self._data_source_name
            )
            self._consumer_process = example_id_consumer.ExampleIdConsumer(
                self._partition_id, follower_data_queue
            )

    @rank_id_wrap
    @partition_id_wrap
    def StartPartition(self, request, context):
        logging.info("Start Partition Req:{0}".format(request.partition_id))
        response = data_join_pb.StartPartitionResponse()
        peer_partition_id = request.partition_id
        partition_id = \
            self._consumer_process.fetch_partition_id()
        if peer_partition_id != partition_id:
            response.status.code = -2
            response.status.error_message = \
                "partition_id :{0} is not same with peer partition_id:{1}".format(partition_id, peer_partition_id)
        if response.status.code == 0:
            response.next_index, response.finished = \
                self._consumer_process.partition_syncer_to_consumer(peer_partition_id)
        return response

    @rank_id_wrap
    @partition_id_wrap
    def FinishPartition(self, request, context):
        logging.info("Finish Partition Req:{0}".format(request.partition_id))
        response = data_join_pb.FinishPartitionResponse()
        peer_partition_id = request.partition_id
        partition_id = self._consumer_process.fetch_partition_id()
        assert partition_id == peer_partition_id, \
            "partition_id :{0} is not same with peer partition_id:{1}".format(partition_id, peer_partition_id)
        response.finished = \
            self._consumer_process.finish_partition_transmit(
                peer_partition_id
            )
        if response.finished:
            self._consumer_process.reset_consumer_wrap(peer_partition_id)
        return response

    @rank_id_wrap
    @partition_id_wrap
    def SyncPartition(self, request, context):
        logging.info("Sync Partition Req:{0}".format(request.partition_id))
        response = data_join_common_pb.Status()
        content_bytes = request.content_bytes
        if request.compressed:
            content_bytes = zlib.decompress(content_bytes)
        send_example_items = data_join_pb.SyncContent()
        send_example_items.ParseFromString(content_bytes)
        status, next_index = self._consumer_process.append_data_items_from_producer(send_example_items)
        if not status:
            response.code = -1
            response.error_message = "example id is enough"
        return response
