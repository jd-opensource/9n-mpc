CURDIR=$(cd `dirname $0`; pwd)

LOG_DIR="$CURDIR/logs/"
if [ ! -d $LOG_DIR ]; then
    mkdir -p $LOG_DIR
fi

rm -f ${LOG_DIR}/*

nohup ./output/bin/fl_server --flagfile=../../conf/Coordinator/conf/fl_server.gflags >${LOG_DIR}/nohup.out 2>&1 &
