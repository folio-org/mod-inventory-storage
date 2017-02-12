#!/usr/bin/env bash

inventory_direct_address=${1:-http://localhost:9403}
inventory_storage_direct_address=${2:-http://localhost:9407}

inventory_instance_id=${3:-localhost-9403}
inventory_storage_instance_id=${4:-localhost-9407}

tenant_id=${5:-demo_tenant}

if [ $# == 0 ] ; then
  echo "Using default parameters"
  echo "Inventory Module Direct Address: ${inventory_direct_address}"
  echo "Inventory Storage Module Direct Address: ${inventory_storage_direct_address}"
  echo "Usage: ./register.sh [inventory direct address] [inventory storage direct address]"
  echo ""
fi

cd inventory

./register.sh ${inventory_direct_address} ${inventory_instance_id} ${tenant_id}

cd ..

cd inventory-storage

./register.sh ${inventory_storage_direct_address} ${inventory_storage_instance_id} ${tenant_id}

cd ..

