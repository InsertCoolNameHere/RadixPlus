#!/bin/bash
#pass file name as first arg i.e /s/bach/j/under/mroseliu/NSF_Time_Series/Raptor/galileo/config/network/hostnames
date
while IFS='' read -r line || [[ -n "$line" ]]; do
    
    ssh $line "killall -9 java"&

done < "$1"
