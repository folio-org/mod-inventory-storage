#!/usr/bin/env bash

inventory_instance_id=${1:-localhost-9403}
inventory_storage_instance_id=${2:-localhost-9407}
tenant_id=${3:-demo_tenant}

cd inventory

./unregister.sh ${inventory_instance_id} ${tenant_id}

cd ..

cd inventory-storage

./unregister.sh ${inventory_storage_instance_id} ${tenant_id}

cd ..
