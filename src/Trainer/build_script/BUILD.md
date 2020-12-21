# 9N-FL OPS使用tf内部的gRPC async api,需要使用tf源码编译.

## 编译流程：

### 下载TF-V1.15.X源码
### 拷贝9NFL Trainer源码到tensorflow/contrib/jdfl, 代码目录结构：
     tensorflow/contrib/jdfl/
      ├── BUILD
      ├── kernels
      │   ├── dataset
      │   │   ├── ...
      │   ├── ...
      ├── ops
      │   ├── ...
      ├── python
      │   ├── ...
      └── rpc
          ├── proto
          │   ├── ...
          └── rpc_bridge
              ├── ...
### 编译9N-FL OPS:
     sh flops_build_.sh


