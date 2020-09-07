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

import json
from flask import jsonify
from DataJoin.utils.data_transfer import get_data_transfer_stub, data_transfer_bucket


def response_api(retcode=0, retmsg='success', data=None):
    response_result= {"code": retcode, "msg": retmsg, "data": data}
    return jsonify(response_result)


def wrap_data_transfer_api(request_method, request_url, request_body):
    data_transfer = data_transfer_bucket(request_body, request_method, request_url)
    try:
        data_transfer_channel, data_transfer_stub = get_data_transfer_stub()
        data_transfe_response = data_transfer_stub.UnaryCall(data_transfer)
        data_transfer_channel.close()
        data_transfe_result = json.loads(data_transfe_response.body.value)
        return data_transfe_result
    except Exception as e:
        raise Exception('data transfer error: {}'.format(e))



