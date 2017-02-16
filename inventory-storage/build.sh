#!/usr/bin/env bash

mvn compile -Dmaven.test.skip=true

mvn test \
    -Dorg.folio.inventory.storage.test.database=embedded

embedded_test_results=$?

./setup-test-db.sh

mvn test \
    -Dorg.folio.inventory.storage.test.database=external

external_test_results=$?

./setup-test-db.sh

env db.host=localhost \
    db.port=5432 \
    db.database=test \
    db.username=inventory_storage_admin \
    db.password=admin \
    mvn test -Dorg.folio.inventory.storage.test.database=environment

environment_test_results=$?

mvn package -Dmaven.test.skip=true

echo "Embedded database tests exit code: ${embedded_test_results}"
echo "External database tests exit code: ${external_test_results}"
echo "Environment database tests exit code: ${environment_test_results}"

if [ $embedded_test_results != 0 ] || [ $external_test_results != 0 ] || [ $environment_test_results != 0 ]; then
    echo '--------------------------------------'
    echo 'BUILD FAILED'
    echo '--------------------------------------'
    exit 1;
fi
