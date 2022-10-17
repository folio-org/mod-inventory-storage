package org.folio.rest.api;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.folio.rest.support.ResponseHandler.json;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.HridSetting;
import org.folio.rest.jaxrs.model.HridSettings;
import org.folio.rest.support.Response;
import org.folio.rest.support.http.InterfaceUrls;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import io.vertx.ext.unit.junit.VertxUnitRunnerWithParametersFactory;

@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(VertxUnitRunnerWithParametersFactory.class)
public class HridSettingsStorageParameterizedTest extends TestBase {
  private static final Logger log = LogManager.getLogger();

  @Parameters(name = "{index}: test validation failure {6}.{7} = {8}")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][] {
      {"in", 999_999_999_999L, "ho", 1, "it", 1, "instances", "startNumber", "999999999999"},
      {"in", 1, "ho", 999_999_999_999L, "it", 1, "holdings", "startNumber", "999999999999"},
      {"in", 1, "ho", 1, "it", 999_999_999_999L, "items", "startNumber", "999999999999"},
      {"in", 0, "ho", 1, "it", 1, "instances", "startNumber", "0"},
      {"in", 1, "ho", 0, "it", 1, "holdings", "startNumber", "0"},
      {"in", 1, "ho", 1, "it", 0, "items", "startNumber", "0"},
      {"invalidprefix", 1, "ho", 1, "it", 1, "instances", "prefix", "invalidprefix"},
      {"in", 1, "invalidprefix", 1, "it", 1, "holdings", "prefix", "invalidprefix"},
      {"in", 1, "ho", 1, "invalidprefix", 1, "items", "prefix", "invalidprefix"},
      {"_invalid", 1, "ho", 1, "it", 1, "instances", "prefix", "_invalid"},
      {"in", 1, "_invalid", 1, "it", 1, "holdings", "prefix", "_invalid"},
      {"in", 1, "ho", 1, "_invalid", 1, "items", "prefix", "_invalid"}
    });
  }

  private final String instancePrefix;
  private final long instanceStartNumber;
  private final String holdingPrefix;
  private final long holdingStartNumber;
  private final String itemPrefix;
  private final long itemStartNumber;
  private final String keyPart;
  private final String testField;
  private final String expectedValue;

  public HridSettingsStorageParameterizedTest(
      String instancePrefix, long instanceStartNumber,
      String holdingPrefix, long holdingStartNumber,
      String itemPrefix, long itemStartNumber,
      String keyPart, String testField, String expectedValue) {
    this.instancePrefix = instancePrefix;
    this.instanceStartNumber = instanceStartNumber;
    this.holdingPrefix = holdingPrefix;
    this.holdingStartNumber = holdingStartNumber;
    this.itemPrefix = itemPrefix;
    this.itemStartNumber = itemStartNumber;
    this.keyPart = keyPart;
    this.testField = testField;
    this.expectedValue = expectedValue;
  }

  @Test
  public void cannotUpdateHridSettingsWithBadData()
      throws InterruptedException, ExecutionException, TimeoutException {
    log.info("Starting cannotUpdateHridSettingsWithBadData()");

    final CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    final HridSettings newHridSettings = new HridSettings()
        .withInstances(new HridSetting().withPrefix(instancePrefix)
            .withStartNumber(instanceStartNumber))
        .withHoldings(new HridSetting().withPrefix(holdingPrefix)
            .withStartNumber(holdingStartNumber))
        .withItems(new HridSetting().withPrefix(itemPrefix).withStartNumber(itemStartNumber));

    client.put(InterfaceUrls.hridSettingsStorageUrl(""), newHridSettings, TENANT_ID,
        json(putCompleted));

    final Response putResponse = putCompleted.get(5, SECONDS);

    verifyValidationError(putResponse, keyPart + '.' + testField, expectedValue);

    log.info("Finished cannotUpdateHridSettingsWithBadData()");
  }

  private void verifyValidationError(Response response, String expectedKey, String expectedValue) {
    assertThat(response.getStatusCode(), is(422));

    final Errors errors = response.getJson().mapTo(Errors.class);

    assertThat(errors.getErrors(), is(notNullValue()));
    assertThat(errors.getErrors().get(0), is(notNullValue()));
    assertThat(errors.getErrors().get(0).getMessage(), is(notNullValue()));
    assertThat(errors.getErrors().get(0).getParameters(), is(notNullValue()));
    assertThat(errors.getErrors().get(0).getParameters().get(0), is(notNullValue()));
    assertThat(errors.getErrors().get(0).getParameters().get(0).getKey(), is(expectedKey));
    assertThat(errors.getErrors().get(0).getParameters().get(0).getValue(), is(expectedValue));
  }
}
