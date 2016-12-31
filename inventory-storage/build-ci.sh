#!/usr/bin/env bash

./setup-demo-db.sh localhost 5433

mvn clean test -Dorg.folio.inventory.storage.test.config=/postgres-conf-ci.json

