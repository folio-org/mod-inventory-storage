#!/usr/bin/env bash

instance_id=${1:-localhost-9403}
module_id=${2:-inventory}
tenant=${3:-test-tenant}

../okapi-registration/unmanaged-deployment/unregister.sh \
  ${instance_id} \
  ${module_id} \
  ${tenant}
