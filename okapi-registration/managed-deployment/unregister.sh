#!/usr/bin/env bash

instance_id=${1}
module_id=${2}
okapi_proxy_address=${3:-http://localhost:9130}
tenant_id=${4:-demo_tenant}

curl -X DELETE -D - -w '\n' "http://localhost:9130/_/proxy/tenants/${tenant_id}/modules/${module_id}"
curl -X DELETE -D - -w '\n' "http://localhost:9130/_/proxy/modules/${module_id}"

if [ -z "${instance_id}" ]
then
  echo "Undeploying managed module instance with id ${instance_id} from Okapi"

  curl -X DELETE -D - -w '\n' "http://localhost:9130/_/deployment/modules/${instance_id}"

elif which python3
then
  echo "Undeploying managed module instances from Okapi using Python"

  pip3 install requests

  python3 undeploy-managed.py ${module_id} ${tenant_id} ${okapi_proxy_address}

else
  echo "Install Python3 to undeploy managed module from Okapi automatically"
fi
