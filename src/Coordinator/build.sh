#!/bin/sh
#Copyright: JD.com (2014)
CUR_PATH=`pwd`
PROJECT_PATH=`dirname $CUR_PATH`
OPT="-j 8 --copt -DHAVE_ZLIB=1 --copt=-g --copt=-Wno-comment --copt=-DRAPIDJSON_HAS_STDSTRING --define with_glog=true"
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
function build_common()
{
    rm -rf output*
    mode=${1:-"opt"}
    execshell "bazel build server/... ${OPT} -c ${mode}"

    bin="./output/bin"
    if [[ ! -d ${bin} ]]
    then
        execshell "mkdir -p ${bin}"
    fi
    src_path="./bazel-bin"
    for src_com in \
        "server/fl_server" "server/fl_client"
    do
        execshell "cp $src_path/$src_com ${bin}"
    done

    commit_id_file=commit_id.txt
    execshell "echo commit_id `git rev-parse HEAD` > $commit_id_file"
}

function build_clean()
{
    execshell "bazel clean"
}

function build_release()
{
    build_common "opt"
}

function build_debug()
{
    build_common "dbg"
}

function usage()
{
    cat <<HELP_END
    sh build.sh
        clean              
        debug             
        release或缺省    
        --help          
HELP_END
}
case $1 in
    clean)
        execshell "build_clean"
    ;;
    release|'')
        execshell "build_clean"
        execshell "build_release"
    ;;
    debug)
        execshell "build_clean"
        execshell "build_debug"
    ;;
    --help|-h|-help|help|*)
        usage
    ;;
esac
exit 0
