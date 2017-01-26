#!/usr/bin/env bash

instance_id=${1}
module_id=${2}
okapi_proxy_address=${3:-http://localhost:9130}
tenant_id=${4:-demo_tenant}

curl -X DELETE -D - -w '\n' "http://localhost:9130/_/proxy/tenants/${tenant_id}/modules/${module_id}"
curl -X DELETE -D - -w '\n' "http://localhost:9130/_/deployment/modules/${instance_id}"
curl -X DELETE -D - -w '\n' "http://localhost:9130/_/proxy/modules/${module_id}"
