#! /bin/bash
# A simple script to make lots of instance data for testing things with large
# datasets.
# Takes the records in sample-data, modifies them in some way, and posts to
# mod-inventory-storage.
#
# Assumes a running system, and reference data already loaded
# For a simple example, using embedded postgres:
#
#  # start Okapi in one console window
#  cd .../okapi
#  java -jar okapi-core/target/okapi-core-fat.jar dev
#
#  Run all this in another console window
#  cd .../mod-inventory-storage
#
#  # Start mod-inventory-storage
#  ./start-managed-demo.sh embedded
#  # See that it ends with "201 Created"
#
#  # Load reference data
#  ./import-reference-data.sh
#  ./import-sample-data.sh
#  # See that we have some data. Should find one record
#  curl -H X-Okapi-Tenant:demo_tenant -D - http://localhost:9130/instance-storage/instances?query=title=genug
#
#  # Run this script
#  ./make-dummy-data.sh 1000 # or as many as you want. 100 is enough to see it working, millions for perf tests
#  curl -H X-Okapi-Tenant:demo_tenant -D - http://localhost:9130/instance-storage/instances?query=title=genug
#  # See that we have a number of records
#
# You can also use an external database, but you need to create and initialize it
# before. I have found the embedded to work fine for quick tests.
#
# If you want to look in the database, you can try something like
#   psql -h localhost -p 6000 -U username postgres
# it will ask for a password. "password" is a good guess :-)
# Then switch to the right role
#  SET ROLE demo_tenant_mod_inventory_storage;
# and you can look at the tables:
#  select count(*) from instance;
#  select * from instance where to_tsvector('english', instance.jsonb->>'title') @@ to_tsquery('english','genug') LIMIT 2;
# and analyze queries
#  \timing on
# explain analyze select * from instance where to_tsvector('english', instance.jsonb->>'title') @@ to_tsquery('english','gen:*') LIMIT 10
#
# When done
#  ./stop-managed-demo.sh
#  Ctrl-C in the Okapi window


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
      sed "/title/s/\",/ $N\",/" |
      sed "/hrid/s/\",/-$N\",/"  > $T
    curl -s \
      -d@/tmp/instance.json \
      -H'X-Okapi-Tenant:demo_tenant' -HContent-Type:application/json \
      http://localhost:9130/instance-storage/instances >/tmp/instance.curl.out
    echo
    N=`expr $N + 1`
    if [ $N -ge $LIM ]
    then
      echo $LIM records created
      exit 0
    fi
  done
done
