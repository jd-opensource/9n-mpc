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

glog_git_src="https://github.com/google/glog.git"
glog_commit="a6a166d"
grpc_git_src="https://github.com/grpc/grpc.git"
grpc_commit="v1.28.0"
hiredis_git_src="https://github.com/redis/hiredis.git"
hiredis_commit="v0.14.1"

function execshell()
{
    echo "[execshell]$@ begin."
    eval $@
    [[ $? != 0 ]] && {
        echo "[execshell]$@ failed."
        exit 1
    }
    echo "[execshell]$@ success."
    return 0
}

function gitclone()
{
  git clone $1
  project=${1##*/}
  project=${project%.*}
  cd $project
  git checkout $2
  cd ..
}

set -x
cd third_party
execshell "gitclone" $glog_git_src $glog_commit
execshell "gitclone" $grpc_git_src $grpc_commit
cd hiredis
execshell "gitclone" $hiredis_git_src $hiredis_commit
cd hiredis
make
