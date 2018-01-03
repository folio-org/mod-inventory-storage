#!/usr/bin/env bash

tenant_id=${1:-demo_tenant}

echo "Unregistering Inventory Storage Module"
./unregister-managed.sh ${tenant_id}

if which python3
then
  pip3 install requests

  echo "Removing Okapi environment variables"
  python3 ./okapi-setup/environment/clear-environment-variables.py

else
  echo "Install Python3 to remove environment variables from Okapi automatically"
fi
