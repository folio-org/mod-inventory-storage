#!/usr/bin/env bash

tenant=${1:-demo_tenant}

curl -w '\n' -D -  \
     -H "X-Okapi-Tenant: ${tenant}" \
     http://localhost:9130/knowledge-base

