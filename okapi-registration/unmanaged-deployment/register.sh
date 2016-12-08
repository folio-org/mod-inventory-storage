#!/usr/bin/env bash

module_direct_address=${1}
module_instance_id=${2}
module_id=${3}
okapi_proxy_address=${4:-http://localhost:9130}
tenant_id=${5:-demo_tenant}

discovery_json=$(cat ./registration/discovery.json)

discovery_json="${discovery_json/moduleidhere/$module_id}"
discovery_json="${discovery_json/directaddresshere/$module_direct_address}"
discovery_json="${discovery_json/instanceidhere/$module_instance_id}"

curl -w '\n' -X POST -D -   \
     -H "Content-type: application/json"   \
     -d "${discovery_json}" \
     "${okapi_proxy_address}/_/discovery/modules"

curl -w '\n' -D - -s \
     -X POST \
     -H "Content-type: application/json" \
     -d @./registration/proxy.json  \
     "${okapi_proxy_address}/_/proxy/modules"

activate_json=$(cat ./registration/activate.json)
activate_json="${activate_json/moduleidhere/$module_id}"

curl -w '\n' -X POST -D - \
     -H "Content-type: application/json" \
     -d "${activate_json}"  \
     "${okapi_proxy_address}/_/proxy/tenants/${tenant_id}/modules"

