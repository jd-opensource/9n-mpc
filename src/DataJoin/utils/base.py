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

import time
import socket
import logging


def get_host_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(('8.8.8.8', 80))
        ip = s.getsockname()[0]
    finally:
        s.close()

    return ip


def current_timestamp():
    return int(time.time() * 1000)


def address_valid_checker(address):
    try:
        (ip, port_str) = address.split(':')
        if ip == 'localhost' or (socket.inet_aton(ip) and ip.count('.') == 3):
            port = int(port_str)
            if 0 <= port <= 65535:
                return True
        return False
    except Exception as e:
        logging.info('{0} is not valid address. error: is {1}.'.format(address, str(e)))
    return False
