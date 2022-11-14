package org.folio.rest;

import org.folio.rest.impl.StorageHelperTest;
import org.folio.rest.impl.TenantRefAPITest;
import org.folio.rest.support.CollectionUtilTest;
import org.folio.rest.support.CompletableFutureUtilTest;
import org.folio.rest.support.CqlQueryTest;
import org.folio.rest.unit.ItemDamagedStatusAPIUnitTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
  CollectionUtilTest.class,
  CompletableFutureUtilTest.class,
  CqlQueryTest.class,
  ItemDamagedStatusAPIUnitTest.class,
  StorageHelperTest.class,
  TenantRefAPITest.class,
})
public class RestTestSuite {

  private RestTestSuite() {
    throw new UnsupportedOperationException("Cannot instantiate utility class.");
  }
}
