#!/usr/bin/env bash

inventory_instance_id=${1:-}
inventory_storage_instance_id=${2:-}
tenant_id=${3:-demo_tenant}

cd inventory

./unregister-managed.sh ${inventory_instance_id} ${tenant_id}

cd ..

cd inventory-storage

./unregister-managed.sh ${inventory_storage_instance_id} ${tenant_id}

cd ..

./delete-tenant.sh

if  which python3
then
  pip3 install requests

  python3 ./okapi-setup/environment/clear-environment-variables.py

else
  echo "Install Python3 to remove environment variables from Okapi automatically"
fi
