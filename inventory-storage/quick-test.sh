#!/usr/bin/env bash

./setup-test-db.sh

mvn clean test \
    -Dorg.folio.inventory.storage.test.database=external


