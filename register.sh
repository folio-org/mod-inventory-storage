#!/usr/bin/env bash

kbport=${1:-9401}
catalogueport=${2:-9402}

echo ${kbport}
echo ${catalogueport}

cd knowledge-base-core

./register.sh ${kbport}

cd ..

cd catalogue-core

./register.sh ${catalogueport}

cd ..

