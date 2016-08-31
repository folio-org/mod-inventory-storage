#!/usr/bin/env bash

CreateInstanceOutput=$(curl -w '\n' -X POST -D - \
     -H "Content-type: application/json" \
     -d @./nod-instance.json \
     http://localhost:9401/knowledge-base/instance)

echo "$CreateInstanceOutput"

InstanceLocation=$(echo "$CreateInstanceOutput" | tr -d '\r' | sed -En 's/^Location: (.*)/\1/p')

echo "$InstanceLocation"

CreateItemJson=$(cat ./nod-item.json)

echo "$CreateItemJson"

CreateItemJson="${CreateItemJson/InstanceLocationHere/$InstanceLocation}"

echo "$CreateItemJson"

curl -w '\n' -X POST -D - \
     -H "Content-type: application/json" \
     -d "$CreateItemJson" \
     http://localhost:9402/catalogue/item

#--------------------------

CreateInstanceOutput=$(curl -w '\n' -X POST -D - \
     -H "Content-type: application/json" \
     -d @./uprooted-instance.json \
     http://localhost:9401/knowledge-base/instance)

echo "$CreateInstanceOutput"

InstanceLocation=$(echo "$CreateInstanceOutput" | tr -d '\r' | sed -En 's/^Location: (.*)/\1/p')

echo "$InstanceLocation"

CreateItemJson=$(cat ./uprooted-item.json)

echo "$CreateItemJson"

CreateItemJson="${CreateItemJson/InstanceLocationHere/$InstanceLocation}"

echo "$CreateItemJson"

curl -w '\n' -X POST -D - \
     -H "Content-type: application/json" \
     -d "$CreateItemJson" \
     http://localhost:9402/catalogue/item