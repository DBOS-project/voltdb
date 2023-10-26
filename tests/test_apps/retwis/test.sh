#!/usr/bin/env bash
# if [ $# -eq 0 ];
# then
#     help
#     exit 0
# fi

if [ -z "$PERF_PATH" ]; then
    PERF_PATH="/lib/linux-tools/5.15.0-87-generic/perf"
fi

pid=$(ps u | grep -i voltdb | grep -v grep | awk '{print $2}')
sleep_time=5

echo "${0}: Benchmarking pid-$pid starting in $sleep_time s"
data_file="$(pwd)/out/perf/perf-$pid.data"
PERF_MAP_DIR=/home/zxjcarrot/Workspace/networking/perf-map-agent/

sleep $sleep_time
sudo -A $PERF_PATH record -F 99 -o $data_file -g -a -p $pid # 5 mins max
# $PERF_MAP_DIR/bin/create-java-perf-map.sh $1
# /home/zxjcarrot/Workspace/networking/perf-map-agent/bin/perf-java-flames ${1} 2>&1 > test.out