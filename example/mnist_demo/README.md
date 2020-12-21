FL Mnist Demo
----------------
#### 先决条件
1. 准备好求交后的mnist数据, 供fl训练器使用,
   参见[mnist_data](../mnist_data/README.md)和[data_join](../data_join/README.md)

2. 运行单机版前，将`fl_comm_libs`复制到这个目录
```
cp -r ../../src/Trainer/fl_comm_libs .
```

#### 单机版
1. 准备python3.6环境(推荐python3.6.8)

我们推荐使用`virtualenv`来避免python相关的环境问题

运行`pip install virtualenv`来安装virtualenv

```bash
yum install python3-devel

cd ~
# 创建一个python虚拟环境
virtualenv fl-env
# 激活环境
source fl-env/bin/activate

# 要求 tensorflow 1.15 和 protobuf 3.8.0
pip install -r ../../requirements.txt

# 配置tensorflow
wget https://github.com/tensorflow/tensorflow/blob/r1.15/tensorflow/python/pywrap_dlopen_global_flags.py 
cp pywrap_dlopen_global_flags.py fl-env/lib/python3.6/site-packages/tensorflow_core/python/
```

如果不打算使用virutalenv, 请确保tensorflow版本为1.15且protobuf版本为3.8.0.

然后下载[pywrap_dlopen_global_flags.py](https://github.com/tensorflow/tensorflow/blob/r1.15/tensorflow/python/pywrap_dlopen_global_flags.py)并拷贝到tensorflow目录, 例如`site-packages/tensorflow_core/python/`

2. 运行mnist demo
我们提供了一个神经网络的例子来验证联邦学习框架的正确性:
一张mnist图片被分成两半，分别由leader和follower所有，leader和follower共同训练一个神经网络模型.
```
# 用联邦学习框架训练模型
bash run_mnist_local_leader.sh
bash run_mnist_local_follower.sh

# 用原生tensorflow训练模型
python baseline.py -d ${DATA_DIR}

```

3. 比较loss
配置相同情况下, 联邦学习模型loss的变化情况应与原生tensorflow框架的loss变化一致
```
# 联邦学习模型的loss变化情况
grep loss logs/leader.log
```

#### 分布式版
1. 先决条件
  - hdfs, 训练数据和模型会放在hdfs上
  - Kubernetes集群能访问hdfs, 为leader和folllower创建namespace(`fl-leader`,`fl-follower`)
  - leader机和follower机python环境依赖jinjia2, 且需要能访问redis和Kubernetes集群.

2. proxy

在leader机和follower机部署proxy

编译和部署proxy参见[src/Proxy/README.md](../../src/Proxy/README.md)

需要修改redis配置和对端proxy配置, 默认对端请求监听8001端口,

redis默认使用6379端口

默认同侧请求监听8002端口, 日志在src/Proxy/logs

3. coordinator

编译和部署参见[src/Coordinator/README.md](../../src/Coordinator/README.md)

同侧coordinator和proxy应使用相同redis

coordinator默认监听6666端口, 日志在src/Coordinator/logs

4. Kubernetes准备

创建datacenter镜像(参见[deploy/data_center/images](../../deploy/data_center/images)), 

leader训练镜像和follower训练镜像(参见[DockerFile](../../DockerFile))

配置conf/ResourceManager/k8s.conf

修改proxy配置(默认8002端口), coordinator配置(默认6666端口), 镜像信息，模型目录 

5. 提交训练任务

根据需要修改`conf/Trainer/leader.json`和`conf/Trainer/follower.json`中的`worker_num`和`data_source_name`

修改`run_mnist_dist_follower.sh`和`run_mnist_dist_leader.sh`中的redis配置

```bash
# follower
bash run_mnist_dist_follower.sh

# leader
bash run_mnist_dist_leader.sh
```
