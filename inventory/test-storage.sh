#!/usr/bin/env bash

inventory_storage_address=${1:-http://localhost:9408}

#set up the inventory storage databases
cd ./../inventory-storage

./drop-schema.sh test_tenant_1
./drop-schema.sh test_tenant_2
./drop-db.sh test

./drop-role.sh test_tenant_1
./drop-role.sh test_tenant_2

./create-role.sh test_tenant_1 test_tenant_1
./create-role.sh test_tenant_2 test_tenant_2

./create-db.sh test test_tenant_1
./create-db.sh test test_tenant_2

./create-schema.sh test test_tenant_1 test_tenant_1
./create-schema.sh test test_tenant_2 test_tenant_2

#start the inventory storage module with correct database config
./start.sh 9408 "$(pwd)/external-test-postgres-conf.json" test_tenant_1

cd ./../inventory

#run the tests
gradle -Dinventory.storage.address="${inventory_storage_address}" clean testExternalStorage

#stop the inventory storage module
cd ./../inventory-storage

./stop.sh

cd ./../inventory
