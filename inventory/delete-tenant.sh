#!/usr/bin/env bash

tenant_id=${1:-demo_tenant}

curl -X DELETE -D - -w '\n' http://localhost:9130/_/proxy/tenants/${tenant_id}

