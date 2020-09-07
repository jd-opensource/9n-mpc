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
from DataJoin.common import data_center_service_pb2
from DataJoin.common import data_center_service_pb2_grpc
from concurrent import futures
import time
import logging
import traceback
import sys
from DataJoin.config import api_version, DATA_CENTER_PORT
import queue
from tensorflow.compat.v1 import gfile
from os import path
from DataJoin.utils.base import get_host_ip


class DataBlockQueryService(data_center_service_pb2_grpc.DataBlockQueryServiceServicer):
    def __init__(self, data_num_epoch, leader_data_block_dir, follower_data_block_dir):
        self.counter = 0
        self.data_num_epoch = data_num_epoch
        self.leader_data_block_dir = leader_data_block_dir
        self.follower_data_block_dir = follower_data_block_dir
        self._data_center_queue = queue.Queue()
        self._block_id_map = dict()
        self.leader_file_path_list = list()
        self.follower_file_path_list = list()

    def parse_data_block_dir(self, data_block_dir, role="leader"):
        dir_path_list = [path.join(data_block_dir, f)
                         for f in gfile.ListDirectory(data_block_dir)
                         if gfile.IsDirectory(path.join(data_block_dir, f))]
        for dir_path in dir_path_list:
            if role == "leader":
                self.leader_file_path_list += [path.join(dir_path, f)
                                               for f in gfile.ListDirectory(dir_path)
                                               if f.split(".")[-1] == "data" and
                                               not gfile.IsDirectory(path.join(dir_path, f))]
            else:
                self.follower_file_path_list += [path.join(dir_path, f)
                                                 for f in gfile.ListDirectory(dir_path)
                                                 if f.split(".")[-1] == "data" and
                                                 not gfile.IsDirectory(path.join(dir_path, f))]

        self.leader_file_path_list.sort()
        self.follower_file_path_list.sort()

    def encode_leader_data_block_info(self, data_block_dir):
        self.counter += 1
        self.parse_data_block_dir(data_block_dir)
        for i in range(int(self.data_num_epoch)):
            for file_path in self.leader_file_path_list:
                block_id = (file_path.split('/')[-1]).replace(".data", "")
                self._data_center_queue.put((block_id, file_path))
                logging.info("block_id:{}, data_block_path: {}".format(block_id, file_path))

    def encode_follower_data_block_info(self, data_block_dir):
        self.parse_data_block_dir(data_block_dir, role="follower")
        for file_path in self.follower_file_path_list:
            block_id = (file_path.split('/')[-1]).replace(".data", "")
            self._block_id_map[block_id] = file_path

    def QueryDataBlock(self, request, context):
        logging.info('server received :%s from client QueryDataBlock ' % request)
        block_id = request.block_id
        try:

            if not block_id:
                if not self.counter:
                    self.encode_leader_data_block_info(self.leader_data_block_dir)
                if not self._data_center_queue.empty():
                    data_block_queue = self._data_center_queue.get()
                    data_block_info = data_center_service_pb2.DataBlockInfo(
                        block_id=data_block_queue[0],
                        dfs_data_block_dir=data_block_queue[1])
                    data_response = data_center_service_pb2.DataBlockResponse(
                        data_block_status=data_center_service_pb2.DataBlockStatus.Value("OK"),
                        error_message="trainer request server query block success",
                        data_block_info=data_block_info)
                    return data_response
                else:
                    return data_center_service_pb2.DataBlockResponse(
                        data_block_status=data_center_service_pb2.DataBlockStatus.Value("FINISHED"),
                        error_message="trainer request server query block finished")

            else:
                if not self._block_id_map:
                    self.encode_follower_data_block_info(self.follower_data_block_dir)
                if self._block_id_map.get(block_id, None):
                    data_block_info = data_center_service_pb2.DataBlockInfo(
                        block_id=block_id,
                        dfs_data_block_dir=self._block_id_map[block_id])
                    data_response = data_center_service_pb2.DataBlockResponse(
                        data_block_status=data_center_service_pb2.DataBlockStatus.Value("OK"),
                        error_message="trainer request server query block success",
                        data_block_info=data_block_info)
                else:
                    data_response = data_center_service_pb2.DataBlockResponse(
                        data_block_status=data_center_service_pb2.DataBlockStatus.Value("NOT_FOUND"),
                        error_message="Not Found Data Block")
                return data_response
        except Exception as e:
            logging.info('query data block meta exec :%s' % str(e))
            traceback.print_exc(file=sys.stdout)
            return data_center_service_pb2.DataBlockResponse(
                data_block_status=data_center_service_pb2.DataBlockStatus.Value("ABORTED"),
                error_message="query data block aborted")


class StartDataCenterServer(object):
    @staticmethod
    def run_server():
        import argparse
        parser = argparse.ArgumentParser()
        parser.add_argument('--data_num_epoch', '-d', type=int, default=1,
                            help='data num epoch for local data center service')
        parser.add_argument('leader_data_block_dir', type=str, default="",
                            help='leader data block dir of local data center service')
        parser.add_argument('follower_data_block_dir', type=str, default="",
                            help='follower data block dir of local data center service')
        parser.add_argument('data_center_port', type=int,
                            help='data center server port ')
        args = parser.parse_args()
        data_num_epoch = args.data_num_epoch
        leader_data_block_dir = args.leader_data_block_dir
        follower_data_block_dir = args.follower_data_block_dir
        data_center_host = get_host_ip()
        data_center_port = args.data_center_port
        server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
        data_center_service_pb2_grpc.add_DataBlockQueryServiceServicer_to_server(
            DataBlockQueryService(data_num_epoch, leader_data_block_dir, follower_data_block_dir), server)
        server.add_insecure_port('{}:{}'.format(data_center_host, data_center_port))
        server.start()
        logging.info("start data center server successfully host:{},port:{}".format(data_center_host, data_center_port))
        try:
            while True:
                time.sleep(60 * 60 * 24)
        except KeyboardInterrupt:
            server.stop(0)


if __name__ == '__main__':
    StartDataCenterServer.run_server()
