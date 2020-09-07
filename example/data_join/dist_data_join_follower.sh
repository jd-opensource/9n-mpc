WORK_DIR=$(cd `dirname $0`;pwd)
BASE_DIR=`readlink -f "${WORK_DIR}/../.."`

create_pod(){
set -x
partition_id=$1
data_source_name="jdfl-opensource-data-join-v1"
task_name="${data_source_name}-follower-worker-${partition_id}"
uuid="jdfl_DataJoinWorkerService_${partition_id}"
image_path="" # data join follower image path
raw_data_dir="" # hdfs data dir for the input of data join
PORT0=8001 # data join port
data_block_dir="" # hdfs data dir for the output of data join
proxy="" # follower proxy host and port

sed \
"s/TASK_NAME/${task_name}/;s!IMAGE_PATH!${image_path}!;s/PID/${partition_id}/;s/DSN/${data_source_name}/;s!RDD!${raw_data_dir}!;s/DATA_JOIN_PORT/${PORT0}/;s!DBD!${data_block_dir}!;s/RI/${uuid}/;s/internal/${proxy}/g" \
${BASE_DIR}/deploy/data_join/k8s/follower.yaml > follower.yaml

namespace="fl-follower"

cat follower.yaml |kubectl --namespace=${namespace} create -f -
}

# data join for partition 0
create_pod 0

