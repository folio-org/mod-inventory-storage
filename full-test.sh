#!/usr/bin/env bash

okapi_address=${1:-http://localhost:9130}

inventory_direct_address=${1:-http://localhost:9603}
inventory_storage_direct_address=${2:-http://localhost:9407}

inventory_instance_id=${3:-localhost-9603}
inventory_storage_instance_id=${4:-localhost-9407}
tenant_id=${5:-test_tenant}

./create-tenant.sh ${tenant_id}

./register.sh \
  ${inventory_direct_address} \
  ${inventory_storage_direct_address} \
  ${inventory_instance_id} \
  ${inventory_storage_instance_id} \
  ${tenant_id}

cd inventory-storage

#Does not perform tests via Okapi yet
./build.sh

cd ..

cd inventory

./test-storage.sh

cd ..

gradle -Dokapi.address="${okapi_address}" clean :inventory:test :inventory:testApiViaOkapi

./unregister.sh \
  ${inventory_instance_id} \
  ${inventory_storage_instance_id} \
  ${tenant_id}

./delete-tenant.sh ${tenant_id}
