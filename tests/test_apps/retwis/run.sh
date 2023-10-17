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

# compile the source code for procedures and the client into jarfiles
function jars() {
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
}

version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
add_open=
if [[ $version == 11.0* ]] || [[ $version == 17.0* ]] ; then
        add_open="--add-opens java.base/sun.nio.ch=ALL-UNNAMED"
fi

function run_sync() {
    java -classpath $APPNAME-client.jar:$APPNAME-procs.jar:$APPCLASSPATH\
        -Dlog4j.configuration=file://$LOG4J\
        retwis.Benchmark sync $1
}

function run_async() {
    java -classpath $APPNAME-client.jar:$APPNAME-procs.jar:$APPCLASSPATH\
        -Dlog4j.configuration=file://$LOG4J\
        retwis.Benchmark async
}

function sync() {
    init
    run_sync $1
}
function async() {
    init
    run_async
}

function remote_init() {
    res=$(curl -X POST "http://localhost:3001/?init_volt=1&init_db=1&record_perf=1&app=retwis" -s)
    echo "$res"
    sleep 0.2
}

function remote_async() {
    remote_init
    run_async
}

function remote_sync() {
    remote_init
    run_sync $1
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
elif [ $# -eq 1 ];
then
    echo "${0}: Performing ${1}..."
    ${1}
else
    echo "${0}: Performing ${1} with parameter ${2}..."
    ${1} ${2}
fi
