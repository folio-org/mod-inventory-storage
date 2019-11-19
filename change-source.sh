#!/usr/bin/env bash

for f in ./sample-data/instances/*.json; do
  cat $f \
    | jq '.source |= "FOLIO"' \
    | sponge $f
done
