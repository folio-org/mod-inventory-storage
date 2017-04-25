#!/usr/bin/env bash

tenant_id=${1:-demo_tenant}
storage_type=${2:-okapi}
storage_location=${3:-''}
inventory_root_address=${4:-http://localhost:9403/inventory}

gradle fatJar

rm output.log

echo "Tenant ID: ${tenant_id}"
echo "Storage Type: ${storage_type}"
echo "Storage Location: ${storage_location}"
echo "Inventory Root Address: ${inventory_root_address}"

java -Dorg.folio.metadata.inventory.storage.type="${storage_type}" \
  -Dorg.folio.metadata.inventory.storage.location="${storage_location}" \
 -jar build/libs/inventory.jar 1>output.log 2>output.log &

printf 'Waiting for inventory module to start\n'

until $(curl --output /dev/null --silent --get --fail -H "X-Okapi-Tenant: ${tenant_id}" ${inventory_root_address}/items); do
    printf '.'
    sleep 1
done

printf '\n'

#tail -F output.log

