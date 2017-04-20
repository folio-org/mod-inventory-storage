#!/usr/bin/env bash

instance_id=${1:-}
tenant_id=${2:-demo_tenant}
okapi_proxy_address=${3:-http://localhost:9130}
module_id=${4:-inventory}

./okapi-registration/managed-deployment/unregister.sh \
  ${instance_id} \
  ${module_id} \
  ${okapi_proxy_address} \
  ${tenant_id}

