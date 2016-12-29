#!/usr/bin/env bash

#Run this from the root directory of the Okapi source

okapi_proxy_address=${1:-"http://localhost:9130"}
storage=${2:-"memory"}

if [ $# == 0 ] ; then
  echo "Using default parameters"
  echo "Okapi Address: ${okapi_proxy_address}"
  echo "Usage: ./start-okapi.sh  [okapi proxy address] from the Okapi source root directory"
fi

echo "Packaging Okapi Core"

mvn package --quiet -Dmaven.test.skip=true

if [ "${storage}" = "postgres" ]; then
  echo "Running Okapi Core using Postgres storage"
  java  \
    -Dokapiurl="${okapi_proxy_address}" \
    -Dloglevel=DEBUG \
    -Dstorage=postgres \
    -Dpostgres_db_init=1 \
    -jar ./okapi-core/target/okapi-core-fat.jar dev

elif [ "${storage}" = "memory" ]; then
  echo "Running Okapi Core using in-memory storage"
  java \
    -Dokapiurl="${okapi_proxy_address}" \
    -Dloglevel=DEBUG \
    -jar ./okapi-core/target/okapi-core-fat.jar dev

else
  echo "Unknown storage mechanism: ${storage}"
  exit 1
fi
