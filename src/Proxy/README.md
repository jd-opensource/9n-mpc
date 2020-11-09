# 依赖

| External dependencies  | Version           | Description              |
| ---------------------- | ----------------- | :----------------------- |
| openresty              | 1.17.8.1rc1       | Generate bin file        |
| nginx                  | openresty version |                          |
| redis                  | 6.0.3             |                          |

# 编译

```bash
wget https://openresty.org/download/openresty-1.17.8.1rc1.tar.gz
tar -xf openresty-1.17.8.1rc1.tar.gz

cd openresty-1.17.8.1rc1
# make sure '~/bin/blade' does not exist
./configure --with-http_v2_module 
gmake && gmake install
mkdir bin && cp openresty-1.17.8.1rc1/build/nginx-1.17.8/objs/nginx bin/ 
```

# 修改配置

1. config/fl_proxy.conf
    - 8001: 监听对面过来的流量, 查询本侧redis获取路由信息, `backend_addr`无需修改,`$redis_url`和`$redis_port`配置为proxy和coordinator共享的redis服务的host和port

    - 8002: 监听本侧想出去的流量，故本侧其他模块配置的proxy端口是8002, `grpc_pass`配置为对端proxy的监听地址, 如xxxx:8001

2. config/nginx.conf
  - `lua_package_path`加入本地openresty的安装路径


