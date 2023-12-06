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

import socket
from .log import logger
import redis


class Register(object):

    def __init__(self, redis_address: str, redis_password: str, role_port: int, app_id: str):
        (host, port) = redis_address.split(':')
        self._redis_cli = redis.Redis(
            host=host, port=int(port),
            password=redis_password if len(redis_password) > 0 else None,
            retry_on_timeout=True)
        self._role_port = role_port
        self.app_id = app_id

        self.register_uuid()

    def __del__(self):
        self.unregister_uuid()

    def register_uuid(self):
        uuid = "network:" + self.app_id
        host_ip = self.get_host_ip()
        address = "{0}:{1}".format(host_ip, self._role_port)
        self._redis_cli.set(uuid, address)
        logger.info("Register key %s, host_address %s ", uuid, address)

    def unregister_uuid(self):
        uuid = "network:" + self.app_id
        self._redis_cli.delete(uuid)
        logger.info("UnRegister key %s", uuid)

    def get_host_ip(self):
        """
        查询本机ip地址
        :return: ip
        """
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.connect(('8.8.8.8', 80))
            ip = s.getsockname()[0]
        finally:
            s.close()
        return ip
