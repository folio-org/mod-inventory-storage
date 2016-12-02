#!/usr/bin/env bash

instance_id=${1:-localhost-9402}
module_id=${2:-"catalogue-core"}
tenant=${3:-test-tenant}

../okapi-registration/unmanaged-deployment/unregister.sh \
  ${instance_id} \
  ${module_id} \
  ${tenant}
