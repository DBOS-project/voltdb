#!/usr/bin/env bash
if [ $# -eq 0 ];
then
    help
    exit 0
fi


echo "${0}: Benchmarking"

/home/zxjcarrot/Workspace/networking/perf-map-agent/bin/perf-java-flames ${1} 2>&1 > test.out