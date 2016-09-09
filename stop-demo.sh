#!/usr/bin/env bash

kbport=${1:-9401}
catalogueport=${2:-9402}

./stop-registered.sh ${kbport} ${catalogueport}

