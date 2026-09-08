package org.folio.rest.api;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.rest.support.ResponseHandler.json;
import static org.folio.utility.ModuleUtility.getClient;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import lombok.SneakyThrows;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.HridSetting;
import org.folio.rest.jaxrs.model.HridSettings;
import org.folio.rest.support.Response;
import org.folio.rest.support.http.InterfaceUrls;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class HridSettingsStorageParameterizedTest extends TestBase {

  private static final Logger log = LogManager.getLogger();

  @SneakyThrows
  @MethodSource("data")
  @ParameterizedTest(name = "{index}: test validation failure {6}.{7} = {8}")
  void cannotUpdateHridSettingsWithBadData(String instancePrefix, long instanceStartNumber,
                                                  String holdingPrefix, long holdingStartNumber,
                                                  String itemPrefix, long itemStartNumber, String keyPart,
                                                  String testField, String expectedValue) {
    log.info("Starting cannotUpdateHridSettingsWithBadData()");

    final CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    final HridSettings newHridSettings = new HridSettings()
      .withInstances(new HridSetting().withPrefix(instancePrefix)
        .withStartNumber(instanceStartNumber))
      .withHoldings(new HridSetting().withPrefix(holdingPrefix)
        .withStartNumber(holdingStartNumber))
      .withItems(new HridSetting().withPrefix(itemPrefix).withStartNumber(itemStartNumber));

    getClient().put(InterfaceUrls.hridSettingsStorageUrl(""), newHridSettings, TENANT_ID,
      json(putCompleted));

    final Response putResponse = putCompleted.get(10, SECONDS);

    verifyValidationError(putResponse, keyPart + '.' + testField, expectedValue);

    log.info("Finished cannotUpdateHridSettingsWithBadData()");
  }

  private static Collection<Object[]> data() {
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

  private void verifyValidationError(Response response, String expectedKey, String expectedValue) {
    assertThat(response.getStatusCode(), is(422));

    final var errors = response.getJson().mapTo(Errors.class);

    assertNotNull(errors.getErrors());
    var error = errors.getErrors().getFirst();
    assertNotNull(error);
    assertNotNull(error.getMessage());
    assertNotNull(error.getParameters());
    var parameter = error.getParameters().getFirst();
    assertNotNull(parameter);
    assertThat(parameter.getKey(), is(expectedKey));
    assertThat(parameter.getValue(), is(expectedValue));
  }
}
