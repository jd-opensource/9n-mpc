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

import sys
import subprocess
import os
from tensorflow.python.platform import gfile
import time
import logging
from DataJoin.controller.sync_convert_data_block import StartSyncConvertDataBlock

time_stamp = str(int(time.time()))


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


class DataBlockController(object):
    def __init__(self, dfs_data_block_dir, **kwargs):
        super(DataBlockController).__init__()
        self.dfs_data_block_dir = dfs_data_block_dir

    def data_block_controller(self):
        logging.info('Fetch Data Block Meta from Path:%s' % self.dfs_data_block_dir)
        data_meta_fpaths = \
            [os.path.join(self.dfs_data_block_dir, f)
             for f in gfile.ListDirectory(self.dfs_data_block_dir)
             if not gfile.IsDirectory(os.path.join(self.dfs_data_block_dir, f))
             and f.endswith(".meta")]
        data_meta_fpaths.sort()
        logging.info("data_meta_fpaths: %s" % data_meta_fpaths)
        data_block_fpaths = \
            [os.path.join(self.dfs_data_block_dir, f)
             for f in gfile.ListDirectory(self.dfs_data_block_dir)
             if not gfile.IsDirectory(os.path.join(self.dfs_data_block_dir, f))
             and f.endswith(".data")]
        assert len(data_meta_fpaths) == len(data_block_fpaths)
        data_block_fpaths_dict = dict()
        data_meta_fpaths_dict = dict()
        for data_met_path in data_meta_fpaths:
            index = data_met_path.split(".")[-2]
            data_meta_fpaths_dict[str(index)] = data_met_path

        for data_block_path in data_block_fpaths:
            index = data_block_path.split(".")[-3]
            data_block_fpaths_dict[str(index)] = data_block_path
        logging.info("data_block_fpaths:%s" % data_block_fpaths_dict)
        result = list()
        for i in range(len(data_block_fpaths_dict)):
            time.sleep(2)
            meta_path = data_meta_fpaths_dict["{:08}".format(i)]
            data_path = data_block_fpaths_dict["{:08}".format(i)]
            logging.info("meta path is: %s" % meta_path)
            logging.info("data path is :%s" % data_path)

            run_subprocess(
                [
                    'python', sys.modules[StartSyncConvertDataBlock.__module__].__file__,
                    '-d', time_stamp,
                    '-m', meta_path,
                    '-p', data_path
                ])


class StartParseDataBlockMeta(object):
    @staticmethod
    def run_task():
        import argparse

        parser = argparse.ArgumentParser()
        parser.add_argument('-d', '--dfs_data_block_dir', required=False, default='', type=str,
                            help="dfs_data_block_dir")
        parser.add_argument('-mt', '--dfs_data_block_meta', required=False, default='', type=str,
                            help="dfs_data_block_meta")
        parser.add_argument('-db', '--dfs_data_block', required=False, default='', type=str, help="dfs_data_block")
        args = parser.parse_args()
        dfs_data_block_dir = args.dfs_data_block_dir
        if dfs_data_block_dir:
            if dfs_data_block_dir.endswith('/'):
                dfs_data_block_dir = dfs_data_block_dir.strip('/')
            logging.info('Fetch Data Block Meta from dir:%s' % dfs_data_block_dir)
            dir_fpaths = \
                [os.path.join(dfs_data_block_dir, f)
                 for f in gfile.ListDirectory(dfs_data_block_dir)
                 if gfile.IsDirectory(os.path.join(dfs_data_block_dir, f))]

            for data_block_path in dir_fpaths:
                DataBlockController(data_block_path).data_block_controller()
        else:
            assert args.dfs_data_block_meta and args.dfs_data_block
            run_subprocess(
                [
                    'python', sys.modules[StartSyncConvertDataBlock.__module__].__file__,
                    '-d', time_stamp,
                    '-m', args.dfs_data_block_meta,
                    '-p', args.dfs_data_block
                ])


if __name__ == '__main__':
    StartParseDataBlockMeta().run_task()
