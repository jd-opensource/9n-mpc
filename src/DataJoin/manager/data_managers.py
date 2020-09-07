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

from DataJoin.utils.base import current_timestamp
from DataJoin.db.db_models import DB, DataBlockMeta, DataSourceMeta, DataSource
import logging


class DataManagers(object):
    def __init__(self, block_id: str = None, partition_id: str = None, file_version: int = None,
                 data_source_name: str = None,
                 dfs_data_block_dir: str = None,
                 dfs_raw_data_dir: str = None,
                 data_source_role: int = None,
                 data_source_state: int = None):
        self.block_id = block_id
        self.partition_id = partition_id
        self.file_version = file_version
        self.data_source_name = data_source_name
        self.dfs_data_block_dir = dfs_data_block_dir
        self.dfs_raw_data_dir = dfs_raw_data_dir
        self.data_source_role = data_source_role
        self.data_source_state = data_source_state

    def save_data_block_meta_info(self, data_block_meta_info, create=False):
        with DB.connection_context():
            logging.info(
                'save {} {} data_block_meta: {}'.format(self.block_id, self.partition_id, data_block_meta_info))
            data_block_metas = DataBlockMeta.select().where(DataBlockMeta.block_id == self.block_id)
            is_insert = True
            if data_block_metas:
                data_block_meta = data_block_metas[0]
                is_insert = False
            elif create:
                data_block_meta = DataBlockMeta()
                data_block_meta.create_time = current_timestamp()
                data_block_meta.update_time = current_timestamp()
            else:
                return None
            data_block_meta.block_id = self.block_id
            data_block_meta.partition_id = self.partition_id
            data_block_meta.file_version = self.file_version
            for k, v in data_block_meta_info.items():
                try:
                    if k in ['block_id', 'partition_id', 'file_version'] or v == getattr(DataBlockMeta, k).default:
                        continue
                    setattr(data_block_meta, k, v)
                except:
                    pass
            if is_insert:
                data_block_meta.save(force_insert=True)
            else:
                data_block_meta.save()

    def save_data_source_meta_info(self, data_source_meta_info, create=False):
        with DB.connection_context():
            logging.info(
                'save {} data_source_meta: {}'.format(self.data_source_name, data_source_meta_info))
            data_source_metas = DataSourceMeta.select().where(DataSourceMeta.block_id == self.block_id)
            is_insert = True
            if data_source_metas:
                data_source_meta = data_source_metas[0]
                is_insert = False
            elif create:
                data_source_meta = DataSourceMeta()
                data_source_meta.create_time = current_timestamp()
                data_source_meta.update_time = current_timestamp()
            else:
                return None
            for k, v in data_source_meta_info.items():
                try:
                    if k in ['data_source_name'] or v == getattr(DataSourceMeta, k).default:
                        continue
                    setattr(data_source_meta, k, v)
                except:
                    pass
            if is_insert:
                data_source_meta.save(force_insert=True)
            else:
                data_source_meta.save()

    def save_data_source_info(self, data_source_info, create=False):
        with DB.connection_context():
            logging.info(
                'save {} data_source: {}'.format(self.data_source_name, data_source_info))
            data_sources = DataSource.select().where(DataSource.block_id == self.block_id)
            is_insert = True
            if data_sources:
                data_source = data_sources[0]
                is_insert = False
            elif create:
                data_source = DataSource()
                data_source.create_time = current_timestamp()
                data_source.update_time = current_timestamp()
            else:
                return None
            data_source.data_source_state = self.data_source_state
            data_source.dfs_data_block_dir = self.dfs_data_block_dir
            data_source.dfs_raw_data_dir = self.dfs_raw_data_dir
            for k, v in data_source_info.items():
                try:
                    if k in ['data_source_name'] or v == getattr(DataSource, k).default:
                        continue
                    setattr(data_source, k, v)
                except:
                    pass
            if is_insert:
                data_source.save(force_insert=True)
            else:
                data_source.save()
