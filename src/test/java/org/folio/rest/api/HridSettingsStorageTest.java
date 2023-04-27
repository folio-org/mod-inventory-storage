package org.folio.rest.api;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.rest.support.ResponseHandler.empty;
import static org.folio.rest.support.ResponseHandler.json;
import static org.folio.rest.support.ResponseHandler.text;
import static org.folio.utility.ModuleUtility.getClient;
import static org.folio.utility.ModuleUtility.getVertx;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertTrue;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.Timeout;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.sqlclient.Row;
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
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class HridSettingsStorageTest extends TestBase {
  private static final Logger log = LogManager.getLogger();

  @Rule
  public Timeout rule = Timeout.seconds(5);

  private final HridSettings initialHridSettings = new HridSettings()
    .withInstances(new HridSetting().withPrefix("in").withStartNumber(1L))
    .withHoldings(new HridSetting().withPrefix("ho").withStartNumber(1L))
    .withItems(new HridSetting().withPrefix("it").withStartNumber(1L));

  private final HridSettings initialHridSettingsWithoutLeadingZeroes = new HridSettings()
    .withInstances(new HridSetting().withPrefix("in").withStartNumber(1L))
    .withHoldings(new HridSetting().withPrefix("ho").withStartNumber(1L))
    .withItems(new HridSetting().withPrefix("it").withStartNumber(1L))
    .withCommonRetainLeadingZeroes(false);

  private Vertx vertx;
  private PostgresClient postgresClient;
  private HridManager hridManager;

  @SneakyThrows
  @Before
  public void beforeEach(TestContext testContext) {
    log.info("Initializing values");
    final Async async = testContext.async();
    vertx = getVertx();
    postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);
    hridManager = new HridManager(vertx.getOrCreateContext(), postgresClient);
    hridManager.updateHridSettings(initialHridSettings).onComplete(hridSettings -> {
      // We need to do this in cases where tests do not update the start number. In this
      // case, calling updateHridSettings will not update the sequences since the start number
      // has not changed. Since tests are executed in a non-deterministic order, we need to
      // ensure that the sequences are reset to 1 on start and that the next HRID value will be
      // use 1 as the number component.
      Promise<Row> instancePromise = Promise.promise();
      Promise<Row> holdingPromise = Promise.promise();
      Promise<Row> itemPromise = Promise.promise();
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

    removeAllEvents();
  }

  @Test
  public void canRetrieveHridSettings()
    throws InterruptedException, ExecutionException, TimeoutException {
    log.info("Starting canRetrieveHridSettings()");
    final CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    getClient().get(InterfaceUrls.hridSettingsStorageUrl(""), TENANT_ID, json(getCompleted));

    final Response response = getCompleted.get(10, SECONDS);

    assertThat(response.getStatusCode(), is(200));

    final HridSettings actualHridSettings = response.getJson().mapTo(HridSettings.class);

    assertThat(actualHridSettings.getInstances(), is(notNullValue()));
    assertThat(actualHridSettings.getInstances().getPrefix(), is("in"));
    assertThat(actualHridSettings.getInstances().getStartNumber(), is(1L));

    assertThat(actualHridSettings.getHoldings(), is(notNullValue()));
    assertThat(actualHridSettings.getHoldings().getPrefix(), is("ho"));
    assertThat(actualHridSettings.getHoldings().getStartNumber(), is(1L));

    assertThat(actualHridSettings.getItems(), is(notNullValue()));
    assertThat(actualHridSettings.getItems().getPrefix(), is("it"));
    assertThat(actualHridSettings.getItems().getStartNumber(), is(1L));

    assertThat(actualHridSettings.getCommonRetainLeadingZeroes(), is(true));

    log.info("Finished canRetrieveHridSettings()");
  }

  @Test
  public void cannotRetrieveHridSettingsWithBadTenant()
    throws InterruptedException, ExecutionException, TimeoutException {
    log.info("Starting cannotRetrieveHridSettingsWithBadTenant()");
    final CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    getClient().get(InterfaceUrls.hridSettingsStorageUrl(""), "BAD", text(getCompleted));

    final Response response = getCompleted.get(10, SECONDS);

    assertThat(response.getStatusCode(), is(500));

    log.info("Finished cannotRetrieveHridSettingsWithBadTenant()");
  }

  @Test
  public void canUpdateHridSettings()
    throws InterruptedException, ExecutionException, TimeoutException {
    log.info("Starting canUpdateHridSettings()");

    final CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    final HridSettings newHridSettings = new HridSettings()
      .withInstances(new HridSetting().withPrefix("inst").withStartNumber(100L))
      .withHoldings(new HridSetting().withPrefix("hold").withStartNumber(200L))
      .withItems(new HridSetting().withPrefix("item").withStartNumber(500L));

    getClient().put(InterfaceUrls.hridSettingsStorageUrl(""), newHridSettings, TENANT_ID,
      empty(putCompleted));

    final Response putResponse = putCompleted.get(10, SECONDS);

    assertThat(putResponse.getStatusCode(), is(204));

    final CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    getClient().get(InterfaceUrls.hridSettingsStorageUrl(""), TENANT_ID, json(getCompleted));

    final Response getResponse = getCompleted.get(10, SECONDS);

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
  public void cannotUpdateHridSettingsId()
    throws InterruptedException, ExecutionException, TimeoutException {
    log.info("Starting cannotUpdateHridSettingsID()");

    final CompletableFuture<Response> originalGetCompleted = new CompletableFuture<>();

    getClient().get(InterfaceUrls.hridSettingsStorageUrl(""), TENANT_ID, json(originalGetCompleted));

    final Response originalGetResponse = originalGetCompleted.get(10, SECONDS);

    assertThat(originalGetResponse.getStatusCode(), is(200));

    final HridSettings originalHridSettings =
      originalGetResponse.getJson().mapTo(HridSettings.class);

    final String uuid = UUID.randomUUID().toString();

    final HridSettings newHridSettings = new HridSettings()
      .withId(uuid)
      .withInstances(new HridSetting().withPrefix("inst").withStartNumber(100L))
      .withHoldings(new HridSetting().withPrefix("hold").withStartNumber(200L))
      .withItems(new HridSetting().withPrefix("item").withStartNumber(500L));

    final CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    getClient().put(InterfaceUrls.hridSettingsStorageUrl(""), newHridSettings, TENANT_ID,
      empty(putCompleted));

    final Response putResponse = putCompleted.get(10, SECONDS);

    assertThat(putResponse.getStatusCode(), is(204));

    final CompletableFuture<Response> getAfterUpdateCompleted = new CompletableFuture<>();

    getClient().get(InterfaceUrls.hridSettingsStorageUrl(""), TENANT_ID, json(getAfterUpdateCompleted));

    final Response getAfterUpdateResponse = getAfterUpdateCompleted.get(10, SECONDS);

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
  public void canGetNextInstanceHrid(TestContext testContext) {
    log.info("Starting canGetNextInstanceHrid()");

    getNextInstanceHrid()
      .compose(hrid -> validateHrid(hrid, "in00000000001", testContext))
      .onComplete(testContext.asyncAssertSuccess(
        v -> log.info("Finished canGetNextInstanceHrid()")));
  }

  @Test
  public void canGetNextInstanceHridWithoutLeadingZeroes(TestContext testContext) {
    log.info("Starting canGetNextInstanceHrid()");

    hridManager.updateHridSettings(initialHridSettingsWithoutLeadingZeroes).onComplete(
      testContext.asyncAssertSuccess(hridSettingsResult -> getNextInstanceHrid()
        .compose(hrid -> validateHrid(hrid, "in1", testContext))
        .onComplete(testContext.asyncAssertSuccess(
          v -> log.info("Finished canGetNextInstanceHridWithoutLeadingZeroes()"))))
    );
  }

  @Test
  public void canGetNextInstanceHridAfterSettingStartNumber(TestContext testContext) {
    log.info("Starting canGetNextInstanceHridAfterSettingStartNumber()");

    final HridSettings newHridSettings = new HridSettings()
      .withInstances(new HridSetting().withPrefix("in").withStartNumber(250L))
      .withHoldings(new HridSetting().withPrefix("ho").withStartNumber(1L))
      .withItems(new HridSetting().withPrefix("it").withStartNumber(1L));

    hridManager.updateHridSettings(newHridSettings).onComplete(
      testContext.asyncAssertSuccess(
        hridSettingsResult -> getNextInstanceHrid().compose(
            hrid -> validateHrid(hrid, "in00000000250", testContext))
          .onComplete(testContext.asyncAssertSuccess(
            v -> log.info("Finished canGetNextInstanceHridAfterSettingStartNumber()")))));
  }

  @Test
  public void canGetNextInstanceHridAfterSettingStartNumberWithoutLeadingZeroes(TestContext testContext) {
    log.info("Starting canGetNextInstanceHridAfterSettingStartNumberWithoutLeadingZeroes()");

    final HridSettings newHridSettings = new HridSettings()
      .withInstances(new HridSetting().withPrefix("in").withStartNumber(250L))
      .withHoldings(new HridSetting().withPrefix("ho").withStartNumber(1L))
      .withItems(new HridSetting().withPrefix("it").withStartNumber(1L))
      .withCommonRetainLeadingZeroes(false);

    hridManager.updateHridSettings(newHridSettings).onComplete(
      testContext.asyncAssertSuccess(
        hridSettingsResult -> getNextInstanceHrid().compose(
            hrid -> validateHrid(hrid, "in250", testContext))
          .onComplete(testContext.asyncAssertSuccess(
            v -> log.info("Finished canGetNextInstanceHridAfterSettingStartNumberWithoutLeadingZeroes()")))));
  }

  @Test
  public void canGetNextHoldingHrid(TestContext testContext) {
    log.info("Starting canGetNextHoldingHrid()");

    hridManager.populateHrid(new HoldingsRecord())
      .map(HoldingsRecord::getHrid)
      .compose(hrid -> validateHrid(hrid, "ho00000000001", testContext))
      .onComplete(testContext.asyncAssertSuccess(
        v -> log.info("Finished canGetNextHoldingHrid()")));
  }

  @Test
  public void canGetNextHoldingHridWithoutLeadingZeroes(TestContext testContext) {
    log.info("Starting canGetNextHoldingHridWithoutLeadingZeroes()");

    hridManager.updateHridSettings(initialHridSettingsWithoutLeadingZeroes).onComplete(
      testContext.asyncAssertSuccess(hridSettingsResult -> getNextHoldingsHrid()
        .compose(hrid -> validateHrid(hrid, "ho1", testContext))
        .onComplete(testContext.asyncAssertSuccess(
          v -> log.info("Finished canGetNextHoldingHridWithoutLeadingZeroes()"))))
    );
  }

  @Test
  public void canGetNextHoldingHridAfterSettingStartNumber(TestContext testContext) {
    log.info("Starting canGetNextHoldingHridAfterSettingStartNumber()");

    final HridSettings newHridSettings = new HridSettings()
      .withInstances(new HridSetting().withPrefix("in").withStartNumber(1L))
      .withHoldings(new HridSetting().withPrefix("ho").withStartNumber(7890L))
      .withItems(new HridSetting().withPrefix("it").withStartNumber(1L));

    hridManager.updateHridSettings(newHridSettings).onComplete(
      testContext.asyncAssertSuccess(
        hridSettings -> getNextHoldingsHrid().compose(
            hrid -> validateHrid(hrid, "ho00000007890", testContext))
          .onComplete(testContext.asyncAssertSuccess(
            v -> log.info("Finished canGetNextHoldingHridAfterSettingStartNumber()")))));
  }

  @Test
  public void canGetNextHoldingHridAfterSettingStartNumberWithoutLeadingZeroes(TestContext testContext) {
    log.info("Starting canGetNextHoldingHridAfterSettingStartNumberWithoutLeadingZeroes()");

    final HridSettings newHridSettings = new HridSettings()
      .withInstances(new HridSetting().withPrefix("in").withStartNumber(1L))
      .withHoldings(new HridSetting().withPrefix("ho").withStartNumber(7890L))
      .withItems(new HridSetting().withPrefix("it").withStartNumber(1L))
      .withCommonRetainLeadingZeroes(false);

    hridManager.updateHridSettings(newHridSettings).onComplete(
      testContext.asyncAssertSuccess(
        hridSettings -> getNextHoldingsHrid().compose(
            hrid -> validateHrid(hrid, "ho7890", testContext))
          .onComplete(testContext.asyncAssertSuccess(
            v -> log.info("Finished canGetNextHoldingHridAfterSettingStartNumberWithoutLeadingZeroes()")))));
  }

  @Test
  public void canGetNextItemHrid(TestContext testContext) {
    log.info("Starting canGetNextItemHrid()");

    getNextItemHrid()
      .compose(hrid -> validateHrid(hrid, "it00000000001", testContext))
      .onComplete(testContext.asyncAssertSuccess(v -> log.info("Finished canGetNextItemHrid()")));
  }

  @Test
  public void canGetNextItemHridAfterSettingStartNumber(TestContext testContext) {
    log.info("Starting canGetNextItemHridAfterSettingStartNumber()");

    final HridSettings newHridSettings = new HridSettings()
      .withInstances(new HridSetting().withPrefix("in").withStartNumber(1L))
      .withHoldings(new HridSetting().withPrefix("ho").withStartNumber(1L))
      .withItems(new HridSetting().withPrefix("it").withStartNumber(87654321L));

    hridManager.updateHridSettings(newHridSettings).onComplete(
      testContext.asyncAssertSuccess(
        hridSettings -> getNextItemHrid().compose(
            hrid -> validateHrid(hrid, "it00087654321", testContext))
          .onComplete(testContext.asyncAssertSuccess(
            v -> log.info("Finished canGetNextItemHridAfterSettingStartNumber()")))));
  }

  @Test
  public void canGetNextItemHridMultipleTimes(TestContext testContext) {
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
      .onComplete(testContext.asyncAssertSuccess(
        v -> log.info("Finished canGetNextItemHridMultipleTimes()")));
  }

  @Test
  public void canGetNextItemHridWithNoPrefix(TestContext testContext) {
    log.info("Starting canGetNextItemHridWithNoPrefix()");

    final HridSettings newHridSettings = new HridSettings()
      .withInstances(new HridSetting().withStartNumber(100L))
      .withHoldings(new HridSetting().withStartNumber(200L))
      .withItems(new HridSetting().withStartNumber(300L));

    hridManager.updateHridSettings(newHridSettings)
      .onComplete(testContext.asyncAssertSuccess(
        hridSettings -> getNextItemHrid().compose(
            hrid -> validateHrid(hrid, "00000000300", testContext))
          .onComplete(testContext.asyncAssertSuccess(
            v -> log.info("Finished canGetNextItemHridWithNoPrefix()")))));
  }

  @Test
  public void canRollbackFailedTransaction(TestContext testContext) {
    log.info("Starting canRollbackFailedTransaction()");

    final HridSettings newHridSettings = new HridSettings()
      .withInstances(new HridSetting().withStartNumber(999_999_999_999L))
      .withHoldings(new HridSetting().withStartNumber(200L))
      .withItems(new HridSetting().withStartNumber(300L));

    hridManager.getHridSettings()
      .compose(originalHridSettings -> {
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
      })
      .onComplete(testContext.asyncAssertSuccess(
        v1 -> log.info("Finished canRollbackFailedTransaction()")));
  }

  @Test
  public void canGetNextHridWhenStartNumberIsLong(TestContext testContext) {
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
      .onComplete(testContext.asyncAssertSuccess());
  }

  @Test
  public void canGetNextHridWhenStartNumberIsLongWithoutLeadingZeroes(TestContext testContext) {
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
      .onComplete(testContext.asyncAssertSuccess(
        v1 -> log.info("Finished canGetNextHridWhenStartNumberIsLongWithoutLeadingZeroes()")));
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

  private Future<String> validateHrid(String hrid, String expectedValue, TestContext testContext) {
    testContext.assertEquals(expectedValue, hrid);
    return Future.succeededFuture(hrid);
  }
}
