#!/usr/bin/env bash
APPNAME="breakdown"

echo '-=-=-=-=- test/test_apps/breakdown -=-=-=-=-'

# find voltdb binaries
if [ -e ../../../bin/voltdb ]; then
    # assume this is the tests/test_apps/breakdown directory
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

# leader host for startup purposes only
# (once running, all nodes are the same -- no leaders)
STARTUPLEADERHOST="localhost"

# list of cluster nodes separated by commas in host:[port] format
SERVERS="localhost"

# remove binaries, logs, runtime artifacts, etc... but keep the jars
function clean() {
    rm -rf voltdbroot log procedures/breakdown/*.class client/breakdown/*.class *.log
}

# remove everything from "clean" as well as the jarfiles
function cleanall() {
    clean
    rm -rf breakdown-procs.jar breakdown-client.jar
}

# compile the source code for procedures and the client into jarfiles
function jars() {
    # compile java source
    javac -classpath $APPCLASSPATH procedures/breakdown/*.java
    javac -classpath $CLIENTCLASSPATH client/breakdown/*.java
    # build procedure and client jars
    jar cf $APPNAME-procs.jar -C procedures breakdown
    jar cf $APPNAME-client.jar -C client breakdown
    # remove compiled .class files
    rm -rf procedures/breakdown/*.class client/breakdown/*.class
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
    jars-ifneeded
    sqlcmd < ddl.sql
}

version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
add_open=
if [[ $version == 11.0* ]] || [[ $version == 17.0* ]] ; then
        add_open="--add-opens java.base/sun.nio.ch=ALL-UNNAMED"
fi

function warmup() {
    jars
    sqlcmd < ddl.sql
    java -classpath $APPNAME-client.jar:$APPNAME-procs.jar:$APPCLASSPATH breakdown.Benchmark warmup
}

function test() {
    java -classpath $APPNAME-client.jar:$APPNAME-procs.jar:$APPCLASSPATH breakdown.Benchmark test
}

function benchmark() {
    warmup
    /home/zxjcarrot/Workspace/networking/perf-map-agent/bin/perf-java-report-stack 
}

function all() {
    warmup
    test
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
fi


echo "${0}: Performing ${1}..."
${1}
