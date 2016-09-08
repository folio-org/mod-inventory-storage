#!/usr/bin/env bash

kbport=${1:-9601}
catalogueport=${2:-9602}

./create-tenant.sh

./register.sh ${kbport} ${catalogueport}

gradle clean test testApiViaOkapi

./unregister.sh ${kbport} ${catalogueport}

./delete-tenant.sh
