#!/usr/bin/env bash

./start.sh

cd sample-data

./example-ingest.sh

cd ..

rm -f file-uploads/*
