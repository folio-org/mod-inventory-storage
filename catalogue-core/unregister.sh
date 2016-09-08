#!/usr/bin/env bash

port=${1:-9402}

curl -X DELETE -D - -w '\n' http://localhost:9130/_/proxy/tenants/our/modules/catalogue-core
curl -X DELETE -D - -w '\n' http://localhost:9130/_/discovery/modules/catalogue-core/localhost-${port}
curl -X DELETE -D - -w '\n' http://localhost:9130/_/proxy/modules/catalogue-core

