#!/usr/bin/env bash

cd knowledge-base-core

./stop-docker.sh

cd ..

cd catalogue-core

./stop-docker.sh

cd ..

cd inventory

./stop.sh

cd ..

cd inventory-storage

./stop.sh

cd ..

./unregister.sh

./delete-tenant.sh
