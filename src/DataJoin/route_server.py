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


import os
import signal
import sys
import time
from concurrent import futures
import traceback
import logging
import grpc
from grpc._cython import cygrpc
from werkzeug.wsgi import DispatcherMiddleware
from flask import Flask
from werkzeug.serving import run_simple
from DataJoin.routine.data_routine import manager as data_app_manager
from DataJoin.routine.parse_data_block_meta_routine import manager as parse_data_block_meta_app_manager
from DataJoin.db.db_models import init_db
from DataJoin.utils.api import response_api
from DataJoin.common import common_pb2_grpc
from DataJoin.config import SLEEP_TIME, api_version, HTTP_SERVICE_HOST, HTTP_SERVICE_PORT, \
    PROXY_SERVICE_HOST, PROXY_SERVICE_PORT
from DataJoin.utils.data_transfer import ProxyDataService
from DataJoin.utils.base import get_host_ip

logging.getLogger().setLevel(logging.INFO)

manager = Flask(__name__)


@manager.errorhandler(500)
def internal_server_error(e):
    logging.error(str(e))
    return response_api(retcode=100, retmsg=str(e))


if __name__ == '__main__':

    manager.url_map.strict_slashes = False
    routine = DispatcherMiddleware(
        manager,
        {
            '/{}/data'.format(api_version): data_app_manager,
            '/{}/parse'.format(api_version): parse_data_block_meta_app_manager,
        }
    )
    http_server_ip = get_host_ip()
    logging.info('http_server_ip is :%s' % http_server_ip)
    mode = os.environ.get("MODE", "local")
    if mode == "distribute":
        init_db()
    grpc_server = grpc.server(futures.ThreadPoolExecutor(max_workers=10),
                              options=[(cygrpc.ChannelArgKey.max_send_message_length, -1),
                                       (cygrpc.ChannelArgKey.max_receive_message_length, -1)])

    common_pb2_grpc.add_ProxyDataServiceServicer_to_server(ProxyDataService(), grpc_server)
    grpc_server.add_insecure_port("{}:{}".format(PROXY_SERVICE_HOST, PROXY_SERVICE_PORT))
    grpc_server.start()
    try:
        run_simple(hostname=HTTP_SERVICE_HOST, port=HTTP_SERVICE_PORT,
                   application=routine, threaded=True)
    except Exception as e:
        traceback.print_exc()
        os.kill(os.getpid(), signal.SIGKILL)
    try:
        while True:
            time.sleep(SLEEP_TIME)
    except KeyboardInterrupt:
        grpc_server.stop(0)
        sys.exit(0)
