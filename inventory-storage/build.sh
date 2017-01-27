#!/usr/bin/env bash

mvn clean test \
    -Dorg.folio.inventory.storage.test.database=embedded

embedded_test_results=$?

./setup-test-db.sh

mvn clean test \
    -Dorg.folio.inventory.storage.test.database=external

external_test_results=$?

mvn install -Dmaven.test.skip=true

echo "Embedded database tests exit code: ${embedded_test_results}"
echo "External database tests exit code: ${external_test_results}"

if [ $embedded_test_results != 0 ] || [ $external_test_results != 0 ]; then
    echo '--------------------------------------'
    echo 'BUILD FAILED'
    echo '--------------------------------------'
    exit 1;
fi