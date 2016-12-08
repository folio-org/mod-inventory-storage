#!/usr/bin/env bash

curl -w '\n' -D -  \
     -H "X-Okapi-Tenant: demo_tenant" \
     http://localhost:9130/catalogue
