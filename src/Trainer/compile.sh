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
copy_file(){

    TENSORFLOW=$1
    mkdir ${JDFL}
    cp BUILD ${JDFL}
    cp -r kernels ${JDFL}
    cp -r ops ${JDFL}
    cp -r rpc ${JDFL}

    cp build_script/flops_build.sh ${TENSORFLOW}/
}


TENSORFLOW=$1
if [ -z ${TENSORFLOW} ];then
    echo "usage: bash $0 tensorflow_dir"
    exit -1
fi

set -x
#copy_file ${TENSORFLOW}
cd ${TENSORFLOW}
bash flops_build.sh
cd -
cp ${TENSORFLOW}/bazel-bin/tensorflow/contrib/jdfl/_fl_ops.so .


#~/anaconda3/bin/python setup.py sdist
