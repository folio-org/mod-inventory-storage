#!/usr/bin/env bash

port=${1:-9407}
config_path=${2:-"$(pwd)/postgres-conf.json"}

rm output.log

echo "Building"

# Temporarily skip tests until using tenant specific databases 
mvn clean install -DskipTests

java -jar target/inventory-storage-fat.jar db_connection=${config_path} -Dhttp.port=${port} 1>output.log 2>output.log &

echo 'Waiting for inventory storage module to start'

until $(curl --output /dev/null --silent --get --fail -H "X-Okapi-Tenant: demo_tenant"  http://localhost:${port}/item-storage/item); do
    printf '.'
    sleep 1
done

echo

echo 'Running'

#tail -F output.log

