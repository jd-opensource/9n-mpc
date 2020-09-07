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
from DataJoin.db.redis_manager import RedisManage

if __name__ == "__main__":
    logging.getLogger().setLevel(logging.DEBUG)
    redis_cli = RedisManage()
    remote_ip = None
    host_ip = None
    while not remote_ip:
        remote_ip = os.environ.get("REMOTE_IP", None)
    get_status, result = redis_cli.get(remote_ip)
    if get_status:
        redis_cli.delete(remote_ip)
    while not host_ip:
        host_ip = os.environ.get("HOST_IP", None)
    port = os.environ.get("PORT0", None)
    redis_cli.set(remote_ip, "{0}:{1}".format(os.environ.get("HOST_IP", None), port))
