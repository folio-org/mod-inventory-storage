#!/usr/bin/env bash

knowledgebase_root_address=${1:-http://localhost:9130/knowledge-base}

curl -w '\n' -X POST -D - \
     -H "Content-type: application/json" \
     -H "X-Okapi-Tenant: our" \
     -d @./advancing-library-education.json \
     "${knowledgebase_root_address}/instance"

curl -w '\n' -X POST -D - \
     -H "Content-type: application/json" \
     -H "X-Okapi-Tenant: our" \
     -d @./advancing-research-methods.json \
     "${knowledgebase_root_address}/instance"

