#!/usr/bin/env bash

knowledgebase_root_address=${1:-http://localhost:9130/knowledge-base}
catalogue_root_address=${2:-http://localhost:9130/catalogue}

cd knowledge-base-core/sample-data

./import.sh ${knowledgebase_root_address}

cd ../..

cd catalogue-core/sample-data

./import.sh ${catalogue_root_address} ${knowledgebase_root_address}

cd ../..

