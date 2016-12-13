#!/usr/bin/env bash

okapi_address=${1:-http://localhost:9130}

knowledgebase_direct_address=${1:-http://localhost:9601}
catalogue_direct_address=${2:-http://localhost:9602}
inventory_direct_address=${3:-http://localhost:9603}
inventory_storage_direct_address=${4:-http://localhost:9407}

knowledgebase_instance_id=${5:-localhost-9601}
catalogue_instance_id=${6:-localhost-9602}
inventory_instance_id=${7:-localhost-9603}
inventory_storage_instance_id=${8:-localhost-9407} #Not currently included
tenant_id=${9:-test_tenant}

./create-tenant.sh ${tenant_id}

./register.sh \
  ${knowledgebase_direct_address} \
  ${catalogue_direct_address} \
  ${inventory_direct_address} \
  ${inventory_storage_direct_address} \
  ${knowledgebase_instance_id} \
  ${catalogue_instance_id} \
  ${inventory_instance_id} \
  ${inventory_storage_instance_id} \
  ${tenant_id}

gradle -Dokapi.address="${okapi_address}" clean test testApiViaOkapi

./unregister.sh \
  ${knowledgebase_instance_id} \
  ${catalogue_instance_id} \
  ${inventory_instance_id} \
  ${inventory_storage_instance_id} \
  ${tenant_id}

./delete-tenant.sh ${tenant_id}
