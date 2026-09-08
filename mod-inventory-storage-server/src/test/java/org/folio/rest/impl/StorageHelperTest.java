package org.folio.rest.impl;

import org.folio.okapi.testing.UtilityClassTester;
import org.junit.jupiter.api.Test;

class StorageHelperTest {
  @Test
  void isUtilityClass() {
    UtilityClassTester.assertUtilityClass(StorageHelper.class);
  }
}
