package org.folio.rest.api;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.folio.rest.support.ResponseHandler.empty;
import static org.folio.rest.support.ResponseHandler.json;
import static org.folio.rest.support.ResponseHandler.text;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.rest.jaxrs.model.HridSetting;
import org.folio.rest.jaxrs.model.HridSettings;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.HridManager;
import org.folio.rest.support.Response;
import org.folio.rest.support.http.InterfaceUrls;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class HridSettingsStorageTest extends TestBase {
  private static final Logger log = LoggerFactory.getLogger(HridSettingsStorageTest.class);

  private final HridSettings initialHridSettings = new HridSettings()
      .withInstances(new HridSetting().withPrefix("in").withStartNumber(1))
      .withHoldings(new HridSetting().withPrefix("ho").withStartNumber(1))
      .withItems(new HridSetting().withPrefix("it").withStartNumber(1));

  @Before
  public void setUp(TestContext testContext) throws Exception {
    log.info("Initializing values");
    final Async async = testContext.async();
    final Vertx vertx = StorageTestSuite.getVertx();
    final PostgresClient postgresClient =
        PostgresClient.getInstance(vertx, TENANT_ID);
    final HridManager hridManager = new HridManager(vertx.getOrCreateContext(), postgresClient);
    hridManager.updateHridSettings(initialHridSettings).setHandler(hridSettings -> {
      // We need to do this in cases where tests do not update the start number. In this
      // case, calling updateHridSettings will not update the sequences since the start number
      // has not changed. Since tests are executed in a non-deterministic order, we need to
      // ensure that the sequences are reset to 1 on start and that the next HRID value will be
      // use 1 as the number component.
      Promise<JsonArray> instancePromise = Promise.promise();
      Promise<JsonArray> holdingPromise = Promise.promise();
      Promise<JsonArray> itemPromise = Promise.promise();
      postgresClient.selectSingle("select setval('hrid_instances_seq',1,FALSE)", instancePromise);
      instancePromise.future().map(v -> {
        postgresClient.selectSingle("select setval('hrid_holdings_seq',1,FALSE)", holdingPromise);
        return null;
      });
      holdingPromise.future().map(v -> {
        postgresClient.selectSingle("select setval('hrid_items_seq',1,FALSE)", itemPromise);
        return null;
      });
      itemPromise.future().map(v -> {
        log.info("Initializing values complete");
        async.complete();
        return null;
      });
    });
  }

  @Test
  public void canRetrieveHridSettings()
      throws InterruptedException, ExecutionException, TimeoutException {
    log.info("Starting canRetrieveHridSettings()");
    final CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(InterfaceUrls.hridSettingsStorageUrl(""), TENANT_ID, json(getCompleted));

    final Response response = getCompleted.get(5, SECONDS);

    assertThat(response.getStatusCode(), is(200));

    final HridSettings actualHridSettings = response.getJson().mapTo(HridSettings.class);

    assertThat(actualHridSettings.getInstances(), is(notNullValue()));
    assertThat(actualHridSettings.getInstances().getPrefix(), is("in"));
    assertThat(actualHridSettings.getInstances().getStartNumber(), is(1));

    assertThat(actualHridSettings.getHoldings(), is(notNullValue()));
    assertThat(actualHridSettings.getHoldings().getPrefix(), is("ho"));
    assertThat(actualHridSettings.getHoldings().getStartNumber(), is(1));

    assertThat(actualHridSettings.getItems(), is(notNullValue()));
    assertThat(actualHridSettings.getItems().getPrefix(), is("it"));
    assertThat(actualHridSettings.getItems().getStartNumber(), is(1));

    log.info("Finished canRetrieveHridSettings()");
  }

  @Test
  public void cannotRetrieveHridSettingsWithBadTenant()
      throws InterruptedException, ExecutionException, TimeoutException {
    log.info("Starting cannotRetrieveHridSettingsWithBadTenant()");
    final CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(InterfaceUrls.hridSettingsStorageUrl(""), "BAD", text(getCompleted));

    final Response response = getCompleted.get(5, SECONDS);

    assertThat(response.getStatusCode(), is(500));

    log.info("Finished cannotRetrieveHridSettingsWithBadTenant()");
  }

  @Test
  public void canUpdateHridSettings()
      throws InterruptedException, ExecutionException, TimeoutException {
    log.info("Starting canUpdateHridSettings()");

    final CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    final HridSettings newHridSettings = new HridSettings()
        .withInstances(new HridSetting().withPrefix("inst").withStartNumber(100))
        .withHoldings(new HridSetting().withPrefix("hold").withStartNumber(200))
        .withItems(new HridSetting().withPrefix("item").withStartNumber(500));

    client.put(InterfaceUrls.hridSettingsStorageUrl(""), newHridSettings, TENANT_ID,
        empty(putCompleted));

    final Response putResponse = putCompleted.get(5, SECONDS);

    assertThat(putResponse.getStatusCode(), is(204));

    final CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(InterfaceUrls.hridSettingsStorageUrl(""), TENANT_ID, json(getCompleted));

    final Response getResponse = getCompleted.get(5, SECONDS);

    final HridSettings actualHridSettings = getResponse.getJson().mapTo(HridSettings.class);

    assertThat(actualHridSettings.getInstances(), is(notNullValue()));
    assertThat(actualHridSettings.getInstances().getPrefix(),
        is(newHridSettings.getInstances().getPrefix()));
    assertThat(actualHridSettings.getInstances().getStartNumber(),
        is(newHridSettings.getInstances().getStartNumber()));

    assertThat(actualHridSettings.getHoldings(), is(notNullValue()));
    assertThat(actualHridSettings.getHoldings().getPrefix(),
        is(newHridSettings.getHoldings().getPrefix()));
    assertThat(actualHridSettings.getHoldings().getStartNumber(),
        is(newHridSettings.getHoldings().getStartNumber()));

    assertThat(actualHridSettings.getItems(), is(notNullValue()));
    assertThat(actualHridSettings.getItems().getPrefix(),
        is(newHridSettings.getItems().getPrefix()));
    assertThat(actualHridSettings.getItems().getStartNumber(),
        is(newHridSettings.getItems().getStartNumber()));

    log.info("Finished canUpdateHridSettings()");
  }

  @Test
  public void cannotUpdateHridSettingsWithBadTenant()
      throws InterruptedException, ExecutionException, TimeoutException {
    log.info("Starting cannotUpdateHridSettingsWithBadTenant()");

    final CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    final HridSettings newHridSettings = new HridSettings()
        .withInstances(new HridSetting().withPrefix("inst").withStartNumber(100))
        .withHoldings(new HridSetting().withPrefix("hold").withStartNumber(200))
        .withItems(new HridSetting().withPrefix("item").withStartNumber(500));

    client.put(InterfaceUrls.hridSettingsStorageUrl(""), newHridSettings, "BAD",
        text(putCompleted));

    final Response putResponse = putCompleted.get(5, SECONDS);

    assertThat(putResponse.getStatusCode(), is(500));

    log.info("Finished canUpdateHridSettings()");
  }

  @Test
  public void cannotUpdateHridSettingsID()
      throws InterruptedException, ExecutionException, TimeoutException {
    log.info("Starting cannotUpdateHridSettingsID()");

    final CompletableFuture<Response> originalGetCompleted = new CompletableFuture<>();

    client.get(InterfaceUrls.hridSettingsStorageUrl(""), TENANT_ID, json(originalGetCompleted));

    final Response originalGetResponse = originalGetCompleted.get(5, SECONDS);

    assertThat(originalGetResponse.getStatusCode(), is(200));

    final HridSettings originalHridSettings =
        originalGetResponse.getJson().mapTo(HridSettings.class);

    final String uuid = UUID.randomUUID().toString();

    final HridSettings newHridSettings = new HridSettings()
        .withId(uuid)
        .withInstances(new HridSetting().withPrefix("inst").withStartNumber(100))
        .withHoldings(new HridSetting().withPrefix("hold").withStartNumber(200))
        .withItems(new HridSetting().withPrefix("item").withStartNumber(500));

    final CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    client.put(InterfaceUrls.hridSettingsStorageUrl(""), newHridSettings, TENANT_ID,
        empty(putCompleted));

    final Response putResponse = putCompleted.get(5, SECONDS);

    assertThat(putResponse.getStatusCode(), is(204));

    final CompletableFuture<Response> getAfterUpdateCompleted = new CompletableFuture<>();

    client.get(InterfaceUrls.hridSettingsStorageUrl(""), TENANT_ID, json(getAfterUpdateCompleted));

    final Response getAfterUpdateResponse = getAfterUpdateCompleted.get(5, SECONDS);

    final HridSettings actualHridSettings =
        getAfterUpdateResponse.getJson().mapTo(HridSettings.class);

    assertThat(actualHridSettings.getId(), not(uuid));
    assertThat(actualHridSettings.getId(), is(originalHridSettings.getId()));

    assertThat(actualHridSettings.getInstances(), is(notNullValue()));
    assertThat(actualHridSettings.getInstances().getPrefix(),
        is(newHridSettings.getInstances().getPrefix()));
    assertThat(actualHridSettings.getInstances().getStartNumber(),
        is(newHridSettings.getInstances().getStartNumber()));

    assertThat(actualHridSettings.getHoldings(), is(notNullValue()));
    assertThat(actualHridSettings.getHoldings().getPrefix(),
        is(newHridSettings.getHoldings().getPrefix()));
    assertThat(actualHridSettings.getHoldings().getStartNumber(),
        is(newHridSettings.getHoldings().getStartNumber()));

    assertThat(actualHridSettings.getItems(), is(notNullValue()));
    assertThat(actualHridSettings.getItems().getPrefix(),
        is(newHridSettings.getItems().getPrefix()));
    assertThat(actualHridSettings.getItems().getStartNumber(),
        is(newHridSettings.getItems().getStartNumber()));

    log.info("Finished cannotUpdateHridSettingsID()");
  }

  @Test
  public void canGetNextInstanceHrid(TestContext testContext)
      throws InterruptedException, ExecutionException, TimeoutException {
    log.info("Starting canGetNextInstanceHrid()");

    final Vertx vertx = StorageTestSuite.getVertx();
    final PostgresClient postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);

    final HridManager hridManager = new HridManager(vertx.getOrCreateContext(), postgresClient);

    hridManager.getNextInstanceHrid()
      .compose(hrid -> validateHrid(hrid, "in00000001", testContext))
      .setHandler(testContext.asyncAssertSuccess(
          v -> log.info("Finished canGetNextInstanceHrid()")));
  }

  @Test
  public void canGetNextInstanceHridAfterSettingStartNumber(TestContext testContext)
      throws InterruptedException, ExecutionException, TimeoutException {
    log.info("Starting canGetNextInstanceHridAfterSettingStartNumber()");

    final Vertx vertx = StorageTestSuite.getVertx();
    final PostgresClient postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);

    final HridManager hridManager = new HridManager(vertx.getOrCreateContext(), postgresClient);

    final HridSettings newHridSettings = new HridSettings()
        .withInstances(new HridSetting().withPrefix("in").withStartNumber(250))
        .withHoldings(new HridSetting().withPrefix("ho").withStartNumber(1))
        .withItems(new HridSetting().withPrefix("it").withStartNumber(1));

    hridManager.updateHridSettings(newHridSettings).setHandler(
        testContext.asyncAssertSuccess(
            hridSettingsResult -> hridManager.getNextInstanceHrid().compose(
                hrid -> validateHrid(hrid, "in00000250", testContext))
              .setHandler(testContext.asyncAssertSuccess(
                  v -> log.info("Finished canGetNextInstanceHridAfterSettingStartNumber()")))));
  }

  @Test
  public void canGetNextHoldingHrid(TestContext testContext)
      throws InterruptedException, ExecutionException, TimeoutException {
    log.info("Starting canGetNextHoldingHrid()");

    final Vertx vertx = StorageTestSuite.getVertx();
    final PostgresClient postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);

    final HridManager hridManager = new HridManager(vertx.getOrCreateContext(), postgresClient);

    hridManager.getNextHoldingHrid()
      .compose(hrid -> validateHrid(hrid, "ho00000001", testContext))
      .setHandler(testContext.asyncAssertSuccess(
          v -> log.info("Finished canGetNextHoldingHrid()")));
  }

  @Test
  public void canGetNextHoldingHridAfterSettingStartNumber(TestContext testContext)
      throws InterruptedException, ExecutionException, TimeoutException {
    log.info("Starting canGetNextHoldingHridAfterSettingStartNumber()");

    final Vertx vertx = StorageTestSuite.getVertx();
    final PostgresClient postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);

    final HridManager hridManager = new HridManager(vertx.getOrCreateContext(), postgresClient);

    final HridSettings newHridSettings = new HridSettings()
        .withInstances(new HridSetting().withPrefix("in").withStartNumber(1))
        .withHoldings(new HridSetting().withPrefix("ho").withStartNumber(7890))
        .withItems(new HridSetting().withPrefix("it").withStartNumber(1));

    hridManager.updateHridSettings(newHridSettings).setHandler(
        testContext.asyncAssertSuccess(
            hridSettings -> hridManager.getNextHoldingHrid().compose(
                hrid -> validateHrid(hrid, "ho00007890", testContext))
              .setHandler(testContext.asyncAssertSuccess(
                  v -> log.info("Finished canGetNextHoldingHridAfterSettingStartNumber()")))));
  }

  @Test
  public void canGetNextItemHrid(TestContext testContext)
      throws InterruptedException, ExecutionException, TimeoutException {
    log.info("Starting canGetNextItemHrid()");

    final Vertx vertx = StorageTestSuite.getVertx();
    final PostgresClient postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);

    final HridManager hridManager = new HridManager(vertx.getOrCreateContext(), postgresClient);

    hridManager.getNextItemHrid()
      .compose(hrid -> validateHrid(hrid, "it00000001", testContext))
      .setHandler(testContext.asyncAssertSuccess(v -> log.info("Finished canGetNextItemHrid()")));
  }

  @Test
  public void canGetNextItemHridAfterSettingStartNumber(TestContext testContext)
      throws InterruptedException, ExecutionException, TimeoutException {
    log.info("Starting canGetNextItemHridAfterSettingStartNumber()");

    final Vertx vertx = StorageTestSuite.getVertx();
    final PostgresClient postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);

    final HridManager hridManager = new HridManager(vertx.getOrCreateContext(), postgresClient);

    final HridSettings newHridSettings = new HridSettings()
        .withInstances(new HridSetting().withPrefix("in").withStartNumber(1))
        .withHoldings(new HridSetting().withPrefix("ho").withStartNumber(1))
        .withItems(new HridSetting().withPrefix("it").withStartNumber(87654321));

    hridManager.updateHridSettings(newHridSettings).setHandler(
        testContext.asyncAssertSuccess(
            hridSettings -> hridManager.getNextItemHrid().compose(
                hrid -> validateHrid(hrid, "it87654321", testContext))
              .setHandler(testContext.asyncAssertSuccess(
                  v -> log.info("Finished canGetNextItemHridAfterSettingStartNumber()")))));
  }

  @Test
  public void canGetNextItemHridMultipleTimes(TestContext testContext)
      throws InterruptedException, ExecutionException, TimeoutException {
    log.info("Starting canGetNextItemHridMultipleTimes()");

    final Vertx vertx = StorageTestSuite.getVertx();
    final PostgresClient postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);

    final HridManager hridManager = new HridManager(vertx.getOrCreateContext(), postgresClient);

    hridManager.getNextItemHrid().compose(hrid -> validateHrid(hrid, "it00000001", testContext))
      .compose(v -> hridManager.getNextItemHrid())
      .compose(hrid -> validateHrid(hrid, "it00000002", testContext))
      .compose(v -> hridManager.getNextItemHrid())
      .compose(hrid -> validateHrid(hrid, "it00000003", testContext))
      .compose(v -> hridManager.getNextItemHrid())
      .compose(hrid -> validateHrid(hrid, "it00000004", testContext))
      .compose(v -> hridManager.getNextItemHrid())
      .compose(hrid -> validateHrid(hrid, "it00000005", testContext))
      .setHandler(testContext.asyncAssertSuccess(
          v -> log.info("Finished canGetNextItemHridMultipleTimes()")));
  }

  @Test
  public void canGetNextItemHridWithNoPrefix(TestContext testContext)
      throws InterruptedException, ExecutionException, TimeoutException {
    log.info("Starting canGetNextItemHridWithNoPrefix()");

    final Vertx vertx = StorageTestSuite.getVertx();
    final PostgresClient postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);

    final HridManager hridManager = new HridManager(vertx.getOrCreateContext(), postgresClient);

    final HridSettings newHridSettings = new HridSettings()
        .withInstances(new HridSetting().withStartNumber(100))
        .withHoldings(new HridSetting().withStartNumber(200))
        .withItems(new HridSetting().withStartNumber(300));

    hridManager.updateHridSettings(newHridSettings)
      .setHandler(testContext.asyncAssertSuccess(
          hridSettings -> hridManager.getNextItemHrid().compose(
              hrid -> validateHrid(hrid, "00000300", testContext))
            .setHandler(testContext.asyncAssertSuccess(
              v -> log.info("Finished canGetNextItemHridWithNoPrefix()")))));
  }

  @Test
  public void canRollbackFailedTransaction(TestContext testContext)
      throws InterruptedException, ExecutionException, TimeoutException {
    log.info("Starting canRollbackFailedTransaction()");

    final Vertx vertx = StorageTestSuite.getVertx();
    final PostgresClient postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);

    final HridManager hridManager = new HridManager(vertx.getOrCreateContext(), postgresClient);

    final HridSettings newHridSettings = new HridSettings()
        .withInstances(new HridSetting().withStartNumber(999999999))
        .withHoldings(new HridSetting().withStartNumber(200))
        .withItems(new HridSetting().withStartNumber(300));

    hridManager.getHridSettings()
        .compose(originalHridSettings -> {
          Promise<HridSettings> promise = Promise.promise();
          hridManager.updateHridSettings(newHridSettings).setHandler(ar -> {
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
                  return Promise.succeededPromise(currentHridSettings).future();
                })
                .setHandler(promise);
          });
          return promise.future();
        })
        .setHandler(testContext.asyncAssertSuccess(
            v1 -> log.info("Finished canRollbackFailedTransaction()")));
  }

  private Future<Void> validateHrid(String hrid, String expectedValue, TestContext testContext) {
    testContext.assertEquals(expectedValue, hrid);
    return Promise.<Void>succeededPromise().future();
  }
}
