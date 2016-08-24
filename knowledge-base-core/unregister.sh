#!/usr/bin/env bash

curl -X DELETE -D - -w '\n' http://localhost:9130/_/proxy/tenants/our/modules/knowledge-base-core
curl -X DELETE -D - -w '\n' http://localhost:9130/_/discovery/modules/knowledge-base-core/localhost-9401
curl -X DELETE -D - -w '\n' http://localhost:9130/_/proxy/modules/knowledge-base-core

