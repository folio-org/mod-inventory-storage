#!/usr/bin/env bash

catalogueport=${1:-9402}
kbport=${2:-9401}

createinstanceoutput=$(curl -w '\n' -X POST -D - \
     -H "Content-type: application/json" \
     -d @./nod-instance.json \
     http://localhost:${kbport}/knowledge-base/instance)

instancelocation=$(echo "${createinstanceoutput}" | tr -d '\r' | sed -En 's/^Location: (.*)/\1/p')

createitemjson=$(cat ./nod-item.json)

createitemjson="${createitemjson/InstanceLocationHere/$instancelocation}"

curl -w '\n' -X POST -D - \
     -H "Content-type: application/json" \
     -d "$createitemjson" \
     http://localhost:${catalogueport}/catalogue/item

#--------------------------

createinstanceoutput=$(curl -w '\n' -X POST -D - \
     -H "Content-type: application/json" \
     -d @./uprooted-instance.json \
     http://localhost:${kbport}/knowledge-base/instance)

instancelocation=$(echo "${createinstanceoutput}" | tr -d '\r' | sed -En 's/^Location: (.*)/\1/p')

createitemjson=$(cat ./uprooted-item.json)

createitemjson="${createitemjson/InstanceLocationHere/$instancelocation}"

curl -w '\n' -X POST -D - \
     -H "Content-type: application/json" \
     -d "$createitemjson" \
     http://localhost:${catalogueport}/catalogue/item