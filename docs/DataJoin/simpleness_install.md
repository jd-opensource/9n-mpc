Stand-alone Deployment Guide
----------
[中文版](simpleness_install_chinese_version.md)

#### Prerequisite
python 3.6+
   
#### Set Environment
copy or move 9nfl_opensource into `/app` (you can change the root dir `/app` into yours directory);
```bash
mkdir -p /app
copy -r 9nfl_opensource /app
echo "/app/9nfl_opensource/src" > `python -c "import os;print(os.path.dirname(os.__file__))"`/site-packages/tmp.pth
```
 
#### Install Requirements
```bash
cd /app/9nfl_opensource/src/DataJoin/
pip install -r requirements.txt
```

#### Compile pb
```bash
cd /app/9nfl_opensource
python -m grpc_tools.protoc  -I protocols --python_out=src/
--grpc_python_out=src/ protocols/DataJoin/common/*.proto
```

#### Set Leader Environment

```bash
export ROLE=leader
export PARTITION_ID=0
export DATA_SOURCE_NAME=test_data_join
export MODE=local
export RAW_DATA_DIR=/app/9nfl_opensource/src/DataJoin/leader_train
export DATA_BLOCK_DIR=/app/9nfl_opensource/src/DataJoin/data_block_leader
export PORT0="6001"
export REMOTE_IP="follower_ip:5001"

#please replace follower_ip with follower server ip address
export RANK_UUID=DataJoinWorker-0
export RAW_DATA_ITER=TF_RECORD_ITERATOR
export EXAMPLE_JOINER=MEMORY_JOINER

cd /app/9nfl_opensource/src/DataJoin/
sh start_server.sh join
```

#### Set Follower Environment

```bash
export ROLE=follower
export PARTITION_ID=0
export DATA_SOURCE_NAME=test_data_join
export MODE=local
export RAW_DATA_DIR=/app/9nfl_opensource/src/DataJoin/follower_train
export DATA_BLOCK_DIR=/app/9nfl_opensource/src/DataJoin/data_block_follower
export PORT0="5001"
export REMOTE_IP="leader_ip:6001"
#please replace leader_ip with leader server ip address
export RANK_UUID=DataJoinWorker-0
export RAW_DATA_ITER=TF_RECORD_ITERATOR
export EXAMPLE_JOINER=MEMORY_JOINER

cd /app/9nfl_opensource/src/DataJoin/
sh start_server.sh join
```


