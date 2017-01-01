#!/usr/bin/env bash

./setup-test-db.sh localhost 5433 postgres postgres

mvn clean test -Dorg.folio.inventory.storage.test.config=/postgres-conf-ci.json

