package org.folio.rest.impl;

import org.folio.rest.testing.UtilityClassTester;
import org.junit.Test;

public class StorageHelperTest {
  @Test
  public void isUtilityClass() {
    UtilityClassTester.assertUtilityClass(StorageHelper.class);
  }
}
