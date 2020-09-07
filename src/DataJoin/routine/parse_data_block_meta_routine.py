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

from flask import Flask, request
from DataJoin.utils.api import response_api
from DataJoin.controller.parse_data_block_meta import StartParseDataBlockMeta
import sys
import subprocess
import os
import logging

manager = Flask(__name__)


@manager.errorhandler(500)
def internal_server_error(e):
    logging.error(e)
    return response_api(retcode=100, retmsg=str(e))


def run_subprocess(process_cmd):
    if os.name == 'nt':
        startupinfo = subprocess.STARTUPINFO()
        startupinfo.dwFlags |= subprocess.STARTF_USESHOWWINDOW
        startupinfo.wShowWindow = subprocess.SW_HIDE
    else:
        startupinfo = None
    p = subprocess.Popen(process_cmd,
                         startupinfo=startupinfo
                         )
    return p


@manager.route('/data/block/meta', methods=['POST'])
def parse_data_block_meta():
    data_block_meta_hdfs_dir = request.json
    dfs_data_block_dir = data_block_meta_hdfs_dir.get('dfs_data_block_dir', '')
    dfs_data_block_meta = data_block_meta_hdfs_dir.get('dfs_data_block_meta', '')
    dfs_data_block = data_block_meta_hdfs_dir.get('dfs_data_block', '')
    if not dfs_data_block_dir and not dfs_data_block_meta and not dfs_data_block:
        return response_api(retcode=500, retmsg='args is null')
    parse_data_block_meta_pid = run_subprocess(
        [
            'python', sys.modules[StartParseDataBlockMeta.__module__].__file__,
            '-d', dfs_data_block_dir,
            '-mt', dfs_data_block_meta,
            '-db', dfs_data_block
        ])
    return response_api(retcode=0, retmsg='success')
