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
import logging
from DataJoin.utils import data_process
from DataJoin.utils.api import response_api
from DataJoin.controller.data_controller import DataController

manager = Flask(__name__)


@manager.errorhandler(500)
def internal_server_error(e):
    logging.error(str(e))
    return response_api(retcode=500, retmsg=str(e))


@manager.route('/<block_id>/<partition_id>/<file_version>/create/data/block', methods=['POST'])
def create_data_block_meta(block_id, partition_id, file_version):
    DataController.update_data_block_meta_status(block_id=block_id, partition_id=partition_id,
                                                 file_version=int(file_version), data_block_meta_info=request.json,
                                                 create=True)
    return response_api(retcode=0, retmsg='success')


@manager.route('/query/data/block/meta', methods=['POST'])
def query_data_block_meta():
    data_block_metas = data_process.query_data_block_meta(**request.json)
    if not data_block_metas:
        return response_api(retcode=500, retmsg='find data block meta failed')
    return response_api(retcode=0, retmsg='success',
                        data=[meta.to_json() for meta in data_block_metas])


@manager.route('/query/data/source/meta', methods=['POST'])
def query_data_source_meta():
    data_source_metas = data_process.query_data_source_meta(**request.json)
    if not data_source_metas:
        return response_api(retcode=500, retmsg='find data source meta failed')
    return response_api(retcode=0, retmsg='success',
                        data=[data_source_meta.to_json() for data_source_meta in data_source_metas])


@manager.route('/query/data/source', methods=['POST'])
def query_data_source():
    data_sources = data_process.query_data_source(**request.json)
    if not data_sources:
        return response_api(retcode=500, retmsg='find data source failed')
    return response_api(retcode=0, retmsg='success', data=[data_source.to_json() for data_source in data_sources])
