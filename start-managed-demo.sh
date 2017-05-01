#!/usr/bin/env bash

storage=${1:-"external"}
okapi_proxy_address=${2:-http://localhost:9130}

create_environment_variable() {
  environment_value_json_file=${1:-}

  environment_json=$(cat ${environment_value_json_file})

  curl -w '\n' -X POST -D -   \
     -H "Content-type: application/json"   \
     -d "${environment_json}" \
     "${okapi_proxy_address}/_/env"
}

echo "Check if Okapi is contactable"
curl -w '\n' -X GET -D -   \
     "${okapi_proxy_address}/_/env" || exit 1

echo "Building Inventory Storage"
mvn package -q -Dmaven.test.skip=true || exit 1

echo "Creating Tenant"
./create-tenant.sh

if [ "${storage}" = "external" ]; then
  echo "Running Inventory Storage module using external PostgreSQL storage"

  # setup Okapi environment variables
  create_environment_variable ./okapi-setup/environment/db-host.json
  create_environment_variable ./okapi-setup/environment/db-port.json

  ./setup-demo-db.sh

  ./register-managed.sh DeploymentDescriptor-environment.json

elif [ "${storage}" = "embedded" ]; then
  echo "Running Inventory Storage module using embedded PostgreSQL storage"

  ./register-managed.sh DeploymentDescriptor.json

else
  echo "Unknown storage mechanism: ${storage}"
  exit 1
fi
