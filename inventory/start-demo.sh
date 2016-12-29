#!/usr/bin/env bash

tenant_id=${1:-demo_tenant}
storage_type=${2:-okapi}
storage_location=${3:-}

./start.sh "${tenant_id}" "${storage_type}" "${storage_location}" \
  "http://localhost:9130/inventory"


