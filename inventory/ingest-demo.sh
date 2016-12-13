#!/usr/bin/env bash

inventory_root_address=${1:-http://localhost:9403/inventory}

cd sample-data

./example-ingest.sh ${inventory_root_address}

cd ..

# wait for completion then delete uploads
# rm -f file-uploads/*
