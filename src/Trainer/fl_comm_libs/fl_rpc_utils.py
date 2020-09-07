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
import os
import sys
import time
import logging

sys.path.insert(0, '../')

import grpc
from proto import co_proxy_pb2 as pxy_pb
from proto import co_proxy_pb2_grpc as pxy_grpc

_LOGGER = logging.getLogger(__name__)

def rpc_channel_uuid_register(stub, appli_uuid, ip_port):
    """
    register_uuid
    """
    _LOGGER.info('Register appli_uuid: %s, ip_port: %s', ','.join(appli_uuid), ip_port)
    request = pxy_pb.Request()
    for id in appli_uuid:
        request.uuid.append(id)
    request.ip_port = ip_port
    try:
        response = stub.RegisterUUID(request)
        if response.status == 0:
            return True
        else:
            _LOGGER.error('Register failed: %s', response.err_msg)
            return False
    except grpc.RpcError as rpc_error:
        _LOGGER.error('Register Call failure: %s', rpc_error)
        return False


def rpc_channel_uuid_getpairs(stub, appli_uuid, ip_port):
    """
    get pair_info
    """
    _LOGGER.info('Getpairs appli_uuid: %s, ip_port: %s', ','.join(appli_uuid), ip_port)
    request = pxy_pb.Request()
    for id in appli_uuid:
        request.uuid.append(id)
    request.ip_port = ip_port
    try:
        response = stub.GetPairInfo(request)
        if response.status.status == 0:
            if len(response.service_map) == 0:
                _LOGGER.error('Getpairs service_map size 0')
                return False, ''
            _LOGGER.info("uuid pairs received: %s", response.service_map)
            uuid_pairs = []
            for servpair in response.service_map:
                #_LOGGER.info('PairInfo: local %s, remote %s', servpair.local_uuid, servpair.remote_uuid)
                uuid_pairs.append((servpair.local_uuid, servpair.remote_uuid))
                break;
            return True, uuid_pairs
        else:
            _LOGGER.error('Getpairs failed: %s', response.status.err_msg)
            return False, ''
    except grpc.RpcError as rpc_error:
        _LOGGER.error('Getpairs Call failure: %s', rpc_error)
        return False, ''


def prepare_rpc_channel(coordinator_addr, appli_id, ipport):
    """
    Register and GetPairInfo
    """
    with grpc.insecure_channel(coordinator_addr) as channel:
        stub = pxy_grpc.PairServiceStub(channel)
        # Wait Register OK
        while True:
            ret = rpc_channel_uuid_register(stub, appli_id, ipport)
            if ret == True:
                print('*** Register OK ***')
                break;
            time.sleep(10)
        
        # Wait WorkPair OK
        while True:
            ret, pairs = rpc_channel_uuid_getpairs(stub, appli_id, ipport)
            if ret == True:
                print('*** WorkerPair OK ***')
                break;
            time.sleep(10)
        channel_uuid = [ruuid for luuid, ruuid in pairs]
        return channel_uuid


