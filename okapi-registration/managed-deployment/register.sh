#!/usr/bin/env bash

deployment_descriptor_filename=${1:-target/DeploymentDescriptor.json}
okapi_proxy_address=${2:-http://localhost:9130}
tenant_id=${3:-demo_tenant}

deployment_json=$(cat ./${deployment_descriptor_filename})

curl -w '\n' -X POST -D -   \
     -H "Content-type: application/json"   \
     -d "${deployment_json}" \
     "${okapi_proxy_address}/_/deployment/modules"

curl -w '\n' -D - -s \
     -X POST \
     -H "Content-type: application/json" \
     -d @./target/ModuleDescriptor.json  \
     "${okapi_proxy_address}/_/proxy/modules"

curl -w '\n' -X POST -D - \
     -H "Content-type: application/json" \
     -d @./target/Activate.json  \
     "${okapi_proxy_address}/_/proxy/tenants/${tenant_id}/modules"
