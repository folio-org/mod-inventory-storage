#!/usr/bin/env bash

catalogue_direct_address=${1:-http://localhost:9402/catalogue}
catalogue_instance_id=${2:-localhost-9402}
okapi_proxy_address=${3:-http://localhost:9130}

discovery_json=$(cat ./registration/discovery.json)

discovery_json="${discovery_json/directaddresshere/$catalogue_direct_address}"
discovery_json="${discovery_json/instanceidhere/$catalogue_instance_id}"

echo "${discovery_json}"

curl -w '\n' -X POST -D -   \
     -H "Content-type: application/json"   \
     -d "${discovery_json}" \
     "${okapi_proxy_address}/_/discovery/modules"

curl -w '\n' -D - -s \
     -X POST \
     -H "Content-type: application/json" \
     -d @./registration/proxy.json  \
     "${okapi_proxy_address}/_/proxy/modules"

curl -w '\n' -X POST -D - \
     -H "Content-type: application/json" \
     -d @./registration/activate.json  \
     "${okapi_proxy_address}/_/proxy/tenants/our/modules"

