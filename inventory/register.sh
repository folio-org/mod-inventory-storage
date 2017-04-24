#!/usr/bin/env bash

module_direct_address=${1:-http://localhost:9403}
module_instance_id=${2:-localhost-9403}
tenant_id=${3:-demo_tenant}
okapi_proxy_address=${4:-http://localhost:9130}
module_id=${5:-inventory}

./okapi-registration/unmanaged-deployment/register.sh \
  ${module_direct_address} \
  ${module_instance_id} \
  ${module_id} \
  ${okapi_proxy_address} \
  ${tenant_id}

