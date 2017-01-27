#!/usr/bin/env bash

item_storage_address=${1:-http://localhost:9130/item-storage/items}
tenant=${3:-demo_tenant}

create_item_json=$(cat ./uprooted-item.json)

curl -w '\n' -X POST -D - \
     -H "Content-type: application/json" \
     -H "X-Okapi-Tenant: ${tenant}" \
     -d "${create_item_json}" \
     "${item_storage_address}"