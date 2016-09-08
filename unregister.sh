#!/usr/bin/env bash

kbport=${1:-9401}
catalogueport=${2:-9402}

cd knowledge-base-core

./unregister.sh ${kbport}

cd ..

cd catalogue-core

./unregister.sh ${catalogueport}

cd ..

