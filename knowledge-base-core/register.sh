#!/usr/bin/env bash

knowledgebase_direct_address=${1:-http://localhost:9401}
knowledge_base_instance_id=${2:-localhost-9401}
okapi_proxy_address=${3:-http://localhost:9130}
tenant=${4:-test-tenant}

../okapi-registration/unmanaged-deployment/register.sh \
  ${knowledgebase_direct_address} \
  ${knowledge_base_instance_id} \
  ${okapi_proxy_address} \
  ${tenant}
