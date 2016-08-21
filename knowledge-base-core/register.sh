#!/usr/bin/env bash

curl -w '\n' -X POST -D -   \
     -H "Content-type: application/json"   \
     -d @./registration/discovery.json \
     http://localhost:9130/_/discovery/modules

curl -w '\n' -D - -s \
     -X POST \
     -H "Content-type: application/json" \
     -d @./registration/proxy.json  \
     http://localhost:9130/_/proxy/modules

curl -w '\n' -X POST -D - \
     -H "Content-type: application/json" \
     -d @./registration/activate.json  \
     http://localhost:9130/_/proxy/tenants/our/modules

