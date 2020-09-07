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
WORK_DIR=$(cd `dirname $0`;pwd)
MNIST_DIR=`readlink -f "${WORK_DIR}"`
BASE_DIR=`readlink -f "${WORK_DIR}/../.."`

DATA_DIR=$1

if [ -z ${DATA_DIR} ];then
    DATA_DIR=`readlink -f "../mnist_data"`
fi

local_host="`hostname --fqdn`"
LOCAL_IP=`python -c"import socket;s=socket.socket(socket.AF_INET,socket.SOCK_DGRAM);s.connect(('8.8.8.8', 80));print(s.getsockname()[0])"`

CHECK_EXAMPLEID=1

L_DC_ADDR="${LOCAL_IP}:50052"
F_DC_ADDR="${LOCAL_IP}:50053"

L_TRAIN_ADDR="${LOCAL_IP}:40001"
F_TRAIN_ADDR="${LOCAL_IP}:40002"

if [ ! -d ${MNIST_DIR}/logs ];then
    mkdir ${MNIST_DIR}/logs
fi

dc_leader(){
export LEADER_DATA_BLOCK_DIR="${DATA_DIR}/data_block_leader"
export FOLLOWER_DATA_BLOCK_DIR="None"
export DATA_CENTER_PORT="50052"
export DATA_NUM_EPOCH=1
export MODE=local    
cd ${BASE_DIR}/src/DataJoin
sh start_server.sh center
cd -
}

train(){
export  _FILE_GET_CMD="./local_get.sh"
python  ${MNIST_DIR}/mnist_leader.py --local_addr="${L_TRAIN_ADDR}" \
--peer_addr="${F_TRAIN_ADDR}" --dc_addr="${L_DC_ADDR}" \
--rpc_service_type=1 \
--local_debug=1 \
--check_exampleid=$CHECK_EXAMPLEID \
--model_dir="./models/leader_model" \
--export_dir="./models/leader_export_savemodel" \
> ${MNIST_DIR}/logs/leader.log 2>&1 &
echo "log: ${MNIST_DIR}/logs/leader.log"

}

rm -rf models/*
bash kill.sh
dc_leader
train


