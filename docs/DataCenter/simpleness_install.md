Stand-alone Deployment Guide
-----------------
        
### Start Data_Center Server

set Leader_Data_Center Env:

```bash
export LEADER_DATA_BLOCK_DIR=/app/9nfl_opensource/src/DataJoin/data_block_leader
export DATA_CENTER_PORT="50052"
export DATA_NUM_EPOCH=1
export MODE=local
```


set Follower_Data_Center Env:

```bash
export FOLLOWER_DATA_BLOCK_DIR=/app/9nfl_opensource/src/DataJoin/data_block_follower
export DATA_CENTER_PORT="50053"
export DATA_NUM_EPOCH=1
export MODE=local
```

start Data_Center server

```bash
cd /app/9nfl_opensource/src/DataJoin
sh start_server.sh center
```
