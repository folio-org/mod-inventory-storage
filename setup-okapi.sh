#!/usr/bin/env bash

curl -w '\n' -X POST -D - \
     -H "Content-type: application/json" \
     -d @./okapi-setup/sample-tenant.json  \
     http://localhost:9130/_/proxy/tenants

