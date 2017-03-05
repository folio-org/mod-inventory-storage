#!/usr/bin/env bash

cd inventory/sample-data

./example-ingest.sh http://localhost:9130/inventory

cd ../..

cd inventory-storage/sample-data

./import.sh

cd ../..
