#!/bin/bash
#pass file name as first arg i.e /s/bach/j/under/mroseliu/NSF_Time_Series/Raptor/galileo/config/network/hostnames
while IFS='' read -r line || [[ -n "$line" ]]; do
    
    ssh $line "rm /tmp/dummy.csv;"&
done < "$1"
