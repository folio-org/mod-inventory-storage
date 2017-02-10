#!/usr/bin/env bash

./create-tenant.sh

./register.sh

cd inventory-storage

./start-demo.sh

cd ..

cd inventory

./start-demo.sh

cd ..

./import-sample-data.sh
