单机版安装文档
--------------
由于先开启单机版数据求教，再开启Data_Center服务，所以一些安装依赖不需要再重复安装，

临时环境变量也不需要重复设置，编译也不需要重复编译
        
1. 开启Leader_Data_Center服务

引入环境变量
```bash
export LEADER_DATA_BLOCK_DIR=/app/9nfl_opensource/src/DataJoin/data_block_leader
#leader侧数据求交结果存放目录，与数据求交leader侧环境变量DATA_BLOCK_DIR保持一致
export DATA_CENTER_PORT="50052"
export DATA_NUM_EPOCH=1
export MODE=local


2.开启Follower_Data_Center服务

引入环境变量：
export FOLLOWER_DATA_BLOCK_DIR=/app/9nfl_opensource/src/DataJoin/data_block_follower
#follower侧数据求交结果存放目录，与数据求交follower侧环境变量DATA_BLOCK_DIR保持一致
export DATA_CENTER_PORT="50053"
export DATA_NUM_EPOCH=1
export MODE=local
```

开启Data_Center服务
```bash
cd /app/9nfl_opensource/src/DataJoin
sh start_server.sh center
```
