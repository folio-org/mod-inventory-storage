#!/usr/bin/env bash

okapi_address=${1:-http://localhost:9130}

inventory_direct_address=${1:-http://localhost:9603}
inventory_storage_direct_address=${2:-http://localhost:9407}

inventory_instance_id=${3:-localhost-9603}
inventory_storage_instance_id=${4:-localhost-9407}
tenant_id=${5:-test_tenant}

cd inventory

./create-tenant.sh ${tenant_id}

cd ..

./register.sh \
  ${inventory_direct_address} \
  ${inventory_storage_direct_address} \
  ${inventory_instance_id} \
  ${inventory_storage_instance_id} \
  ${tenant_id}

cd inventory-storage

#Does not perform tests via Okapi yet
#./build.sh
#
#storage_test_results = $?
#
cd ..
#
cd inventory
#
#gradle test
#
#inventory_test_results = $?
#
#./test-storage.sh
#
#inventory_storage_integration_test_results = $?
#
#./test-via-okapi.sh
#
#inventory_test_via_okapi_results = $?

cd ..

./unregister.sh \
  ${inventory_instance_id} \
  ${inventory_storage_instance_id} \
  ${tenant_id}

cd inventory

./delete-tenant.sh ${tenant_id}

cd ..

#echo "Inventory storage build exit code: ${storage_test_results}"
#echo "Inventory tests exit code: ${inventory_test_results}"
#echo "Inventory tests using real storage exit code: ${inventory_storage_integration_test_results}"
#echo "Inventory tests via Okapi exit code: ${inventory_test_via_okapi_results}"
#
#if [ $storage_test_results != 0 ] || [ $inventory_test_results != 0 ] || [ $inventory_storage_integration_test_results != 0 ] || [ $inventory_test_via_okapi_results != 0 ]; then
#    echo '--------------------------------------'
#    echo 'BUILD FAILED'
#    echo '--------------------------------------'
#    exit 1;
#fi
