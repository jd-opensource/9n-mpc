#!/bin/sh
# Copyright 2020 The 9nFL Authors. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

DISABLE="--config=noaws --config=nogcp --config=noignite --config=nokafka --config=nonccl"
BAZEL_FLAGS="--config=opt --cxxopt=-D_GLIBCXX_USE_CXX11_ABI=0 ${DISABLE}"

tf_config() {
    export PYTHONDONTWRITEBYTECODE=1
    export PYTHON_BIN_PATH=$(which python3)
    export PYTHON_LIB_PATH="$($PYTHON_BIN_PATH -c 'import site; print(site.getsitepackages()[0])')"

    export TF_NEED_GCP=1
    export TF_NEED_HDFS=1
    export TF_NEED_OPENCL=0
    export TF_NEED_JEMALLOC=1
    export TF_ENABLE_XLA=0
    export TF_NEED_VERBS=0
    export TF_NEED_MKL=0
    export TF_DOWNLOAD_MKL=0
    export TF_NEED_MPI=0
    export TF_NEED_GDR=0
    export TF_NEED_S3=0
    export TF_NEED_AWS=0
    export TF_NEED_KAFKA=0
    export TF_DOWNLOAD_CLANG=0
    export TF_SET_ANDROID_WORKSPACE=0
    export TF_NEED_OPENCL_SYCL=0
    export TF_CUDA_CLANG=0
    export TF_NEED_NGRAPH=0
    export TF_NEED_ROCM=0
    export TF_NEED_CUDA=0
    
    export GCC_HOST_COMPILER_PATH=$(which gcc)
    export CC_OPT_FLAGS="-march=native"

    echo "Bazel flags: $BAZEL_FLAGS"
    ./configure 
}


tf_config

bazel build ${BAZEL_FLAGS} --jobs 4 //tensorflow/contrib/jdfl:_fl_ops.so --verbose_failures

exit 0
