#!/usr/bin/env bash

catalogue_root_address=${1:-http://localhost:9130/catalogue}
knowledgebase_root_address=${2:-http://localhost:9130/knowledge-base}

create_instance_output=$(curl -w '\n' -X POST -D - \
     -H "Content-type: application/json" \
     -H "X-Okapi-Tenant: our" \
     -d @./nod-instance.json \
     "${knowledgebase_root_address}/instance")

instance_location=$(echo "${create_instance_output}" | tr -d '\r' | sed -En 's/^Location: (.*)/\1/p')

create_item_json=$(cat ./nod-item.json)

create_item_json="${create_item_json/InstanceLocationHere/$instance_location}"

curl -w '\n' -X POST -D - \
     -H "Content-type: application/json" \
     -H "X-Okapi-Tenant: our" \
     -d "${create_item_json}" \
     "${catalogue_root_address}/item"

#--------------------------

create_instance_output=$(curl -w '\n' -X POST -D - \
     -H "Content-type: application/json" \
     -H "X-Okapi-Tenant: our" \
     -d @./uprooted-instance.json \
     "${knowledgebase_root_address}/instance")

instance_location=$(echo "${create_instance_output}" | tr -d '\r' | sed -En 's/^Location: (.*)/\1/p')

create_item_json=$(cat ./uprooted-item.json)

create_item_json="${create_item_json/InstanceLocationHere/$instance_location}"

curl -w '\n' -X POST -D - \
     -H "Content-type: application/json" \
     -H "X-Okapi-Tenant: our" \
     -d "${create_item_json}" \
     "${catalogue_root_address}/item"
