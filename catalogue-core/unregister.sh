#!/usr/bin/env bash

instance_id=${1:-localhost-9402}
tenant_id=${2:-demo_tenant}
module_id=${3:-catalogue-core}

../okapi-registration/unmanaged-deployment/unregister.sh \
  ${instance_id} \
  ${module_id} \
  ${tenant_id}
