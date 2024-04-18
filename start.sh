#!/bin/bash
source ../.profile
echo "Start.sh with $1 sites per host"

deployment_xml="<?xml version=\"1.0\"?>\
<deployment>\
    <cluster hostcount=\"1\" sitesperhost=\"$1\" />\
</deployment>"

temp_file=$(mktemp)
echo $deployment_xml > "$temp_file"

voltdb init --force --config="$temp_file"
echo "Initialized voltdb with deployment.xml"

sleep 1
# Spin up MP Site SP process
voltdb start --procedureprocess --vmid=0 --vmisolation=TCP --vmpvaccel --vmisolationtcpport=3030 --vmisolationtcphost=localhost  > log_sp_0.txt 2>&1 &
sp_pid=$!
echo "Spun up MP-SP process with PID $sp_pid"

firstPort=3030
for ((i = 1; i <= $1; i++))
do
    thisPort=$((firstPort + i))
    voltdb start --procedureprocess --vmid=$i --vmisolation=TCP --vmpvaccel --vmisolationtcpport=$thisPort --vmisolationtcphost=localhost  > log_sp_${i}.txt 2>&1 &
    sp_pid=$!
    echo "Spun up SP process $i with PID $sp_pid"
done
voltdb start --vmisolation=TCP --vmpvaccel --vmisolationtcpport=3030 > log_db.txt 2>&1 &
db_pid=$!
echo "Started DB process in PID $db_pid"

# nohup voltdb start --procedureprocess --vmid=0 --vmisolation=TCP --vmpvaccel --vmisolationtcpport=3030  > log_sp.txt 2>&1 &
# sp_pid=$!
# nohup voltdb start --procedureprocess --vmid=1 --vmisolation=TCP --vmpvaccel --vmisolationtcpport=3031  > log_sp2.txt 2>&1 &
# sp2_pid=$!

# echo "Spun up sp processes with PIDs $sp_pid and $sp2_pid"
