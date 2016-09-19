#!/usr/bin/env bash

instance_id=${1:-localhost-9401}

curl -X DELETE -D - -w '\n' "http://localhost:9130/_/proxy/tenants/our/modules/knowledge-base-core"
curl -X DELETE -D - -w '\n' "http://localhost:9130/_/discovery/modules/knowledge-base-core/${instance_id}"
curl -X DELETE -D - -w '\n' "http://localhost:9130/_/proxy/modules/knowledge-base-core"
