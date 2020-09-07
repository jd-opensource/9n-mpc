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
# coding=utf-8
"""python script for check task status on Kubernetes"""
import sys
import argparse
import json
try:
    from commands import getstatusoutput as sys_execute
except Exception as e:
    from subprocess import getstatusoutput as sys_execute

def get_args():
    """get command arguments"""
    parser = argparse.ArgumentParser()
    parser.add_argument("role", help="is leader or follower")
    parser.add_argument("task_id", help="the task id to check")
    return parser.parse_args()

def check():
    """check status"""
    args = get_args()
    name_space = 'fl-follower'
    if args.role == '1':
        name_space = 'fl-leader'
    retcode = {'Running':0, 'Created':0, 'Restarting':0, 'Succeeded':1, 'Failed':2}
    try:
        status, output = sys_execute('kubectl get tfjob -l instanceId=%s -n %s -o json' \
        % (args.task_id, name_space))
        if status != 0:
            print('Error: %s' % output)
            sys.exit(3)
        json_dt = json.loads(output)
        sys.exit(retcode[json_dt['items'][0]['status']['conditions'][-1]['type']])
    except Exception as e_str:
        print('Exception: %s' % str(e_str))
        sys.exit(3)

if __name__ == '__main__':
    check()
