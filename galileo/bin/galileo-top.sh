#!/bin/bash
date +"%s" > /s/bach/j/under/mroseliu/Documents/systemPerf/$HOSTNAME.log
date >> /s/bach/j/under/mroseliu/Documents/systemPerf/$HOSTNAME.log
top -u mroseliu -b -n 30000 -d 0.01 | grep java >> /s/bach/j/under/mroseliu/Documents/systemPerf/$HOSTNAME.log
date +"%s" >> /s/bach/j/under/mroseliu/Documents/systemPerf/$HOSTNAME.log
