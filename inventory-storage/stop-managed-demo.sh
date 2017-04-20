#!/usr/bin/env bash

inventory_storage_instance_id=${1:-}
tenant_id=${2:-demo_tenant}

echo "Unregistering Inventory Storage Module"
./unregister-managed.sh ${inventory_storage_instance_id} ${tenant_id}

echo "Deleting Tenant"
./delete-tenant.sh

if  which python3
then
  pip3 install requests

  echo "Removing Okapi environment variables"
  python3 ./okapi-setup/environment/clear-environment-variables.py

else
  echo "Install Python3 to remove environment variables from Okapi automatically"
fi
