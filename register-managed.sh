#!/usr/bin/env bash

deployment_descriptor=${1:-target/DeploymentDescriptor-environment.json}
tenant_id=${2:-demo_tenant}
okapi_proxy_address=${3:-http://localhost:9130}

./okapi-registration/managed-deployment/register.sh \
  ${deployment_descriptor} \
  ${okapi_proxy_address} \
  ${tenant_id}


