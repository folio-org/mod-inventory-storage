package org.folio.rest.impl;

import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import org.folio.rest.api.TestBase;
import org.folio.rest.impl.StorageHelper;
import org.folio.rest.testing.UtilityClassTester;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class StorageHelperTest extends TestBase {
  @Rule
  public Timeout timeoutRule = Timeout.seconds(5);

  @Rule
  public RunTestOnContext contextRule = new RunTestOnContext();

  @Test
  public void isUtilityClass() {
    UtilityClassTester.assertUtilityClass(StorageHelper.class);
  }
}
