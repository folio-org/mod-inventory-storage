#!/usr/bin/env bash

port=${1:-9407}
config_path=${2:-"$(pwd)/postgres-conf.json"}
tenant_id=${3:-demo_tenant}

rm output.log

echo "Building"

./setup-test-db.sh

mvn clean install

./setup-demo-db.sh

java -jar target/inventory-storage-fat.jar db_connection=${config_path} -Dhttp.port=${port} 1>output.log 2>output.log &

echo 'Waiting for inventory storage module to start'

until $(curl --output /dev/null --silent --get --fail -H "X-Okapi-Tenant: ${tenant_id}"  http://localhost:${port}/item-storage/items); do
    printf '.'
    sleep 1
done

echo

echo 'Running'

#tail -F output.log

