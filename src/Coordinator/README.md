Coordinator安装指导
------------

### 1. 编译 

编译依赖
| External dependencies | version      |
| --------------------- | ------------ |
| grpc                  | v1.26.0      |
| glog                  | a6a166d      |
| hiredis               | v0.14.1      |
| bazel                 | grpc version |

下载编译依赖
```bash
cd src/Coordinator
sh pre.sh
```

bazel编译
```bash
# 编译release版本
sh build.sh

# 编译debug版本
sh build.sh debug
```

编译完成后，`./output/bin`目录下会有`fl_client`和`fl_server`两个可执行文件

其中`fl_server`是coordinator模块服务器，建议部署成常驻服务

`fl_client`是用于leader提交训练任务, 向本侧的`fl_server`发rpc请求，并返回状态码

### 2. 部署

修改配置文件`../../conf/Coordinator/conf/fl_server.gflags`

redis的作用是保存模型配置信息和路由信息，与本侧proxy共用

如果只有1台coordinator机器，可配置`port=6666, coordinator_domain=xxxx:6666`

如果coordinator不只1台机器，且有域名，可配置`port=6666, coordinator_domain=your.coordnator.domain:2003`

```
-redis_hostname= # redis配置与本侧proxy共用
-redis_port=
-proxy_domain=XXXXX:8002 # 本侧proxy
-coordinator_domain=XXXXXX:xxxx # 向proxy注册的、用于proxy路由的域名:端口
-port=6666 # 本机coordinator监听端口
-log_dir=./logs
-logbufsecs=0
```

启动server， 建议使用gcc 5.2
```
# 指定配置文件
nohup ./output/bin/fl_server --flagfile=YOUR_FLAGFILE &

# 或者使用默认配置文件
sh start_server.sh
```

启动之后，日志在`logs`目录下

### 3. 提交训练任务

所有模块配置完成后，向redis写入模型配置文件，参见

运行`fl_client`可提交任务

```
cd src/Coordinator
./output/bin/fl_client --server_ip_port=127.0.0.1:6666 --model_uri=fl --model_version=1

# 或修改start_client.sh
sh start_client.sh
```

预期是: leader client启动后，follower server侧会尝试拉起follower k8s;

如果返回值正常，leader server会拉起leader k8s
