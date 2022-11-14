package org.folio;

import org.folio.rest.RestTestSuite;
import org.folio.rest.api.StorageTestSuite;
import org.folio.services.ServiceTestSuite;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
  RestTestSuite.class,
  ServiceTestSuite.class,
  StorageTestSuite.class,
})
public class AllTestSuite {
  public static String KAFKA_CONTAINER_NAME = "confluentinc/cp-kafka:5.4.3";

  private AllTestSuite() {
    throw new UnsupportedOperationException("Cannot instantiate utility class.");
  }
}
