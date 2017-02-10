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

echo "Building Inventory Storage"
cd inventory-storage

mvn package -q -Dmaven.test.skip=true || exit 1

cd ..

echo "Building Inventory"

cd inventory

gradle -q fatJar || exit 1

cd ..

echo "Check if Okapi is contactable"
curl -w '\n' -X GET -D -   \
     "${okapi_proxy_address}/_/env" || exit 1

./create-tenant.sh

if [ "${storage}" = "external" ]; then
  echo "Running Inventory Storage module using external PostgreSQL storage"

  # setup Okapi environment variables
  create_environment_variable ./okapi-setup/environment/db-host.json
  create_environment_variable ./okapi-setup/environment/db-port.json
  create_environment_variable ./okapi-setup/environment/db-database.json
  create_environment_variable ./okapi-setup/environment/db-username.json
  create_environment_variable ./okapi-setup/environment/db-password.json

  cd inventory-storage

  ./setup-demo-db.sh

  ./register-managed.sh DeploymentDescriptor-environment.json

  cd ..

elif [ "${storage}" = "embedded" ]; then
  echo "Running Inventory Storage module using embedded PostgreSQL storage"

  cd inventory-storage

  ./register-managed.sh DeploymentDescriptor.json

  cd ..

else
  echo "Unknown storage mechanism: ${storage}"
  exit 1
fi

echo "Running Inventory module"

cd inventory

./register-managed.sh

cd ..

./import-sample-data.sh
