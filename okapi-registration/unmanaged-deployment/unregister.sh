#!/usr/bin/env bash

instance_id=${1}
module_id=${2}
tenant=${3:-test-tenant}

curl -X DELETE -D - -w '\n' "http://localhost:9130/_/proxy/tenants/${tenant}/modules/${module_id}"
curl -X DELETE -D - -w '\n' "http://localhost:9130/_/discovery/modules/${module_id}/${instance_id}"
curl -X DELETE -D - -w '\n' "http://localhost:9130/_/proxy/modules/${module_id}"
