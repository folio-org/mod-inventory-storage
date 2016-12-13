#!/usr/bin/env bash

inventory_storage_address=${1:-http://localhost:9408}

#set up the inventory storage databases
cd ./../inventory-storage

./drop-db.sh test_tenant_1
./drop-db.sh test_tenant_2

./drop-role.sh externaltestuser

./create-role.sh externaltestuser test

./create-db.sh test_tenant_1 externaltestuser
./create-db.sh test_tenant_2 externaltestuser

#start the inventory storage module with correct database config
./start.sh 9408 "$(pwd)/external-test-postgres-conf.json" test_tenant_1

cd ./../inventory

#run the tests
gradle -Dinventory.storage.address="${inventory_storage_address}" clean testExternalStorage

#stop the inventory storage module
cd ./../inventory-storage

./stop.sh

cd ./../inventory
