#!/usr/bin/env bash

kbport=${1:-9401}
catalogueport=${2:-9402}

cd knowledge-base-core

./start.sh ${kbport}

cd ..

cd catalogue-core

./start.sh ${catalogueport}

cd ..
