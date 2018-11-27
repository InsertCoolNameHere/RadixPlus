#!/bin/bash
#pass file name as first arg i.e /s/bach/j/under/mroseliu/NSF_Time_Series/Raptor/galileo/config/network/hostnames
while IFS='' read -r line || [[ -n "$line" ]]; do
    
    echo '/tmp/dummy.csv' | nc $line 42070 &
done < "$1"
