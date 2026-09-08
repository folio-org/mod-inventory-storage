package org.folio.rest.api;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.rest.support.ResponseHandler.empty;
import static org.folio.rest.support.ResponseHandler.json;
import static org.folio.rest.support.ResponseHandler.text;
import static org.folio.utility.ModuleUtility.getClient;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import lombok.SneakyThrows;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.HridSetting;
import org.folio.rest.jaxrs.model.HridSettings;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.HridManager;
import org.folio.rest.support.Response;
import org.folio.rest.support.http.InterfaceUrls;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
class HridSettingsStorageTest extends TestBase {

  private static final Logger log = LogManager.getLogger();

  private final HridSettings initialHridSettings = new HridSettings()
    .withInstances(new HridSetting().withPrefix("in").withStartNumber(1L))
    .withHoldings(new HridSetting().withPrefix("ho").withStartNumber(1L))
    .withItems(new HridSetting().withPrefix("it").withStartNumber(1L));

  private final HridSettings initialHridSettingsWithoutLeadingZeroes = new HridSettings()
    .withInstances(new HridSetting().withPrefix("in").withStartNumber(1L))
    .withHoldings(new HridSetting().withPrefix("ho").withStartNumber(1L))
    .withItems(new HridSetting().withPrefix("it").withStartNumber(1L))
    .withCommonRetainLeadingZeroes(false);

  private PostgresClient postgresClient;
  private HridManager hridManager;

  @SneakyThrows
  @BeforeEach
  void beforeEach(Vertx vertx, VertxTestContext testContext) {
    log.info("Initializing values");
    postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);
    hridManager = new HridManager(postgresClient);
    hridManager.updateHridSettings(initialHridSettings).onComplete(hridSettings -> {
      // We need to do this in cases where tests do not update the start number. In this
      // case, calling updateHridSettings will not update the sequences since the start number
      // has not changed. Since tests are executed in a non-deterministic order, we need to
      // ensure that the sequences are reset to 1 on start and that the next HRID value will be
      // use 1 as the number component.
      postgresClient.selectSingle("select setval('hrid_instances_seq',1,FALSE)")
        .compose(v -> postgresClient.selectSingle("select setval('hrid_holdings_seq',1,FALSE)"))
        .compose(v -> postgresClient.selectSingle("select setval('hrid_items_seq',1,FALSE)"))
        .map(v -> {
          log.info("Initializing values complete");
          testContext.completeNow();
          return null;
        });
    });

    removeAllEvents();
  }

  @Test
  void canRetrieveHridSettings()
    throws Exception {
    log.info("Starting canRetrieveHridSettings()");
    final CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    getClient().get(InterfaceUrls.hridSettingsStorageUrl(""), TENANT_ID, json(getCompleted));

    final Response response = getCompleted.get(10, SECONDS);

    assertThat(response.getStatusCode(), is(200));

    final HridSettings actualHridSettings = response.getJson().mapTo(HridSettings.class);

    assertNotNull(actualHridSettings.getInstances());
    assertThat(actualHridSettings.getInstances().getPrefix(), is("in"));
    assertThat(actualHridSettings.getInstances().getStartNumber(), is(1L));

    assertNotNull(actualHridSettings.getHoldings());
    assertThat(actualHridSettings.getHoldings().getPrefix(), is("ho"));
    assertThat(actualHridSettings.getHoldings().getStartNumber(), is(1L));

    assertNotNull(actualHridSettings.getItems());
    assertThat(actualHridSettings.getItems().getPrefix(), is("it"));
    assertThat(actualHridSettings.getItems().getStartNumber(), is(1L));

    assertThat(actualHridSettings.getCommonRetainLeadingZeroes(), is(true));

    log.info("Finished canRetrieveHridSettings()");
  }

  @Test
  void cannotRetrieveHridSettingsWithBadTenant()
    throws Exception {
    log.info("Starting cannotRetrieveHridSettingsWithBadTenant()");
    final CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    getClient().get(InterfaceUrls.hridSettingsStorageUrl(""), "BAD", text(getCompleted));

    final Response response = getCompleted.get(10, SECONDS);

    assertThat(response.getStatusCode(), is(500));

    log.info("Finished cannotRetrieveHridSettingsWithBadTenant()");
  }

  @Test
  void canUpdateHridSettings()
    throws Exception {
    log.info("Starting canUpdateHridSettings()");

    final HridSettings newHridSettings = new HridSettings()
      .withInstances(new HridSetting().withPrefix("inst").withStartNumber(100L))
      .withHoldings(new HridSetting().withPrefix("hold").withStartNumber(200L))
      .withItems(new HridSetting().withPrefix("item").withStartNumber(500L));

    final Response putResponse = updateHridSettings(newHridSettings);
    assertThat(putResponse.getStatusCode(), is(204));

    final HridSettings actualHridSettings = getHridSettings();
    verifyHridSettingsMatch(actualHridSettings, newHridSettings);

    log.info("Finished canUpdateHridSettings()");
  }

  @Test
  void cannotUpdateHridSettingsWithBadTenant()
    throws Exception {
    log.info("Starting cannotUpdateHridSettingsWithBadTenant()");

    final CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    final HridSettings newHridSettings = new HridSettings()
      .withInstances(new HridSetting().withPrefix("inst").withStartNumber(100L))
      .withHoldings(new HridSetting().withPrefix("hold").withStartNumber(200L))
      .withItems(new HridSetting().withPrefix("item").withStartNumber(500L));

    getClient().put(InterfaceUrls.hridSettingsStorageUrl(""), newHridSettings, "BAD",
      text(putCompleted));

    final Response putResponse = putCompleted.get(10, SECONDS);

    assertThat(putResponse.getStatusCode(), is(500));

    log.info("Finished canUpdateHridSettings()");
  }

  @Test
  void cannotUpdateHridSettingsId()
    throws Exception {
    log.info("Starting cannotUpdateHridSettingsID()");

    final HridSettings originalHridSettings = getHridSettings();
    final String uuid = UUID.randomUUID().toString();

    final HridSettings newHridSettings = new HridSettings()
      .withId(uuid)
      .withInstances(new HridSetting().withPrefix("inst").withStartNumber(100L))
      .withHoldings(new HridSetting().withPrefix("hold").withStartNumber(200L))
      .withItems(new HridSetting().withPrefix("item").withStartNumber(500L));

    final Response putResponse = updateHridSettings(newHridSettings);
    assertThat(putResponse.getStatusCode(), is(204));

    final HridSettings actualHridSettings = getHridSettings();
    verifyIdNotChanged(actualHridSettings, originalHridSettings, uuid);
    verifyHridSettingsMatch(actualHridSettings, newHridSettings);

    log.info("Finished cannotUpdateHridSettingsID()");
  }

  @Test
  void canGetNextInstanceHrid(VertxTestContext testContext) {
    log.info("Starting canGetNextInstanceHrid()");

    getNextInstanceHrid()
      .compose(hrid -> validateHrid(hrid, "in00000000001", testContext))
      .onComplete(testContext.succeeding(
        v -> {
          log.info("Finished canGetNextInstanceHrid()");
          testContext.completeNow();
        }));
  }

  @Test
  void canGetNextInstanceHridWithoutLeadingZeroes(VertxTestContext testContext) {
    log.info("Starting canGetNextInstanceHrid()");

    hridManager.updateHridSettings(initialHridSettingsWithoutLeadingZeroes)
      .compose(hridSettingsResult -> getNextInstanceHrid())
      .compose(hrid -> validateHrid(hrid, "in1", testContext))
      .onComplete(testContext.succeeding(
        v -> {
          log.info("Finished canGetNextInstanceHridWithoutLeadingZeroes()");
          testContext.completeNow();
        }));
  }

  @Test
  void canGetNextInstanceHridAfterSettingStartNumber(VertxTestContext testContext) {
    log.info("Starting canGetNextInstanceHridAfterSettingStartNumber()");

    final HridSettings newHridSettings = new HridSettings()
      .withInstances(new HridSetting().withPrefix("in").withStartNumber(250L))
      .withHoldings(new HridSetting().withPrefix("ho").withStartNumber(1L))
      .withItems(new HridSetting().withPrefix("it").withStartNumber(1L));

    hridManager.updateHridSettings(newHridSettings)
      .compose(hridSettingsResult -> getNextInstanceHrid())
      .compose(hrid -> validateHrid(hrid, "in00000000250", testContext))
      .onComplete(testContext.succeeding(
        v -> {
          log.info("Finished canGetNextInstanceHridAfterSettingStartNumber()");
          testContext.completeNow();
        }));
  }

  @Test
  void canGetNextInstanceHridAfterSettingStartNumberWithoutLeadingZeroes(VertxTestContext testContext) {
    log.info("Starting canGetNextInstanceHridAfterSettingStartNumberWithoutLeadingZeroes()");

    final HridSettings newHridSettings = new HridSettings()
      .withInstances(new HridSetting().withPrefix("in").withStartNumber(250L))
      .withHoldings(new HridSetting().withPrefix("ho").withStartNumber(1L))
      .withItems(new HridSetting().withPrefix("it").withStartNumber(1L))
      .withCommonRetainLeadingZeroes(false);

    hridManager.updateHridSettings(newHridSettings)
      .compose(hridSettingsResult -> getNextInstanceHrid())
      .compose(hrid -> validateHrid(hrid, "in250", testContext))
      .onComplete(testContext.succeeding(
        v -> {
          log.info("Finished canGetNextInstanceHridAfterSettingStartNumberWithoutLeadingZeroes()");
          testContext.completeNow();
        }));
  }

  @Test
  void canGetNextHoldingHrid(VertxTestContext testContext) {
    log.info("Starting canGetNextHoldingHrid()");

    hridManager.populateHrid(new HoldingsRecord())
      .map(HoldingsRecord::getHrid)
      .compose(hrid -> validateHrid(hrid, "ho00000000001", testContext))
      .onComplete(testContext.succeeding(
        v -> {
          log.info("Finished canGetNextHoldingHrid()");
          testContext.completeNow();
        }));
  }

  @Test
  void canGetNextHoldingHridWithoutLeadingZeroes(VertxTestContext testContext) {
    log.info("Starting canGetNextHoldingHridWithoutLeadingZeroes()");

    hridManager.updateHridSettings(initialHridSettingsWithoutLeadingZeroes)
      .compose(hridSettingsResult -> getNextHoldingsHrid())
      .compose(hrid -> validateHrid(hrid, "ho1", testContext))
      .onComplete(testContext.succeeding(
        v -> {
          log.info("Finished canGetNextHoldingHridWithoutLeadingZeroes()");
          testContext.completeNow();
        }));
  }

  @Test
  void canGetNextHoldingHridAfterSettingStartNumber(VertxTestContext testContext) {
    log.info("Starting canGetNextHoldingHridAfterSettingStartNumber()");

    final HridSettings newHridSettings = new HridSettings()
      .withInstances(new HridSetting().withPrefix("in").withStartNumber(1L))
      .withHoldings(new HridSetting().withPrefix("ho").withStartNumber(7890L))
      .withItems(new HridSetting().withPrefix("it").withStartNumber(1L));

    hridManager.updateHridSettings(newHridSettings)
      .compose(hridSettings -> getNextHoldingsHrid())
      .compose(hrid -> validateHrid(hrid, "ho00000007890", testContext))
      .onComplete(testContext.succeeding(
        v -> {
          log.info("Finished canGetNextHoldingHridAfterSettingStartNumber()");
          testContext.completeNow();
        }));
  }

  @Test
  void canGetNextHoldingHridAfterSettingStartNumberWithoutLeadingZeroes(VertxTestContext testContext) {
    log.info("Starting canGetNextHoldingHridAfterSettingStartNumberWithoutLeadingZeroes()");

    final HridSettings newHridSettings = new HridSettings()
      .withInstances(new HridSetting().withPrefix("in").withStartNumber(1L))
      .withHoldings(new HridSetting().withPrefix("ho").withStartNumber(7890L))
      .withItems(new HridSetting().withPrefix("it").withStartNumber(1L))
      .withCommonRetainLeadingZeroes(false);

    hridManager.updateHridSettings(newHridSettings)
      .compose(hridSettings -> getNextHoldingsHrid())
      .compose(hrid -> validateHrid(hrid, "ho7890", testContext))
      .onComplete(testContext.succeeding(
        v -> {
          log.info("Finished canGetNextHoldingHridAfterSettingStartNumberWithoutLeadingZeroes()");
          testContext.completeNow();
        }));
  }

  @Test
  void canGetNextItemHrid(VertxTestContext testContext) {
    log.info("Starting canGetNextItemHrid()");

    getNextItemHrid()
      .compose(hrid -> validateHrid(hrid, "it00000000001", testContext))
      .onComplete(testContext.succeeding(v -> {
        log.info("Finished canGetNextItemHrid()");
        testContext.completeNow();
      }));
  }

  @Test
  void canGetNextItemHridAfterSettingStartNumber(VertxTestContext testContext) {
    log.info("Starting canGetNextItemHridAfterSettingStartNumber()");

    final HridSettings newHridSettings = new HridSettings()
      .withInstances(new HridSetting().withPrefix("in").withStartNumber(1L))
      .withHoldings(new HridSetting().withPrefix("ho").withStartNumber(1L))
      .withItems(new HridSetting().withPrefix("it").withStartNumber(87654321L));

    hridManager.updateHridSettings(newHridSettings)
      .compose(hridSettings -> getNextItemHrid())
      .compose(hrid -> validateHrid(hrid, "it00087654321", testContext))
      .onComplete(testContext.succeeding(
        v -> {
          log.info("Finished canGetNextItemHridAfterSettingStartNumber()");
          testContext.completeNow();
        }));
  }

  @Test
  void canGetNextItemHridMultipleTimes(VertxTestContext testContext) {
    log.info("Starting canGetNextItemHridMultipleTimes()");

    getNextItemHrid().compose(hrid -> validateHrid(hrid, "it00000000001", testContext))
      .compose(v -> getNextItemHrid())
      .compose(hrid -> validateHrid(hrid, "it00000000002", testContext))
      .compose(v -> getNextItemHrid())
      .compose(hrid -> validateHrid(hrid, "it00000000003", testContext))
      .compose(v -> getNextItemHrid())
      .compose(hrid -> validateHrid(hrid, "it00000000004", testContext))
      .compose(v -> getNextItemHrid())
      .compose(hrid -> validateHrid(hrid, "it00000000005", testContext))
      .onComplete(testContext.succeeding(
        v -> {
          log.info("Finished canGetNextItemHridMultipleTimes()");
          testContext.completeNow();
        }));
  }

  @Test
  void canGetNextItemHridWithNoPrefix(VertxTestContext testContext) {
    log.info("Starting canGetNextItemHridWithNoPrefix()");

    final HridSettings newHridSettings = new HridSettings()
      .withInstances(new HridSetting().withStartNumber(100L))
      .withHoldings(new HridSetting().withStartNumber(200L))
      .withItems(new HridSetting().withStartNumber(300L));

    hridManager.updateHridSettings(newHridSettings)
      .compose(hridSettings -> getNextItemHrid())
      .compose(hrid -> validateHrid(hrid, "00000000300", testContext))
      .onComplete(testContext.succeeding(
        v -> {
          log.info("Finished canGetNextItemHridWithNoPrefix()");
          testContext.completeNow();
        }));
  }

  @Test
  void canRollbackFailedTransaction(VertxTestContext testContext) {
    log.info("Starting canRollbackFailedTransaction()");

    final HridSettings newHridSettings = new HridSettings()
      .withInstances(new HridSetting().withStartNumber(111L))
      .withHoldings(new HridSetting().withStartNumber(200L))
      .withItems(new HridSetting().withStartNumber(999_999_999_999L));

    hridManager.getHridSettings()
      .compose(originalHridSettings -> verifyRollbackOnFailure(newHridSettings, originalHridSettings))
      .onComplete(testContext.succeeding(
        v1 -> {
          log.info("Finished canRollbackFailedTransaction()");
          testContext.completeNow();
        }));
  }

  @Test
  void canGetNextHridWhenStartNumberIsLong(VertxTestContext testContext) {
    final HridSettings newHridSettings = new HridSettings()
      .withInstances(new HridSetting().withStartNumber(9_999_999_997L))
      .withHoldings(new HridSetting().withStartNumber(9_999_999_998L))
      .withItems(new HridSetting().withStartNumber(9_999_999_999L));

    hridManager.updateHridSettings(newHridSettings)
      .compose(v -> getNextInstanceHrid())
      .compose(hrid -> validateHrid(hrid, "09999999997", testContext))
      .compose(v -> getNextHoldingsHrid())
      .compose(hrid -> validateHrid(hrid, "09999999998", testContext))
      .compose(v -> getNextItemHrid())
      .compose(hrid -> validateHrid(hrid, "09999999999", testContext))
      .onComplete(testContext.succeedingThenComplete());
  }

  @Test
  void canGetNextHridWhenStartNumberIsLongWithoutLeadingZeroes(VertxTestContext testContext) {
    log.info("Starting canGetNextHridWhenStartNumberIsLongWithoutLeadingZeroes()");

    final HridSettings newHridSettings = new HridSettings()
      .withInstances(new HridSetting().withStartNumber(9_999_999_997L))
      .withHoldings(new HridSetting().withStartNumber(9_999_999_998L))
      .withItems(new HridSetting().withStartNumber(9_999_999_999L))
      .withCommonRetainLeadingZeroes(false);

    hridManager.updateHridSettings(newHridSettings)
      .compose(v -> getNextInstanceHrid())
      .compose(hrid -> validateHrid(hrid, "9999999997", testContext))
      .compose(v -> getNextHoldingsHrid())
      .compose(hrid -> validateHrid(hrid, "9999999998", testContext))
      .compose(v -> getNextItemHrid())
      .compose(hrid -> validateHrid(hrid, "9999999999", testContext))
      .onComplete(testContext.succeeding(
        v1 -> {
          log.info("Finished canGetNextHridWhenStartNumberIsLongWithoutLeadingZeroes()");
          testContext.completeNow();
        }));
  }

  private Future<String> getNextInstanceHrid() {
    return hridManager.populateHrid(new Instance()).map(Instance::getHrid);
  }

  private Future<String> getNextHoldingsHrid() {
    return hridManager.populateHrid(new HoldingsRecord()).map(HoldingsRecord::getHrid);
  }

  private Future<String> getNextItemHrid() {
    return hridManager.populateHrid(new Item()).map(Item::getHrid);
  }

  private Future<String> validateHrid(String hrid, String expectedValue, VertxTestContext testContext) {
    testContext.verify(() -> assertEquals(expectedValue, hrid));
    return Future.succeededFuture(hrid);
  }

  private Response updateHridSettings(HridSettings hridSettings)
    throws InterruptedException, ExecutionException, TimeoutException {
    final CompletableFuture<Response> putCompleted = new CompletableFuture<>();
    getClient().put(InterfaceUrls.hridSettingsStorageUrl(""), hridSettings, TENANT_ID,
      empty(putCompleted));
    return putCompleted.get(10, SECONDS);
  }

  private HridSettings getHridSettings()
    throws InterruptedException, ExecutionException, TimeoutException {
    final CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    getClient().get(InterfaceUrls.hridSettingsStorageUrl(""), TENANT_ID, json(getCompleted));
    final Response getResponse = getCompleted.get(10, SECONDS);
    assertThat(getResponse.getStatusCode(), is(200));
    return getResponse.getJson().mapTo(HridSettings.class);
  }

  private void verifyHridSettingsMatch(HridSettings actualHridSettings, HridSettings expectedHridSettings) {
    assertNotNull(actualHridSettings.getInstances());
    assertThat(actualHridSettings.getInstances().getPrefix(),
      is(expectedHridSettings.getInstances().getPrefix()));
    assertThat(actualHridSettings.getInstances().getStartNumber(),
      is(expectedHridSettings.getInstances().getStartNumber()));

    assertNotNull(actualHridSettings.getHoldings());
    assertThat(actualHridSettings.getHoldings().getPrefix(),
      is(expectedHridSettings.getHoldings().getPrefix()));
    assertThat(actualHridSettings.getHoldings().getStartNumber(),
      is(expectedHridSettings.getHoldings().getStartNumber()));

    assertNotNull(actualHridSettings.getItems());
    assertThat(actualHridSettings.getItems().getPrefix(),
      is(expectedHridSettings.getItems().getPrefix()));
    assertThat(actualHridSettings.getItems().getStartNumber(),
      is(expectedHridSettings.getItems().getStartNumber()));
  }

  private void verifyIdNotChanged(HridSettings actualHridSettings, HridSettings originalHridSettings, String uuid) {
    assertNotEquals(actualHridSettings.getId(), uuid);
    assertThat(actualHridSettings.getId(), is(originalHridSettings.getId()));
  }

  private Future<HridSettings> verifyRollbackOnFailure(HridSettings newHridSettings,
                                                       HridSettings originalHridSettings) {
    Promise<HridSettings> promise = Promise.promise();
    hridManager.updateHridSettings(newHridSettings).onComplete(ar -> {
      assertTrue(ar.failed());
      hridManager.getHridSettings()
        .compose(currentHridSettings -> {
          assertThat(currentHridSettings.getId(), is(originalHridSettings.getId()));
          assertThat(currentHridSettings.getInstances().getPrefix(),
            is(originalHridSettings.getInstances().getPrefix()));
          assertThat(currentHridSettings.getInstances().getStartNumber(),
            is(originalHridSettings.getInstances().getStartNumber()));
          assertThat(currentHridSettings.getHoldings().getPrefix(),
            is(originalHridSettings.getHoldings().getPrefix()));
          assertThat(currentHridSettings.getHoldings().getStartNumber(),
            is(originalHridSettings.getHoldings().getStartNumber()));
          assertThat(currentHridSettings.getItems().getPrefix(),
            is(originalHridSettings.getItems().getPrefix()));
          assertThat(currentHridSettings.getItems().getStartNumber(),
            is(originalHridSettings.getItems().getStartNumber()));
          return Future.succeededFuture(currentHridSettings);
        })
        .onComplete(promise);
    });
    return promise.future();
  }
}
