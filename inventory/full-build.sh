#!/usr/bin/env bash

#Requires Okapi to be running on localhost:9301

inventory_direct_address=${1:-http://localhost:9603}
inventory_instance_id=${2:-localhost-9603}
tenant_id=${3:-test_tenant}

gradle clean test

cd ..

./create-tenant.sh ${tenant_id}

cd inventory

./register.sh ${inventory_direct_address} ${inventory_instance_id} ${tenant_id}

gradle -Dokapi.address="${okapi_address}" testApiViaOkapi

./unregister.sh ${inventory_instance_id} ${tenant_id}

cd ..

./delete-tenant.sh ${tenant_id}

cd inventory

gradle fatJar
