#!/usr/bin/env bash

kbport=${1:-9401}
catalogueport=${2:-9402}

cd knowledge-base-core/sample-data

./import.sh ${kbport}

cd ../..

cd catalogue-core/sample-data

./import.sh ${catalogueport} ${kbport}

cd ../..

