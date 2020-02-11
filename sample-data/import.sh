#!/usr/bin/env bash

okapiUrl="http://localhost:9130"
tenant=${1:-demo_tenant}
modEndpoints='location-units/institutions location-units/campuses location-units/libraries locations
              instance-storage/instances holdings-storage/holdings item-storage/items instance-storage/instance-relationships'
for endpoint in $modEndpoints
do
  echo "Importing ${endpoint}"
  if [ -d "${endpoint}" ]; then
    json=$(ls ${endpoint}/*.json)
    for j in $json
    do
      curl -s -S -w '\n' --connect-timeout 10 \
         -H 'Content-type: application/json' \
         -H 'Accept: application/json, text/plain' \
         -H "X-Okapi-Tenant: $tenant" \
         -X POST -d @$j ${okapiUrl}/${endpoint}
    done
  fi
done

