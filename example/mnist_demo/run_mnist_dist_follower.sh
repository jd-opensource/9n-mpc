follower_redis_host="127.0.0.1"
follower_redis_port=6379

python write_redis.py -H ${follower_redis_host} -P ${follower_redis_port} -f ../../conf/Trainer/follower.json 
