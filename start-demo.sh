#!/usr/bin/env bash

./create-tenant.sh

./register.sh

cd inventory-storage

./start-demo.sh

cd ..

cd inventory

./start-demo.sh

cd ..

cd inventory/sample-data

./example-ingest.sh

cd ../..

cd inventory-storage/sample-data

./import.sh

cd ../..



