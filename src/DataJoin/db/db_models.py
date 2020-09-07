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
import datetime
import inspect
import sys
from peewee import Model, CharField, IntegerField, BigIntegerField, TextField, CompositeKey
from playhouse.pool import PooledMySQLDatabase
import __main__
from DataJoin.utils.base import current_timestamp
import logging
from DataJoin.config import DATABASE


def singleton(cls, *args, **kw):
    _registry = dict()

    def _singleton():
        key = str(cls) + str(os.getpid())
        if key not in _registry:
            _registry[key] = cls(*args, **kw)
        return _registry[key]

    return _singleton


@singleton
class DataBase(object):
    def __init__(self):
        db_conf = DATABASE.copy()
        db_name = db_conf.pop("name")
        self.db_connect = PooledMySQLDatabase(db_name, **db_conf)
        logging.info('init mysql database successfully')


def close_connect(db_connect):
    try:
        if db_connect:
            db_connect.close()
    except Exception as e:
        logging.error(str(e))


if __main__.__file__.endswith('route_server.py'):
    DB = DataBase().db_connect
else:
    DB = None


class DataBaseModel(Model):
    class Meta:
        database = DB

    def to_json(self):
        return self.__dict__['__data__']

    def save(self, *args, **kwargs):
        if hasattr(self, "update_date"):
            self.update_date = datetime.datetime.now()
        if hasattr(self, "update_time"):
            self.update_time = current_timestamp()
        super(DataBaseModel, self).save(*args, **kwargs)


def init_db():
    with DB.connection_context():
        components = inspect.getmembers(sys.modules[__name__], inspect.isclass)
        components_list = []
        for name, component in components:
            if component != DataBaseModel and issubclass(component, DataBaseModel):
                components_list.append(component)
        DB.create_tables(components_list)


class DataBlockMeta(DataBaseModel):
    block_id = CharField(max_length=500)
    dfs_data_block_dir = CharField(max_length=500)
    partition_id = IntegerField(null=True, default=0)
    file_version = IntegerField(null=True, default=0)
    start_time = BigIntegerField(null=True)
    end_time = BigIntegerField(null=True)
    example_ids = TextField(null=True, default='')
    leader_start_index = BigIntegerField(null=True)
    leader_end_index = BigIntegerField(null=True)
    follower_start_index = BigIntegerField(null=True)
    follower_end_index = BigIntegerField(null=True)
    data_block_index = BigIntegerField(null=True)
    follower_restart_index = BigIntegerField(null=True)
    create_time = BigIntegerField(null=True)
    update_time = BigIntegerField(null=True)
    create_status = IntegerField(null=True, default=0)
    consumed_status = IntegerField(null=True, default=0)
    data_source_name = CharField(max_length=300)

    class Meta:
        db_table = "data_block_meta"
        primary_key = CompositeKey('block_id')


class DataSourceMeta(DataBaseModel):
    data_source_name = CharField(max_length=100)
    partition_num = IntegerField(null=True, default=0)
    matching_window = BigIntegerField(null=True)
    data_source_type = IntegerField(null=True)
    max_example_in_data_block = BigIntegerField(null=True)
    inherit_data_source = CharField(max_length=100)
    start_time = BigIntegerField(null=True)
    end_time = BigIntegerField(null=True)
    create_time = BigIntegerField(null=True)
    update_time = BigIntegerField(null=True)

    class Meta:
        db_table = "data_source_meta"
        primary_key = CompositeKey('data_source_name')


class DataSource(DataBaseModel):
    data_source_name = CharField(max_length=100)
    dfs_data_block_dir = CharField(max_length=100)
    dfs_raw_data_dir = CharField(max_length=100)
    data_source_role = IntegerField(null=True, default=0)
    data_source_state = IntegerField(null=True, default=0)
    start_time = BigIntegerField(null=True)
    end_time = BigIntegerField(null=True)
    create_time = BigIntegerField(null=True)
    update_time = BigIntegerField(null=True)

    class Meta:
        db_table = "data_source"
        primary_key = CompositeKey('data_source_name')


class Coordinator(DataBaseModel):
    ip_port = CharField(max_length=100)
    register_code = IntegerField(null=True, default=0)
    query_code = IntegerField(null=True, default=0)
    local_uuid = CharField(max_length=100)
    remote_uuid = CharField(max_length=100)
    create_time = BigIntegerField(null=True)
    update_time = BigIntegerField(null=True)

    class Meta:
        db_table = "register_coordinator"
        primary_key = CompositeKey('local_uuid')
