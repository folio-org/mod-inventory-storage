#!/usr/bin/env bash

tenant=${1:-demo_tenant}
item_storage_address=http://localhost:9130/item-storage/items
instance_storage_address=http://localhost:9130/instance-storage/instances
holdings_storage_address=http://localhost:9130/holdings-storage/holdings

curl -w '\n' -X DELETE -D - \
     -H "X-Okapi-Tenant: ${tenant}" \
     "${item_storage_address}"

curl -w '\n' -X DELETE -D - \
     -H "X-Okapi-Tenant: ${tenant}" \
     "${holdings_storage_address}"

curl -w '\n' -X DELETE -D - \
     -H "X-Okapi-Tenant: ${tenant}" \
     "${instance_storage_address}"









