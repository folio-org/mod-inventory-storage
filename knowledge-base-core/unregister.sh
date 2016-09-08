#!/usr/bin/env bash

port=${1:-9401}

curl -X DELETE -D - -w '\n' http://localhost:9130/_/proxy/tenants/our/modules/knowledge-base-core
curl -X DELETE -D - -w '\n' http://localhost:9130/_/discovery/modules/knowledge-base-core/localhost-${port}
curl -X DELETE -D - -w '\n' http://localhost:9130/_/proxy/modules/knowledge-base-core

