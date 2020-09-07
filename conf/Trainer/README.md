训练配置
-----
训练之前双方应调用(example/mnist_demo/write_redis.py)[../example/mnist_demo/write_redis.py]把各自的json文件写入coordinator的redis里.

```
{
    "conf_info": {
        "model_uri": "fl",
        "version": "1",
        "worker_num": 2,
        "data_source_name": "jdfl-opensource-data-join-v1",
        "train_data_start": "20191001042452",
        "train_data_end": "20191001042452",
        "data_num_epoch": 1
    }   
}
```

支持的配置项参见(src/Coordinator/proto/internal_service.proto)[../src/Coordinator/proto/internal_service.proto]里的ConfInfo



