#!/bin/bash
#pass file name as first arg i.e /s/bach/j/under/mroseliu/NSF_Time_Series/Raptor/galileo/config/network/hostnames
date
while IFS='' read -r line || [[ -n "$line" ]]; do
    
    ssh $line "sh /s/bach/j/under/mroseliu/NSF_Time_Series/Radix/galileo/bin/galileo-top.sh;"&
echo "Running perf on $line"
done < "$1"
