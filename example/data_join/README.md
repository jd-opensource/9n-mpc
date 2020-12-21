数据求交示例
--------
### 前提
准备好要求交的数据, 参见[mnist_data](../mnist_data/README.md)

### 单机版
环境准备参见[docs/DataJoin/simpleness_install_chinese_version.md](../../docs/DataJoin/simpleness_install_chinese_version.md)

启动数据求交的leader和follower
```bash 
bash data_join_leader.sh
bash data_join_follower.sh
```
求交需要花费一些时间, 日志在`src/DataJoin/logs/data_join_logs`, 输出结果在`../mnist_data/data_block_leader`和`../mnist_data/data_block_follower`

### 分布式版
1. 先决条件
  - hdfs, 数据求交的输入和输出都放在hdfs上
  - Kubernetes集群能访问hdfs, 为leader和folllower创建namespace(fl-leader,fl-follower)
  - mysql, 数据求交产生的datablock元数据放在mysql, 供datacenter使用
  - leader机和follower机部署*proxy*模块, 且能访问redis和Kubernetes集群, 参见[src/Proxy/README.md](../../src/Proxy/README.md)

2. 制作镜像
  
  修改src/DataJoin/config.py,
  配置mysql和redis信息，redis配置应与proxy使用的redis一致

  ```
    DATABASE = {
        'name': '', # 数据库名
        'user': '',
        'passwd': '',
        'host': '',
        'port': 3306,
        'max_connections': 100,
        'stale_timeout': 30,
    }
    REDIS = {
        'host': "",
        'port': 6379,
        'password': "",
        'max_connections': 500
    }
  PROXY_SERVICE_HOST = "localhost"
  PROXY_SERVICE_PORT = 3700

  ```

  **注意** src/DataJoin/config.py里的`PROXY_SERVICE_HOST`与*proxy*模块无关, 填"localhost"即可

  制作data join的镜像,
  参见`deploy/data_join/images`和[docs/DataJoin/distributer_install_chinese_version.md](../../docs/DataJoin/distributer_install_chinese_version.md)

3. 提交任务

  修改`dist_data_join_leader.sh`和`dist_data_join_follower.sh`中的镜像,输入输出hdfs目录,*proxy*地址

  ```
  # leader
  bash dist_data_join_leader.sh 

  # follower 
  bash dist_data_join_follower.sh 
  
  ```

