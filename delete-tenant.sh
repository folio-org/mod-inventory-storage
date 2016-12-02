#!/usr/bin/env bash

tenant=${1:-test-tenant}

curl -X DELETE -D - -w '\n' http://localhost:9130/_/proxy/tenants/${tenant}

