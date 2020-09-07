#!/bin/bash

CURRENT_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
http_server_log_dir="${CURRENT_DIR}/logs/http_server_logs"
data_join_log_dir="${CURRENT_DIR}/logs/data_join_logs"
data_center_log_dir="${CURRENT_DIR}/logs/data_center_logs"
IFS='-' read -r -a array <<< "$RANK_UUID"
export INDEX="${array[1]}"


get_http_server_pid() {
    http_server_pid=`ps aux | grep "python $CURRENT_DIR/route_server.py" | grep -v grep | awk '{print $2}'`
    if [[ -n ${http_server_pid} ]]; then
        return 0
    else
        return 1
    fi
}

get_data_join_server_pid() {
    data_join_server_pid=`ps aux | grep "python $CURRENT_DIR/data_join/data_join_server.py" | grep -v grep | awk '{print $2}'`
    if [[ -n ${data_join_server_pid} ]]; then
        return 0
    else
        return 1
    fi
}

mkdir_http_server_log_dir() {
    if [[ ! -d $http_server_log_dir ]]; then
        mkdir -p $http_server_log_dir
    fi
}

mkdir_data_join_log_dir() {
    if [[ ! -d $data_join_log_dir ]]; then
        mkdir -p $data_join_log_dir
    fi
}

mkdir_data_center_log_dir() {
    if [[ ! -d $data_center_log_dir ]]; then
        mkdir -p $data_center_log_dir
    fi
}

http_server_status() {
    get_http_server_pid
    if [[ -n ${http_server_pid} ]]; then
        echo "http_server_status:
        `ps aux | grep ${http_server_pid} | grep -v grep`"
        exit 1
    else
        echo "http service not running"
        exit 0
    fi
}


data_join_server_status() {
    get_data_join_server_pid
    if [[ -n ${data_join_server_pid} ]]; then
        echo "data_join_server_pid:
        `ps aux | grep ${data_join_server_pid} | grep -v grep`"
        exit 1
    else
        echo "data join service not running"
        exit 0
    fi
}

start_http_server() {
    get_http_server_pid
    if [[ $? -eq 1 ]]; then
        mkdir_http_server_log_dir
        nohup python $CURRENT_DIR/route_server.py >> "${http_server_log_dir}/console.log" 2>>"${http_server_log_dir}/error.log" &
        if [[ $? -eq 0 ]]; then
            sleep 2
            get_http_server_pid
            if [[ $? -eq 0 ]]; then
                echo "http service start successful. pid: ${http_server_pid}"
            else
                echo " http service start failed"
            fi
        else
            echo "http service start failed"
        fi
    else
        echo "http service already started. pid: ${http_server_pid}"
    fi
}


data_join_server_start() {
    mkdir_data_join_log_dir
    nohup python $CURRENT_DIR/data_join/data_join_server.py $REMOTE_IP $INDEX \
    $PARTITION_ID $DATA_SOURCE_NAME $DATA_BLOCK_DIR $RAW_DATA_DIR $ROLE -m=$MODE \
    -p=$PORT0 --raw_data_iter=$RAW_DATA_ITER --compressed_type=$COMPRESSED_TYPE \
    --example_joiner=$EXAMPLE_JOINER $EAGER_MODE >>"${data_join_log_dir}/console_${ROLE}.log" \
    2>>"${data_join_log_dir}/error_${ROLE}.log" &
    if [[ $? -eq 0 ]]; then
        echo "data join service start successfully"
        echo "log dir: ${data_join_log_dir}"
        echo "input dir: ${RAW_DATA_DIR}"
        echo "output dir: ${DATA_BLOCK_DIR}"
    else
        echo "data join service start failed"
    fi
}


local_data_center_server_start() {
    mkdir_data_center_log_dir
    nohup python $CURRENT_DIR/data_center/local_data_center_server.py -d=$DATA_NUM_EPOCH $LEADER_DATA_BLOCK_DIR $FOLLOWER_DATA_BLOCK_DIR $DATA_CENTER_PORT >> "${data_center_log_dir}/console.log" 2>>"${data_center_log_dir}/error.log" &
    if [[ $? -eq 0 ]]; then
        echo "local data center service start successfully"
    else
        echo "local data center service start failed"
    fi
}

distribute_data_center_server_start() {
    mkdir_data_center_log_dir
    nohup python $CURRENT_DIR/data_center/distribute_data_center_server.py $train_data_start $train_data_end $data_source_name -d=$data_num_epoch >> "${data_center_log_dir}/console.log" 2>>"${data_center_log_dir}/error.log" &
    if [[ $? -eq 0 ]]; then
        echo "distribute data center service start successfully"
    else
        echo "distribute data center service start failed"
    fi
}


register_uuid_server_start() {
    mkdir_data_join_log_dir
    nohup python $CURRENT_DIR/prepare_register_uuid.py >> "${data_join_log_dir}/console.log" 2>>"${data_join_log_dir}/error.log" &
    if [[ $? -eq 0 ]]; then
        echo "register uuid start successfully"
    else
        echo "register uuid start failed"
    fi
}

case "$1" in
    join)
        if [[ "$MODE" == "distribute" ]]; then
            start_http_server
            register_uuid_server_start
            data_join_server_start
        else
            data_join_server_start
        fi
        ;;
    center)
        if [[ "$MODE" == "distribute" ]]; then
            start_http_server
            distribute_data_center_server_start
        else
            local_data_center_server_start
        fi
        ;;

    *)
        echo "usage: $0 {join|center}"
        exit -1
esac

