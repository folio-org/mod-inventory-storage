#!/usr/bin/env bash

port="9407"
tenant_id="demo_tenant"

curl -w '\n' -X DELETE -D -   \
     -H "Content-type: application/json"   \
     -H "Accept: */*"   \
     -H "X-Okapi-Tenant: ${tenant_id}" \
    http://localhost:${port}/tenant

./stop.sh