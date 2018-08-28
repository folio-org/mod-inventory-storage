#! /bin/bash
# A simple script to make lots of instance data for testing things with large
# datasets.
# Takes the records in sample-data, modifies them in some way, and posts to
# mod-inventory-storage.
# Assumes a running system, and reference data already loaded

LIM=${1:-100}
N=1
DIR=sample-data/instances
T=/tmp/instance.json
while true
do
  for R in $DIR/*
  do
    echo $N: $R
    cat $R |
      grep -v '"id"' |
      sed "/title/s/\",/ $N\",/" > $T
    curl -s \
      -d@/tmp/instance.json \
      -H'X-Okapi-Tenant:demo_tenant' -HContent-Type:application/json \
      http://localhost:9130/instance-storage/instances >/dev/null
    echo
    N=`expr $N + 1`
    if [ $N -ge $LIM ]
    then
      echo $LIM records created
      exit 0
    fi
  done
done