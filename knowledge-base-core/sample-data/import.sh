#!/usr/bin/env bash

curl -w '\n' -X POST -D - \
     -H "Content-type: application/json" \
     -d @./advancing-library-education.json \
     http://localhost:9401/knowledge-base/instance

curl -w '\n' -X POST -D - \
     -H "Content-type: application/json" \
     -d @./advancing-research-methods.json \
     http://localhost:9401/knowledge-base/instance

