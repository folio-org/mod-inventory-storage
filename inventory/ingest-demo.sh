#!/usr/bin/env bash

./start.sh

printf 'Waiting for inventory module to start\n'

until $(curl --output /dev/null --silent --get --fail http://localhost:9403/inventory/items); do
    printf '.'
    sleep 1
done

printf '\n'

cd sample-data

./example-ingest.sh

cd ..

rm -f file-uploads/*
