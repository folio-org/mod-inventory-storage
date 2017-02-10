#!/usr/bin/env bash

cd inventory/sample-data

./example-ingest.sh

cd ../..

cd inventory-storage/sample-data

./import.sh

cd ../..
