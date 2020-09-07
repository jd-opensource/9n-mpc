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

from DataJoin.manager.data_managers import DataManagers


class DataController(object):

    @staticmethod
    def init():
        pass

    @staticmethod
    def update_data_block_meta_status(block_id, partition_id, file_version, data_block_meta_info, create=True):
        data_managers = DataManagers(block_id=block_id, partition_id=partition_id, file_version=file_version)
        data_managers.save_data_block_meta_info(data_block_meta_info=data_block_meta_info, create=create)
