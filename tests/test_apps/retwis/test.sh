#!/usr/bin/env bash
if [ $# -eq 0 ];
then
    help
    exit 0
fi


echo "${0}: Benchmarking"
data_file="$(pwd)/out/perf/perf-${1}.data"
PERF_MAP_DIR=/home/zxjcarrot/Workspace/networking/perf-map-agent/

/lib/linux-tools/5.15.0-58-generic/perf record -F 99 -o $data_file -g -a -p $1 -- sleep 300 # 5 mins max
# $PERF_MAP_DIR/bin/create-java-perf-map.sh $1
# /home/zxjcarrot/Workspace/networking/perf-map-agent/bin/perf-java-flames ${1} 2>&1 > test.out