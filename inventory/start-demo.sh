#!/usr/bin/env bash

tenant_id=${1:-demo_tenant}
storage_type=${2:-internal}
storage_location=${3:-}

./start.sh demo_tenant external http://localhost:9407


