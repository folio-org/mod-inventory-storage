#!/usr/bin/env bash

cd inventory

./stop.sh

cd ..

cd inventory-storage

./stop.sh

cd ..

./unregister.sh

./delete-tenant.sh
