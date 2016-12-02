#!/usr/bin/env bash
curl -w '\n' -D -  \
     -H "X-Okapi-Tenant: test-tenant" \
     http://localhost:9130/knowledge-base

