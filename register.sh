#!/usr/bin/env bash

knowledgebase_direct_address=${1:-http://localhost:9401}
catalogue_direct_address=${2:-http://localhost:9402}

knowledgebase_instance_id=${3:-localhost-9401}
catalogue_instance_id=${4:-localhost-9402}

if [ $# == 0 ] ; then
  echo "Using default parameters"
  echo "Knowledge Base Core Module Direct Address: ${knowledgebase_direct_address}"
  echo "Catalogue Core Module Direct Address: ${catalogue_direct_address}"
  echo "Usage: ./register.sh  [knowledge base core direct address] [catalogue core direct address]"
fi

cd knowledge-base-core

./register.sh ${knowledgebase_direct_address} ${knowledgebase_instance_id}

cd ..

cd catalogue-core

./register.sh ${catalogue_direct_address} ${catalogue_instance_id}

cd ..
