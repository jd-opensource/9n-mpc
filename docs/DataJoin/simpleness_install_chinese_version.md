单机版安装指导
----------------

[English](simpleness_install.md)

#### 依赖
python 3.6以上
   
#### 设置环境变量

拷贝`9nfl_opensource`到工作目录(比如 `/app`)下
```bash
mkdir -p /app
copy -r 9nfl_opensource /app
echo "/app/9nfl_opensource/src" > `python -c "import os;print(os.path.dirname(os.__file__))"`/site-packages/tmp.pth
```
 
#### 安装相关依赖包

```bash
cd /app/9nfl_opensource/
pip install -r requirements.txt
```

#### 编译pb
输出在`/app/9nfl_opensource/src/DataJoin/common`
```
cd /app/9nfl_opensource
python -m grpc_tools.protoc  -I protocols --python_out=src/ --grpc_python_out=src/ protocols/DataJoin/common/*.proto
```

#### Leader侧设置环境变量

在一台机器开两个终端，一端为leader侧，一端为follower侧
在leader侧设置环境变量
```bash
export ROLE=leader
export PARTITION_ID=0
export DATA_SOURCE_NAME=test_data_join
export MODE=local
export RAW_DATA_DIR=/app/9nfl_opensource/src/DataJoin/leader_train
#数据求交的原始目录，替换成你自己的目录
export DATA_BLOCK_DIR=/app/9nfl_opensource/src/DataJoin/data_block_leader
#数据求交结果存放的额目录，替换成你自己的目录
export PORT0="6001"
export REMOTE_IP="follower_ip:5001"
#follower_ip替换为follower侧服务的ip，5001为follower侧得服务端口，可自己定义，
#需要与follower侧环境变量端口保持一致
export RANK_UUID=DataJoinWorker-0
export RAW_DATA_ITER=TF_RECORD_ITERATOR
export EXAMPLE_JOINER=MEMORY_JOINER
```

启动leader侧服务
```bash
cd /app/9nfl_opensource/src/DataJoin/
sh start_server.sh join
```

#### Follower侧设置环境变量

在follower侧设置环境变量：
```
export ROLE=follower
export PARTITION_ID=0
export DATA_SOURCE_NAME=test_data_join
export MODE=local
export RAW_DATA_DIR=/app/9nfl_opensource/src/DataJoin/follower_train
#数据求交的原始目录，替换成你自己的目录
export DATA_BLOCK_DIR=/app/9nfl_opensource/src/DataJoin/data_block_follower
#数据求交结果存放的额目录，替换成你自己的目录
export PORT0="5001"
export REMOTE_IP="leader_ip:6001"
#leader_ip替换为leader侧服务的ip，6001为leader侧得服务端口，可自己定义
#需要与leader侧环境变量端口保持一致
export RANK_UUID=DataJoinWorker-0
export RAW_DATA_ITER=TF_RECORD_ITERATOR
export EXAMPLE_JOINER=MEMORY_JOINER
```

启动follower侧服务：
```
cd /app/9nfl_opensource/src/DataJoin/
sh start_server.sh join
```


