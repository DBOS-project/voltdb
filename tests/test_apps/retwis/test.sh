#!/usr/bin/env bash
# if [ $# -eq 0 ];
# then
#     help
#     exit 0
# fi

if [ -z "$PERF_PATH" ]; then
    PERF_PATH="/lib/linux-tools-5.15.0-88/perf"
fi

if [ -z "$APP_PATH" ]; then
    APP_PATH="/home/zxjcarrot/Workspace/networking/retwis/out/perf"
fi

PERF_MAP_DIR=/home/zxjcarrot/Workspace/networking/perf-map-agent/

# $PERF_MAP_DIR/bin/create-java-perf-map.sh $1
# /home/zxjcarrot/Workspace/networking/perf-map-agent/bin/perf-java-flames ${1} 2>&1 > test.out

function start() {
    pid=$(ps u | grep -i voltdb | grep -v grep | awk '{print $2}')
    echo "${0}: Benchmarking pid-$pid starting in $1 s"
    sleep $1

    data_file=$APP_PATH/perf-$pid.data
    sudo -A $PERF_PATH record -F 99 -o $data_file -g -a -p $pid # 5 mins max
}

function stop() {
    volt_pid=$(ps u | grep -i voltdb | grep -v grep | awk '{print $2}')
    echo "${0}: Stopping perf for pid-$volt_pid"
    if pgrep -x perf > /dev/null
    then
        # Kill all running perf processes
        sudo -A pkill -x perf
        /home/zxjcarrot/Workspace/networking/perf-map-agent/bin/create-java-perf-map.sh $volt_pid
        PERF_FILE=$APP_PATH/perf-$volt_pid.data
        SCRIPT_OUT_FILE=$APP_PATH/$volt_pid.perf
        sudo -A $PERF_PATH script -i $PERF_FILE > $SCRIPT_OUT_FILE
    else
        echo "No perf process found."
    fi
}

if [ $# -eq 0 ];
then
    help
    exit 0
elif [ $# -eq 1 ];
then
    ${1}
else
    ${1} ${2}
fi