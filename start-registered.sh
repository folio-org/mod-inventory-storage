#!/usr/bin/env bash

okapi_proxy_address=${1:-http://localhost:9130}

knowledgebase_direct_address=${2:-http://localhost:9401}
catalogue_direct_address=${3:-http://localhost:9402}

./create-tenant.sh

./register.sh ${knowledgebase_direct_address} ${catalogue_direct_address}

./start-docker.sh

