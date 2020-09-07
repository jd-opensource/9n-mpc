### FL Trainer
我们提供编译好的`_fl_ops.so`, 也可以参照下面的步骤重新编译`_fl_ops.so`

#### 编译依赖
1. Tensorflow 1.15 源码
```bash
git clone https://github.com/tensorflow/tensorflow.git
git chekout -b r1.15 origin/r1.15
```
2. bazel 0.26.1
参见 [bazel github](https://github.com/bazelbuild/bazel/releases/tag/0.26.1)
```bash
wget https://github.com/bazelbuild/bazel/releases/download/0.26.1/bazel-0.26.1-installer-linux-x86_64.sh
sh bazel-0.26.1-installer-linux-x86_64.sh --user
```
3. GCC 5.2

#### 编译
```bash
bash compile.sh ${full_path_of_your_tensorflow_dir}
```
