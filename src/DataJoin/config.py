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

from enum import Enum

api_version = "v1"
SLEEP_TIME = 60 * 60 * 24
HEADERS = {
    'Content-Type': 'application/json',
}

Data_Block_Suffix = '.data'
Data_Block_Meta_Suffix = '.meta'
Invalid_ExampleId = ''
Invalid_EventTime = -8223372020784275321

HTTP_SERVICE_HOST = '0.0.0.0'
HTTP_SERVICE_PORT = 6380

DATABASE = {
    'name': '',
    'user': '',
    'passwd': '',
    'host': '',
    'port': 3306,
    'max_connections': 100,
    'stale_timeout': 30,
}
REDIS = {
    'host': "",
    'port': 6379,
    'password': "",
    'max_connections': 500
}
db_index = 0

PROXY_SERVICE_HOST = "localhost"
PROXY_SERVICE_PORT = 3700
DATA_CENTER_PORT = 50052
sync_example_id_nums = 2048
removed_items_nums_from_buffer = 1024
data_block_index_threshold = 1 << 53


class ModeType(Enum):
    UNKNOWN = 0
    REMOTE = 1
