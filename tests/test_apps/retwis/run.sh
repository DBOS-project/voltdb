#!/usr/bin/env bash
APPNAME="retwis"

echo '-=-=-=-=- test/test_apps/retwis -=-=-=-=-'

# find voltdb binaries
if [ -e ../../../bin/voltdb ]; then
    # assume this is the tests/test_apps/retwis directory
    VOLTDB_BIN="$(dirname $(dirname $(dirname $(pwd))))/bin"
elif [ -n "$(which voltdb 2> /dev/null)" ]; then
    # assume we're using voltdb from the path
    VOLTDB_BIN=$(dirname "$(which voltdb)")
else
    echo "Unable to find VoltDB installation."
    echo "Please add VoltDB's bin directory to your path."
    exit -1
fi

# call script to set up paths, including
# java classpaths and binary paths
source $VOLTDB_BIN/voltenv

VOLTDB="$VOLTDB_BIN/voltdb"
LOG4J="$VOLTDB_VOLTDB/log4j.xml"

# leader host for startup purposes only
# (once running, all nodes are the same -- no leaders)
STARTUPLEADERHOST="localhost"

# list of cluster nodes separated by commas in host:[port] format
SERVERS="localhost"

# remove binaries, logs, runtime artifacts, etc... but keep the jars
function clean() {
    rm -rf voltdbroot log procedures/retwis/*.class client/retwis/*.class *.log
}

# remove everything from "clean" as well as the jarfiles
function cleanall() {
    clean
    rm -rf retwis-procs.jar retwis-client.jar
}

function print_time() {
    timestamp=$(date +"%H:%M:%S")
    echo "$timestamp :: $1"
}

# compile the source code for procedures and the client into jarfiles
function jars() {
    print_time "Performing jars"
    # compile java source
    javac -classpath $APPCLASSPATH procedures/retwis/*.java
    javac -classpath $CLIENTCLASSPATH client/retwis/*.java
    # build procedure and client jars
    jar cf $APPNAME-procs.jar -C procedures retwis
    jar cf $APPNAME-client.jar -C client retwis
    # remove compiled .class files
    rm -rf procedures/retwis/*.class client/retwis/*.class
}

# compile the procedure and client jarfiles if they don't exist
function jars-ifneeded() {
    if [ ! -e $APPNAME-procs.jar ] || [ ! -e $APPNAME-client.jar ]; then
        jars;
    fi
}

# Init to directory voltdbroot
function voltinit-ifneeded() {
    voltdb init --force
}

# run the voltdb server locally
function server() {
    jars-ifneeded
    voltinit-ifneeded
    voltdb start -H $STARTUPLEADERHOST
}

# load schema and procedures
function init() {
    jars
    sqlcmd < ddl.sql
    run -t "async" -a "init"
}

version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
add_open=
if [[ $version == 11.0* ]] || [[ $version == 17.0* ]] ; then
        add_open="--add-opens java.base/sun.nio.ch=ALL-UNNAMED"
fi

function run() {
    print_time "Running benchmark with params- $@"
    java -classpath $APPNAME-client.jar:$APPNAME-procs.jar:$APPCLASSPATH\
        -Dlog4j.configuration=file://$LOG4J\
        retwis.Benchmark $@
}

function sync() {
    init
    run -t "sync" $@
}
function async() {
    init
    run -t "async" $@
}

function warmup() {
    run -t "async" -a "warmup" $@
}

function remote_init() {
    print_time "Performing remote init"
    res=$(curl -X POST "http://18.26.2.124:3001/?init_volt=1&init_db=1&record_perf=1&app=retwis&id=$1&num_cores=$3&warmup_time=$2" -s)
    print_time "Remote returned- $res"
}

function stop_perf() {
    print_time "Stopping perf on remote"
    res=$(curl -X POST "http://18.26.2.124:3001/stop-perf?id=$1" -s)
    print_time "Remote returned- $res"
}

function start_sp() {
    cd /home/zxjcarrot/Workspace/networking/voltdb/
    sleep 1
    # Spin up MP Site SP process
    nohup voltdb start --procedureprocess --vmid=0 --vmisolation=TCP --vmpvaccel --vmisolationtcpport=3030 --vmisolationtcphost=localhost  > log_sp.txt 2>&1 &
    sp_pid=$!
    echo "Spun up MP-SP process with PID $sp_pid"

    firstPort=3030
    for ((i = 1; i <= $1; i++))
    do
        thisPort=$((firstPort + i))
        nohup voltdb start --procedureprocess --vmid=$i --vmisolation=TCP --vmpvaccel --vmisolationtcpport=$thisPort --vmisolationtcphost=localhost  > log_sp.txt 2>&1 &
        sp_pid=$!
        echo "Spun up SP process $i with PID $sp_pid"
    done
}

function remote_bench() {
    id=$RANDOM
    warmup_time=0
    num_cores=1
    jars
    start_sp $num_cores &
    remote_init $id $warmup_time $num_cores
    print_time "Sleeping for $warmup_time s"
    sleep $warmup_time
    print_time "Woken up from sleep"
    run $@
    stop_perf $id
}

function remote_async() {
    remote_bench -t "async" $@
}

function remote_sync() {
    remote_bench -t "sync" $@
}

function help() {
    echo "
Usage: ./run.sh target...

General targets:
        help | clean | cleanall | jars | jars-ifneeded |
        init | voltinit-ifneeded | server

Benchmark targets:
        client
"
}

# Run the targets passed on the command line

if [ $# -eq 0 ];
then
    help
    exit 0
else
    echo "${0}: Performing ${1} with parameter ${@}..."
    $@
fi
