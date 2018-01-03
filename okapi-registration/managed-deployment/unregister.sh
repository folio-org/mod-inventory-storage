#!/usr/bin/env bash

okapi_proxy_address=${1:-http://localhost:9130}
tenant_id=${2:-demo_tenant}

if which python3
then
  echo "Un-registering managed module instances from Okapi using Python"

  pip3 install requests

  script_directory="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

  python3 ${script_directory}/unregister.py ${tenant_id} ${okapi_proxy_address}

else
  echo "Install Python3 to un-register managed module from Okapi automatically"
fi
