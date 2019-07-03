#!/usr/bin/env bash
################################################################################
# populate-data ssh to every node and create a data file for ingestion testing
################################################################################

class="dev.DummyDataGenerator"
logfile="/s/bach/j/under/mroseliu/dataGen/populate-data.log"

java -classpath "${GALILEO_HOME}"/lib/\* ${class} $1 &>> "${logfile}" &
