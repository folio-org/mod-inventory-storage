#!/usr/bin/env bash

okapi_proxy_address=${1:-http://localhost:9130}

knowledgebase_proxied_address="${okapi_proxy_address}/knowledge-base"
catalogue_proxied_address="${okapi_proxy_address}/catalogue"

./start-registered.sh ${okapi_proxy_address}

sleep 15

./create-sample-data.sh ${knowledgebase_proxied_address} ${catalogue_proxied_address}

