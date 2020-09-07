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

import operator
from DataJoin.db.db_models import DB, DataBlockMeta, DataSourceMeta, DataSource, Coordinator


def query_data_block_meta(**kwargs):
    with DB.connection_context():
        filters = []
        for f_n, f_v in kwargs.items():
            attr_name = '%s' % f_n
            if hasattr(DataBlockMeta, attr_name):
                if attr_name == "start_time":
                    filters.append(operator.attrgetter('%s' % f_n)(DataBlockMeta) >= f_v)
                elif attr_name == "end_time":
                    filters.append(operator.attrgetter('%s' % f_n)(DataBlockMeta) <= f_v)
                else:
                    filters.append(operator.attrgetter('%s' % f_n)(DataBlockMeta) == f_v)
        if filters:
            data_block_metas = DataBlockMeta.select(DataBlockMeta.block_id, DataBlockMeta.data_block_index,
                                                    DataBlockMeta.dfs_data_block_dir, DataBlockMeta.start_time,
                                                    DataBlockMeta.end_time, DataBlockMeta.leader_end_index,
                                                    DataBlockMeta.leader_start_index,
                                                    DataBlockMeta.follower_restart_index, DataBlockMeta.partition_id,
                                                    DataBlockMeta.file_version).where(*filters)
        else:
            data_block_metas = DataBlockMeta.select(DataBlockMeta.block_id, DataBlockMeta.data_block_index,
                                                    DataBlockMeta.dfs_data_block_dir, DataBlockMeta.start_time,
                                                    DataBlockMeta.end_time, DataBlockMeta.leader_end_index,
                                                    DataBlockMeta.leader_start_index,
                                                    DataBlockMeta.follower_restart_index, DataBlockMeta.partition_id,
                                                    DataBlockMeta.file_version)
        return [data_block_meta for data_block_meta in data_block_metas]


def query_data_source_meta(**kwargs):
    with DB.connection_context():
        filters = []
        for f_n, f_v in kwargs.items():
            attr_name = '%s' % f_n
            if hasattr(DataSourceMeta, attr_name):
                filters.append(operator.attrgetter('%s' % f_n)(DataSourceMeta) == f_v)
        if filters:
            data_source_metas = DataSourceMeta.select().where(*filters)
        else:
            data_source_metas = DataSourceMeta.select()
        return [data_source_meta for data_source_meta in data_source_metas]


def query_data_source(**kwargs):
    with DB.connection_context():
        filters = []
        for f_n, f_v in kwargs.items():
            attr_name = '%s' % f_n
            if hasattr(DataSource, attr_name):
                filters.append(operator.attrgetter('%s' % f_n)(DataSource) == f_v)
        if filters:
            data_sources = DataSource.select().where(*filters)
        else:
            data_sources = DataSource.select()
        return [data_source for data_source in data_sources]
