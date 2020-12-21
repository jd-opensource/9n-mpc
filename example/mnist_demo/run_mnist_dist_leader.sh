leader_redis_host="127.0.0.1"
leader_redis_port=6379
leader_coordinator_host="127.0.0.1"
leader_coordinator_port=6666

python write_redis.py -H ${leader_redis_host} -P ${leader_redis_port} -f ../../conf/Trainer/leader.json 

../../src/Coordinator/output/bin/fl_client --server_ip_port="${leader_coordinator_host}:${leader_coordinator_port}" --model_uri=fl --model_version=1
