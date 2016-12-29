#!/usr/bin/env bash

inventory_storage_address=${1:-http://localhost:9408}

database_name="test"
admin_user_name="inventory_storage_admin"
admin_password="admin"

#set up the inventory storage databases
cd ./../inventory-storage

./drop-db.sh ${database_name}

./create-admin-role.sh ${admin_user_name} ${password}
./create-db.sh ${database_name} ${admin_user_name}

#start the inventory storage module with correct database config
./start.sh 9408 "$(pwd)/external-test-postgres-conf.json" test_tenant_1

echo "Initialising Tenant: test_tenant_2"

curl -w '\n' -X POST -D -   \
     -H "Content-type: application/json"   \
     -H "Accept: */*"   \
     -H "X-Okapi-Tenant: test_tenant_2" \
     http://localhost:9408/tenant

cd ./../inventory

#run the tests
gradle -Dinventory.storage.address="${inventory_storage_address}" clean testExternalStorage

#stop the inventory storage module
cd ./../inventory-storage

./stop.sh

cd ./../inventory
