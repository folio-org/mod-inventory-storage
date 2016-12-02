#!/usr/bin/env bash

instance_id=${1:-localhost-9401}
module_id=${2:-"knowledge-base-core"}
tenant=${3:-test-tenant}

../okapi-registration/unmanaged-deployment/unregister.sh \
  ${instance_id} \
  ${module_id} \
  ${tenant}
