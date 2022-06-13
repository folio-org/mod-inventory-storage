package org.folio.rest.impl;

import org.folio.okapi.testing.UtilityClassTester;
import org.junit.Test;

public class StorageHelperTest {
  @Test
  public void isUtilityClass() {
    UtilityClassTester.assertUtilityClass(StorageHelper.class);
  }
}
