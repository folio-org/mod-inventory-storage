#!/usr/bin/env bash

#Requires Okapi to be running on localhost:9301

inventory_direct_address=${1:-http://localhost:9603}
inventory_instance_id=${2:-localhost-9603}
tenant_id=${3:-test_tenant}

gradle clean test

./test-via-okapi.sh

gradle fatJar
