#!/usr/bin/env bash

port=${1:-9407}
config_path=${2:-"$(pwd)/demo-postgres-conf.json"}
tenant_id=${3:-demo_tenant}

rm output.log

echo "Building"

mvn clean install -DskipTests

java -jar target/inventory-storage-fat.jar db_connection=${config_path} -Dhttp.port=${port} 1>output.log 2>output.log &

echo "Waiting for inventory storage module to start"

until $(curl --output /dev/null --silent --get --fail -H "X-Okapi-Tenant: ${tenant_id}"  http://localhost:${port}/_/tenant); do
    printf '.'
    sleep 1
done

echo

echo "Running"

echo "Initialising Tenant: ${tenant_id}"

curl -w '\n' -X POST -D -   \
     -H "Content-type: application/json"   \
     -H "Accept: */*"   \
     -H "X-Okapi-Tenant: ${tenant_id}" \
     http://localhost:${port}/_/tenant

#tail -F output.log

