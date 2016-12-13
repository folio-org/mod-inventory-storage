#!/usr/bin/env bash

cd knowledge-base-core

./stop.sh

cd ..

cd catalogue-core

./stop.sh

cd ..

cd inventory-storage

./stop.sh

cd ..

cd inventory

./stop.sh

cd ..
