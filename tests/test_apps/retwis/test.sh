if [ $# -eq 0 ];
then
    help
    exit 0
fi


echo "${0}: Benchmarking"
./run.sh "warmup"
echo "Warmed up!"
/home/zxjcarrot/Workspace/networking/perf-map-agent/bin/perf-java-flames ${1} 2>&1 > test.out &
pid=$!
sleep 0.3
./run.sh "async"
wait $pid