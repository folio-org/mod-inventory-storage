#!/usr/bin/env bash

kbport=${1:-9401}
catalogueport=${2:-9402}

./start.sh ${kbport} ${catalogueport}

sleep 15

./create-sample-data.sh ${kbport} ${catalogueport}

