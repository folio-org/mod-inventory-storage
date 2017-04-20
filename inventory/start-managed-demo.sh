#!/usr/bin/env bash

storage=${1:-"external"}
okapi_proxy_address=${2:-http://localhost:9130}

echo "Check if Okapi is contactable"
curl -w '\n' -X GET -D -   \
     "${okapi_proxy_address}/_/env" || exit 1

echo "Building Inventory"

gradle -q fatJar || exit 1

echo "Creating Tenant"
./create-tenant.sh

echo "Running Inventory module"
./register-managed.sh
