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
from concurrent import futures
import tensorflow
import grpc
from DataJoin.common import common_pb2 as data_join_common_pb
from DataJoin.common import data_join_service_pb2_grpc as data_join_service_grpc
from DataJoin.common import data_join_service_pb2 as data_join_pb
from DataJoin.proxy.data_join_channel import create_data_join_channel
from DataJoin.config import ModeType
from DataJoin.data_join.data_join_manager import DataJoin


class DataJoinService(object):
    def __init__(self, peer_address, port, rank_id, options_args, data_source):
        self._data_source_name = data_source.data_source_name
        self._port = port
        self._worker_server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
        peer_channel = create_data_join_channel(peer_address, ModeType.REMOTE)
        peer_client = data_join_service_grpc.DataJoinServiceStub(peer_channel)
        self._data_join_worker = DataJoin(
            peer_client, rank_id, options_args, data_source
        )
        data_join_service_grpc.add_DataJoinServiceServicer_to_server(
            self._data_join_worker, self._worker_server
        )
        self._role = "leader" if data_source.role == data_join_common_pb.FLRole.Leader else "follower"
        self._worker_server.add_insecure_port('[::]:%d' % port)
        self._data_join_server_started = False

    def run(self):
        self.start_data_join_service()
        self._worker_server.wait_for_termination()
        self.stop_data_join_service()

    def start_data_join_service(self):
        if not self._data_join_server_started:
            self._worker_server.start()
            self._data_join_worker.start_data_join_processor()
            self._data_join_server_started = True
            logging.info("Data Join :{0} of data_source :{1} listen on port:{2} ".
                         format(self._role, self._data_source_name, self._port))

    def stop_data_join_service(self):
        if self._data_join_server_started:
            self._data_join_worker.stop()
            self._worker_server.stop(None)
            self._data_join_server_started = False
            logging.info("Data Join Worker:{0} of data_source :{1} stopped".
                         format(self._role, self._data_source_name))


class RunDataJoinService(object):
    @staticmethod
    def run_task():
        import argparse
        parser = argparse.ArgumentParser(description='Start DataJoinService ....')
        parser.add_argument('peer_address', type=str,
                            help='uuid or address of peer data join service')
        parser.add_argument('rank_id', type=int,
                            help='the unique id of data join processor')
        parser.add_argument('partition_id', type=int,
                            help='namespace of raw data dir ')
        parser.add_argument('data_source_name', type=str,
                            help='the data source name of data join task')
        parser.add_argument('data_block_dir', type=str,
                            help='data block dir of data join ')
        parser.add_argument('raw_data_dir', type=str,
                            help='the raw data dir of data join')
        parser.add_argument('role', type=str,
                            help='the role of data join')
        parser.add_argument('--mode', '-m', type=str, default='local',
                            help='local or distribute for data join')

        parser.add_argument('--port', '-p', type=int, default=8001,
                            help=' service port of data join  service')

        parser.add_argument('--raw_data_iter', type=str, default='TF_DATASET',
                            help='the type of raw data iter')
        parser.add_argument('--compressed_type', type=str, default='',
                            choices=['', 'ZLIB', 'GZIP'],
                            help='the compressed type of raw data')
        parser.add_argument('--example_joiner', type=str,
                            default='MEMORY_JOINER',
                            help='the join method of data joiner')

        parser.add_argument('--dump_data_block_time_span', type=int, default=-1,
                            help='the time span between dump data block and last dump data block')
        parser.add_argument('--dump_data_block_threshold', type=int, default=4096,
                            help='the max count of example items dumped in data block')
        parser.add_argument('--tf_eager_mode', action='store_true',
                            help='use the eager_mode for tf')
        args = parser.parse_args()
        if args.tf_eager_mode:
            tensorflow.compat.v1.enable_eager_execution()
        raw_data_options = data_join_pb.RawDataOptions()
        example_joiner_options = data_join_pb.ExampleJoinerOptions()
        raw_data_options.raw_data_iter = args.raw_data_iter
        raw_data_options.compressed_type = args.compressed_type
        example_joiner_options.example_joiner = args.example_joiner
        example_joiner_options.dump_data_block_time_span = args.dump_data_block_time_span
        example_joiner_options.dump_data_block_threshold = args.dump_data_block_threshold
        options_args = data_join_pb.DataJoinOptions(
            raw_data_options=raw_data_options,
            example_joiner_options=example_joiner_options,
        )
        data_source = data_join_common_pb.DataSource()
        data_source.data_source_name = args.data_source_name
        data_source.data_block_dir = args.data_block_dir
        data_source.raw_data_dir = args.raw_data_dir
        data_source.partition_id = args.partition_id
        data_source.mode = args.mode
        if args.role == 'leader':
            data_source.role = data_join_common_pb.FLRole.Leader
        else:
            assert args.role == 'follower'
            data_source.role = data_join_common_pb.FLRole.Follower
        worker_service = DataJoinService(args.peer_address, args.port,
                                         args.rank_id,
                                         options_args, data_source)
        worker_service.run()


if __name__ == "__main__":
    logging.getLogger().setLevel(logging.INFO)
    import logging

    logging.getLogger().setLevel(logging.INFO)
    RunDataJoinService.run_task()
