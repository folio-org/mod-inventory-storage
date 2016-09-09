#!/usr/bin/env bash

kbport=${1:-9401}
catalogueport=${2:-9402}

./stop.sh

./unregister.sh ${kbport} ${catalogueport}

./delete-tenant.sh

