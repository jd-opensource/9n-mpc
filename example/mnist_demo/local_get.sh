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
DFS_FILE=$1
DEST_FILE=$2

DEST_DIR=`dirname ${DEST_FILE}`

while [ 0 -eq 0 ]
do
    echo "[LOCAL INFO] >> Get File ${DFS_FILE} Start "

    if [ ! -e ${DEST_DIR} ];then
        echo ${DEST_DIR} "not exist create"
        mkdir -p ${DEST_DIR}
    fi
    if [ -f ${DEST_FILE} ];then
        echo "${DEST_FILE} Exists! Remove it First."
        \rm -f ${DEST_FILE}
    fi
    cp ${DFS_FILE} ${DEST_FILE}

    # check and retry

    if [ $? -eq 0 ]; then
        echo "[LOCAL INFO] >> Get File ${DFS_FILE} complete "
        break;
    else
        echo "[LOCAL ERROR] >> Get File ${DFS_FILE} ERROR Occur, retry in 2 seconds"
        echo ${DFS_FILE} >> HDFS_MISSING_FILE
        sort HDFS_MISSING_FILE -o HDFS_MISSING_FILE_TEMP
        uniq HDFS_MISSING_FILE_TEMP HDFS_MISSING_FILE
        sleep 2
    fi
done

exit 0

