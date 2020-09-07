# Compile dependencies

| External dependencies | version      |
| --------------------- | ------------ |
| grpc                  | v1.26.0      |
| glog                  | a6a166d      |
| hiredis               | v0.14.1      |
| bazel                 | grpc version |

# Compile

- sh pre.sh Download the required dependent libraries
- sh build.sh Compile

# Attention

- Modify gflags infos, including redis_hostname/-redis_port/-proxy_domain/-coordinator_domain
- The model related information needs to be stored in redis in the form of json before running
