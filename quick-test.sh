#!/usr/bin/env bash

./clean.sh

./setup-test-db.sh

mvn -q clean org.jacoco:jacoco-maven-plugin:prepare-agent test -Dorg.folio.inventory.storage.test.database=external

# Convert .xml reports into .html report, but without the CSS or images
mvn surefire-report:report-only

# Put the CSS and images where they need to be without the rest of the
# time-consuming stuff
mvn site -DgenerateReports=false

test_results=$?

if [ $test_results != 0 ]; then
  echo '--------------------------------------'
  echo 'BUILD FAILED'
  echo '--------------------------------------'
  exit 1;
else
  ./destroy-test-db.sh
  ./setup-test-db.sh

  echo '--------------------------------------'
  echo 'BUILD SUCCEEDED'
  echo '--------------------------------------'
fi
