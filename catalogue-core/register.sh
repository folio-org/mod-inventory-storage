#!/usr/bin/env bash

port=${1:-9402}

discoveryjson=$(cat ./registration/discovery.json)

#execute twice as need to replace two occurrences
discoveryjson="${discoveryjson/porthere/$port}"
discoveryjson="${discoveryjson/porthere/$port}"

echo "${discoveryjson}"

curl -w '\n' -X POST -D -   \
     -H "Content-type: application/json"   \
     -d "${discoveryjson}" \
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

