#!/usr/bin/env bash

catalogue_direct_address=${1:-http://localhost:9402}
catalogue_instance_id=${2:-localhost-9402}
okapi_proxy_address=${3:-http://localhost:9130}
tenant=${4:-test-tenant}

../okapi-registration/unmanaged-deployment/register.sh \
  ${catalogue_direct_address} \
  ${catalogue_instance_id} \
  ${okapi_proxy_address} \
  ${tenant}

