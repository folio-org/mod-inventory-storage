#!/usr/bin/env bash

./destroy-test-db.sh

./setup-test-db.sh

mvn -q clean test -Dorg.folio.inventory.storage.test.database=external

test_results=$?

if [ $test_results != 0 ]; then
  echo '--------------------------------------'
  echo 'BUILD FAILED'
  echo '--------------------------------------'
  exit 1;
else
  ./destroy-test-db.sh

  echo '--------------------------------------'
  echo 'BUILD SUCCEEDED'
  echo '--------------------------------------'
fi
