#!/usr/bin/env bash

knowledgebase_direct_address=${1:-http://localhost:9401/knowledge-base}
knowledge_base_instance_id=${2:-localhost-9401}
okapi_proxy_address=${3:-http://localhost:9130}

discovery_json=$(cat ./registration/discovery.json)

discovery_json="${discovery_json/directaddresshere/$knowledgebase_direct_address}"
discovery_json="${discovery_json/instanceidhere/$knowledge_base_instance_id}"

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
