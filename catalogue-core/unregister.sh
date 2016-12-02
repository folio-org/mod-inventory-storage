#!/usr/bin/env bash

instance_id=${1:-localhost-9402}
tenant=${2:-test-tenant}

curl -X DELETE -D - -w '\n' "http://localhost:9130/_/proxy/tenants/${tenant}/modules/catalogue-core"
curl -X DELETE -D - -w '\n' "http://localhost:9130/_/discovery/modules/catalogue-core/${instance_id}"
curl -X DELETE -D - -w '\n' "http://localhost:9130/_/proxy/modules/catalogue-core"
