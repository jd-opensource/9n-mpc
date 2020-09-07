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
import json
from DataJoin.utils.api import wrap_data_transfer_api
from DataJoin.data_center.counter import count
from DataJoin.data_center.data_block_manager import DataBlockMetaManage, ReidsHandle
import traceback
import sys
from DataJoin.config import api_version, DATA_CENTER_PORT
from DataJoin.utils.base import get_host_ip


class DataBlockMeta(object):
    def __init__(self, **kwargs):
        super(DataBlockMeta, self).__init__()
        self.block_id = kwargs.get("block_id", "")
        self.partition_id = kwargs.get("partition_id", 0)
        self.file_version = kwargs.get("file_version", 0)
        self.start_time = kwargs.get("start_time", 0)
        self.end_time = kwargs.get("end_time", 0)
        self.example_ids = [v.encode("utf-8") for v in json.loads(kwargs.get("example_ids", []))] if kwargs.get(
            "example_ids", []) else []
        self.leader_start_index = kwargs.get("leader_start_index", 0)
        self.leader_end_index = kwargs.get("leader_end_index", 0)
        self.follower_restart_index = kwargs.get("follower_restart_index", 0)
        self.data_block_index = kwargs.get("data_block_index", 0)

    def create_data_block_meta(self):
        data_block_meta = data_center_service_pb2.DataBlockMeta()
        data_block_meta.block_id = self.block_id
        data_block_meta.partition_id = self.partition_id
        data_block_meta.file_version = self.file_version
        data_block_meta.start_time = self.start_time
        data_block_meta.end_time = self.end_time
        data_block_meta.leader_start_index = self.leader_start_index
        data_block_meta.leader_end_index = self.leader_end_index
        data_block_meta.follower_restart_index = self.follower_restart_index
        data_block_meta.data_block_index = self.data_block_index
        data_block_meta.example_ids.extend(self.example_ids)
        return data_block_meta


class DataBlockQueryService(data_center_service_pb2_grpc.DataBlockQueryServiceServicer):
    def __init__(self, train_data_start, train_data_end, data_source_name, data_num_epoch):
        self.train_data_start = train_data_start
        self.train_data_end = train_data_end
        self.data_source_name = data_source_name
        self.data_num_epoch = data_num_epoch

    def QueryDataBlock(self, request, context):
        logging.info('server received :%s from client QueryDataBlock ' % request)
        data_block_dict = None
        block_id = request.block_id
        endpoint = "/{0}/data/query/data/block/meta".format(api_version)
        try:

            if not block_id:
                json_body = {}
                if self.train_data_start and self.train_data_end:
                    json_body["start_time"] = self.train_data_start
                    json_body["end_time"] = self.train_data_end
                if self.data_source_name:
                    json_body["data_source_name"] = self.data_source_name
                num = count()
                logging.info('execute query data block meta current num :%s' % num)
                logging.info('data num epoch :%s' % self.data_num_epoch)
                redis_handle = ReidsHandle()
                if num == 1:
                    logging.info('server received json_body :%s from client QueryDataBlock ' % json_body)
                    data_block_result = wrap_data_transfer_api("POST", endpoint, json_body)
                    data_block_check_null_status = DataBlockMetaManage().check_result_null(**data_block_result)
                    if data_block_check_null_status:
                        return data_block_check_null_status
                    data_block_result = data_block_result.get("data", [])
                    data_block_dict = data_block_result[0]
                    data_length = len(data_block_result)
                    total_epoch_time = data_length * int(self.data_num_epoch)
                    data_block_redis = dict(data_block_result=data_block_result, total_epoch_time=total_epoch_time)

                    redis_handle.redis_set(json.dumps(data_block_redis))
                else:
                    status, data_block_redis = redis_handle.redis_get()
                    if not status:
                        data_block_meta_status = DataBlockMetaManage(2, 1).check_result_null(**{})
                        if data_block_meta_status:
                            return data_block_meta_status
                    data_block_result = (json.loads(data_block_redis)).get("data_block_result", [])
                    total_epoch_time = (json.loads(data_block_redis)).get("total_epoch_time", 0)
                    data_length = total_epoch_time / int(self.data_num_epoch)
                    if num < total_epoch_time:
                        if num % data_length == 0:
                            data_block_dict = data_block_result[-1]
                        else:
                            logging.info('current data block meta index :%s ' % (int(num % data_length - 1)))
                            data_block_dict = data_block_result[int(num % data_length - 1)]
                    elif num == total_epoch_time:
                        data_block_dict = data_block_result[-1]
                        redis_handle.redis_delete()
                    data_block_check_ready_status = DataBlockMetaManage(num, total_epoch_time).check_result_status(
                        **data_block_dict)
                    if data_block_check_ready_status:
                        return data_block_check_ready_status

            else:
                json_body = {"block_id": block_id}
                logging.info('server received json_body :%s from client QueryDataBlock ' % json_body)
                data_block_result = wrap_data_transfer_api("POST", endpoint, json_body)
                data_block_check_null_status = DataBlockMetaManage().check_result_null(**data_block_result)
                if data_block_check_null_status:
                    return data_block_check_null_status
                data_block_result = data_block_result.get("data", [])
                data_block_dict = data_block_result[0]
                data_block_check_ready_status = DataBlockMetaManage().check_result_status(**data_block_dict)
                if data_block_check_ready_status:
                    return data_block_check_ready_status

            data_block_obj = DataBlockMeta(**data_block_dict)
            data_block_meta = data_block_obj.create_data_block_meta()
            data_block_info = data_center_service_pb2.DataBlockInfo(block_id=data_block_dict.get("block_id", ""),
                                                                    dfs_data_block_dir=data_block_dict.get(
                                                                        "dfs_data_block_dir", ""),
                                                                    data_block_meta=data_block_meta)
            data_response = data_center_service_pb2.DataBlockResponse(
                data_block_status=data_center_service_pb2.DataBlockStatus.Value("OK"),
                error_message="trainer request server query block success",
                data_block_info=data_block_info)

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
        parser.add_argument('train_data_start', type=int,
                            help='train start time of distribute data center service')
        parser.add_argument('train_data_end', type=int,
                            help='train end time of distribute data center service')
        parser.add_argument('data_source_name', type=str,
                            help='data source name of distribute data center service')
        parser.add_argument('--data_num_epoch', '-d', type=int, default=1,
                            help='data num epoch for distribute data center service')
        args = parser.parse_args()
        train_data_start = int(
            str(args.train_data_start).replace("-", "").replace(":", "").replace(" ", ""))
        train_data_end = int(
            str(args.train_data_end).replace("-", "").replace(":", "").replace(" ", ""))
        data_source_name = args.data_source_name
        data_num_epoch = args.data_num_epoch
        data_center_host = get_host_ip()
        data_center_port = DATA_CENTER_PORT
        server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
        data_center_service_pb2_grpc.add_DataBlockQueryServiceServicer_to_server(
            DataBlockQueryService(train_data_start, train_data_end, data_source_name, data_num_epoch), server)
        server.add_insecure_port('{}:{}'.format(data_center_host, data_center_port))
        server.start()
        logging.info("start data center server successfully host:{},port:{}".format(data_center_host, data_center_port))
        try:
            while True:
                time.sleep(60 * 60 * 24)
        except KeyboardInterrupt:
            server.stop(0)


if __name__ == '__main__':
    import logging
    logging.getLogger().setLevel(logging.INFO)
    logging.getLogger().setLevel(logging.INFO)
    StartDataCenterServer.run_server()
