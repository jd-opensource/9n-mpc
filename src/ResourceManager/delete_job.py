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
import os
import sys
import argparse

def get_args():
    """get command arguments"""
    parser = argparse.ArgumentParser()
    parser.add_argument("role", help="is leader or follower")
    parser.add_argument("task_id", help="the task id for stop")
    return parser.parse_args()

def delete():
    """delete the job"""
    args = get_args()
    if args.role not in ['0', '1']:
        print('role should be 1:leader or 0:follower')
        sys.exit(-1)
    name_space = 'fl-follower'
    if args.role == '1':
        name_space = 'fl-leader'
    retval_deljob = os.system('kubectl delete tfjob -l instanceId=%s -n %s' \
        % (args.task_id, name_space))
    retval_delrc = os.system('kubectl delete rc -l instanceId=%s -n %s' % \
        (args.task_id, name_space))
    retval_delsvc = os.system('kubectl delete svc -l instanceId=%s -n %s' \
        % (args.task_id, name_space))
    if retval_deljob != 0 or retval_delrc != 0 or retval_delsvc != 0:
        print('delete job error, pelease check th kubectl!')
        sys.exit(-1)

if __name__ == '__main__':
    delete()
