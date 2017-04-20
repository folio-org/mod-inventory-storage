#!/usr/bin/env bash

module_id=${1}
okapi_proxy_address=${2:-http://localhost:9130}
tenant_id=${3:-demo_tenant}
deployment_descriptor_filename=${4:-DeploymentDescriptor.json}

deployment_json=$(cat ./${deployment_descriptor_filename})

curl -w '\n' -X POST -D -   \
     -H "Content-type: application/json"   \
     -d "${deployment_json}" \
     "${okapi_proxy_address}/_/deployment/modules"

curl -w '\n' -D - -s \
     -X POST \
     -H "Content-type: application/json" \
     -d @./ModuleDescriptor.json  \
     "${okapi_proxy_address}/_/proxy/modules"

activate_json=$(cat ./registration/activate.json)
activate_json="${activate_json/moduleidhere/$module_id}"

curl -w '\n' -X POST -D - \
     -H "Content-type: application/json" \
     -d "${activate_json}"  \
     "${okapi_proxy_address}/_/proxy/tenants/${tenant_id}/modules"
