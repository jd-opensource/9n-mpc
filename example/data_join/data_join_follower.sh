WORK_DIR=$(cd `dirname $0`;pwd)
BASE_DIR=`readlink -f "${WORK_DIR}/../.."`
# BASE_DIR为9nfl_opensource根目录

export ROLE=follower
export PARTITION_ID=0
export DATA_SOURCE_NAME=test_data_join
export MODE=local
export RAW_DATA_DIR=${BASE_DIR}/example/mnist_data/follower_train
#数据求交的原始目录，替换成你自己的目录
export DATA_BLOCK_DIR=${BASE_DIR}/example/mnist_data/data_block_follower
#数据求交结果存放的额目录，替换成你自己的目录

export PORT0="5001"
export REMOTE_IP="0.0.0.0:6001"
# leader: 0.0.0.0:6001
# follower: 0.0.0.0:5001

export RANK_UUID=DataJoinWorker-0
export RAW_DATA_ITER=TF_RECORD_ITERATOR
export EXAMPLE_JOINER=MEMORY_JOINER

cd ${BASE_DIR}/src/DataJoin/
sh start_server.sh join


