#!/usr/bin/env bash

okapiaddress=${1:-localhost}
kbport=${2:-9601}
catalogueport=${3:-9602}

./create-tenant.sh

./register.sh ${kbport} ${catalogueport}

gradle -Dokapi.address="http://${okapiaddress}:9130" clean test testApiViaOkapi

./unregister.sh ${kbport} ${catalogueport}

./delete-tenant.sh
