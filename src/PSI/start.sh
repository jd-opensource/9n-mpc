#!/bin/bash

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

echo $*

source ~/.bashrc

# default log root dir
LOGDIR=/mnt/logs

# obtain log files
for key in "$@"; do
    case $key in
    --log-path=*)
        LOGDIR="${key#*=}"
        shift # past argument=value
        ;;
    --default)
        DEFAULT=YES
        shift # past argument with no value
        ;;
    *) ;;
    esac
done

LOGFILENAME="psi.log"

echo "environment node name ${MY_NODE_NAME}"

if [[ -z ${MY_NODE_NAME} ]]; then
    LOGFILE=${LOGDIR}/${TASK_ID}_${APP_ID}_${NODE_ID}_${LOGFILENAME}
else
    LOGFILE=${LOGDIR}/${MY_NODE_NAME}_${LOGFILENAME}
fi

echo "write logs to ${LOGFILE}"

source /root/.bashrd
python3 ./psi_actors.py "$@" 2>&1 | tee ${LOGFILE}

sleep 1d
