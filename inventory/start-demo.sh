#!/usr/bin/env bash

tenant_id=${1:-demo_tenant}
storage_type=${2:-external}
storage_location=${3:-http://localhost:9407}

./start.sh demo_tenant ${storage_type} ${storage_location}


