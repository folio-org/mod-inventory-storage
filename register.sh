#!/usr/bin/env bash

knowledgebase_direct_address=${1:-http://localhost:9401}
catalogue_direct_address=${2:-http://localhost:9402}
inventory_direct_address=${3:-http://localhost:9403}

knowledgebase_instance_id=${4:-localhost-9401}
catalogue_instance_id=${5:-localhost-9402}
inventory_instance_id=${6:-localhost-9403}
tenant_id=${7:-demo_tenant}

if [ $# == 0 ] ; then
  echo "Using default parameters"
  echo "Knowledge Base Core Module Direct Address: ${knowledgebase_direct_address}"
  echo "Catalogue Core Module Direct Address: ${catalogue_direct_address}"
  echo "Inventory Module Direct Address: ${inventory_direct_address}"
  echo "Usage: ./register.sh  [knowledge base core direct address]
  [catalogue core direct address] [inventory direct address]"
  echo ""
fi

cd knowledge-base-core

./register.sh ${knowledgebase_direct_address} ${knowledgebase_instance_id} ${tenant_id}

cd ..

cd catalogue-core

./register.sh ${catalogue_direct_address} ${catalogue_instance_id} ${tenant_id}

cd ..

cd inventory

./register.sh ${inventory_direct_address} ${inventory_instance_id} ${tenant_id}

cd ..
