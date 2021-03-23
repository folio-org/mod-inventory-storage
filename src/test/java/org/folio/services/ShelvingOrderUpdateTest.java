package org.folio.services;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import static org.folio.rest.api.StorageTestSuite.removeTenant;
import static org.folio.rest.api.StorageTestSuite.tenantOp;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.pgclient.PgConnection;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import lombok.SneakyThrows;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.helpers.LocalRowSet;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.folio.rest.api.TestBase;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.support.ShelvingOrderUpdate;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

@RunWith(VertxUnitRunner.class)
public class ShelvingOrderUpdateTest extends TestBase {

  private static final Logger log = LogManager.getLogger();

  private static final String VERSION_WITH_SHELVING_ORDER = "mod-inventory-storage-20.1.0";
  private static final String FROM_MODULE_DO_UPGRADE = "mod-inventory-storage-19.9.0";
  private static final String FROM_MODULE_SKIP_UPGRADE = "mod-inventory-storage-20.2.0";

  private static String SQL_UPDATE_REMOVE_ITEMS_PROPERTY = new StringBuilder()
      .append("UPDATE item ")
      .append("SET jsonb = jsonb - 'effectiveShelvingOrder' ").toString();

  private static String SQL_SELECT_ITEMS_COUNT = new StringBuilder()
      .append("SELECT COUNT(*) AS cnt ")
      .append("FROM item ").toString();

  private static String SQL_SELECT_ITEMS_AMOUNT_FOR_UPDATE = new StringBuilder()
      .append("SELECT COUNT(*) AS cnt ")
      .append("FROM item ")
      .append("WHERE NOT (jsonb ? 'effectiveShelvingOrder') ").toString();

  private static String defaultTenant;
  private static ShelvingOrderUpdate shelvingOrderUpdate;

  @BeforeClass
  @SneakyThrows
  public static void beforeClass(TestContext context) {
    shelvingOrderUpdate = ShelvingOrderUpdate.getInstance();
    defaultTenant = generateTenantValue();

    log.info("Default tenant initialization started: {}", defaultTenant);
    initTenant(defaultTenant, VERSION_WITH_SHELVING_ORDER);
    log.info("Default tenant initialization finished");
  }

  @AfterClass
  @SneakyThrows
  public static void afterClass(TestContext context) {
    removeTenant(defaultTenant);
  }

  @Test
  public void checkingShouldPassForOlderModuleVersions() {
    final TenantAttributes tenantAttributes = getTenantAttributes(FROM_MODULE_DO_UPGRADE);
    boolean result = shelvingOrderUpdate.isAllowedToUpdate(tenantAttributes);
    assertTrue("Module version eligible to upgrade", result);
  }

  @Test
  public void checkingShouldFailForNewerModuleVersions() {
    final TenantAttributes tenantAttributes = getTenantAttributes(FROM_MODULE_SKIP_UPGRADE);
    boolean result = shelvingOrderUpdate.isAllowedToUpdate(tenantAttributes);
    assertFalse("Module version isn't eligible to upgrade", result);
  }

  @Test
  public void checkingShouldFailForEmptyModuleVersionPassed() {
    final TenantAttributes tenantAttributes = getTenantAttributes(StringUtils.EMPTY);
    boolean result = shelvingOrderUpdate.isAllowedToUpdate(tenantAttributes);
    assertFalse("Empty module version isn't eligible to upgrade", result);
  }

  @Test
  public void checkingShouldFailForNullModuleVersionPassed() {
    final TenantAttributes tenantAttributes = getTenantAttributes(null);
    boolean result = shelvingOrderUpdate.isAllowedToUpdate(tenantAttributes);
    assertFalse("Empty module version isn't eligible to upgrade", result);
  }

  @Test
  public void checkingShouldFailForMissedModuleVersionPassed() {
    final TenantAttributes tenantAttributes = new TenantAttributes();
    boolean result = shelvingOrderUpdate.isAllowedToUpdate(tenantAttributes);
    assertFalse("Request without specified module version isn't eligible to upgrade", result);
  }

  @Test
  public void shouldSucceedItemsUpdateForExpectedModuleVersion() {
    log.info("The test \"shouldSucceedItemsUpdateForExpectedModuleVersion\" started...");
//    String tenant = generateTenantValue();
//    log.info("Tenant generated: {}", tenant);
//
//    log.info("Tenant initialization started: {}", tenant);
//    initTenant(tenant, VERSION_WITH_SHELVING_ORDER);
//    log.info("Tenant initialization finished");

    // Check total items
    RowSet<Row> result = executeSql(SQL_SELECT_ITEMS_COUNT);
    int itemsTotal = result.iterator().next().getInteger(0);
    log.info("There are {} items total", itemsTotal);

    // Prepare items for update
    removeItemsProperty((defaultTenant));

    // Check amount of items for update
    int expectedItemsCountForUpdate = getItemsForUpdateCount();
    log.info("There are {} items to update", expectedItemsCountForUpdate);
    assertTrue("The are expected items for update", expectedItemsCountForUpdate > 0);

    // Get operation result
    boolean operationResult = doModuleUpgrade(defaultTenant, FROM_MODULE_DO_UPGRADE);
    log.info("The result of doModuleUpgrade execution", operationResult);

    // Check amount of items after update
    int expectedItemsCountAfterUpdate = getItemsForUpdateCount();
    log.info("There are {} items after update", expectedItemsCountAfterUpdate);
    assertTrue("No items should remain after update", expectedItemsCountAfterUpdate == 0);

//    log.info("Tenant deletion started: {}", tenant);
//    deleteTenant(tenant);
//    log.info("Tenant deletion finished");
    log.info("The test \"shouldSucceedItemsUpdateForExpectedModuleVersion\" finished");
  }

  @Test
  public void shouldFailItemsUpdateForUnexpectedModuleVersion() {
    log.info("The test \"shouldFailItemsUpdateForUnexpectedModuleVersion\" started...");
//    String tenant = generateTenantValue();
//    log.info("Tenant generated: {}", tenant);
//
//    log.info("Tenant initialization started: {}", tenant);
//    initTenant(tenant, VERSION_WITH_SHELVING_ORDER);
//    log.info("Tenant initialization finished");

    // Check total items
    RowSet<Row> result = executeSql(SQL_SELECT_ITEMS_COUNT);
    int itemsTotal = result.iterator().next().getInteger(0);
    log.info("There are {} items total", itemsTotal);

    // Prepare items for update
    removeItemsProperty((defaultTenant));

    // Check amount of items for update
    int expectedItemsCountForUpdate = getItemsForUpdateCount();
    log.info("There are {} items to update", expectedItemsCountForUpdate);
    assertTrue("The are expected items for update", expectedItemsCountForUpdate > 0);

    // Get operation result
    boolean operationResult = doModuleUpgrade(defaultTenant, FROM_MODULE_SKIP_UPGRADE);
    log.info("The result of doModuleUpgrade execution: {}", operationResult);

    // Check amount of items after update
    int expectedItemsCountAfterUpdate = getItemsForUpdateCount();
    log.info("There are {} items after update", expectedItemsCountAfterUpdate);
    assertTrue("No items should remain after update", expectedItemsCountAfterUpdate == 0);

//    log.info("Tenant deletion started: {}", tenant);
//    deleteTenant(tenant);
//    log.info("Tenant deletion finished");
    log.info("The test \"shouldFailItemsUpdateForUnexpectedModuleVersion\" finished");
  }

  @Ignore
  @Test
  public void acquiringOfConnectionShouldFail() {
    PostgresClient postgresClient =  Mockito.mock(PostgresClient.class);
    AsyncResult<PgConnection> asyncResult = Mockito.mock(AsyncResult.class);

    Mockito.doAnswer((Answer<AsyncResult<PgConnection>>) invocationOnMock -> {
      //((Handler<AsyncResult<PgConnection>>) invocationOnMock.getArgument(0)).handle(asyncResult);
      throw new RuntimeException("Connection can't be acquired by testing purposes");
    }).when(postgresClient).getConnection(Mockito.any());

    // Try to execute processing
    Future<Integer> future = shelvingOrderUpdate.fetchAndUpdatesItems(Vertx.vertx(), postgresClient);
    assertTrue("Connection acquiring should fail", future.failed());
  }

  /*
  * Internal purposes functions below
  */
  private static TenantAttributes getTenantAttributes(String moduleFrom) {
    return new TenantAttributes().withModuleTo(VERSION_WITH_SHELVING_ORDER)
        .withModuleFrom(moduleFrom);
  }

  private static String generateTenantValue() {
    String tenant = String.format("tenant_%s", RandomStringUtils.randomAlphanumeric(7));
    log.info("New tenant generated: {}", tenant);
    return tenant;
  }

  @SneakyThrows
  private static void postTenantOperation(String tenant, String fromModuleValue) {
    tenantOp(tenant, JsonObject.mapFrom(getTenantAttributes(fromModuleValue)));
  }

  @SneakyThrows
  private static void initTenant(String tenant, String initialModuleVersion) {
    tenantOp(tenant,
        JsonObject.mapFrom(new TenantAttributes().withModuleTo(initialModuleVersion)));
  }

  private static PostgresClient getPostgresClient(String tenant) {
    return PostgresClient.getInstance(Vertx.vertx(), tenant);
  }

  private boolean doModuleUpgrade(String tenant, String fromModuleVersion) {
    log.info("Started execution of the doModuleUpgrade ...");
    final Promise<Boolean> result = Promise.promise();
    shelvingOrderUpdate.setCompletionHandler(result);

    // Initiate post tenant operation
    log.info("Module upgrade started, fromModuleVersion: {}", fromModuleVersion);
    postTenantOperation(tenant, fromModuleVersion);

    result.future().onComplete(ar -> {
      log.info("doModuleUpgrade post tenant operation completed");
      shelvingOrderUpdate.setCompletionHandler(null);
      if (ar.succeeded()) {
        log.info("doModuleUpgrade post tenant operation succeed : {}", ar.result());
      } else {
        log.info("doModuleUpgrade post tenant operation failed: {}", ar.cause().getMessage());
      }
    });

    log.info("Finished execution of the doModuleUpgrade");
    return get(result.future());
  }

  @SneakyThrows
  private RowSet<Row> executeSql(String sql) {
    log.info("Started executeSql: {}", sql);
    final Promise<RowSet<Row>> result = Promise.promise();

    if (StringUtils.isBlank(sql)) {
      result.complete(new LocalRowSet(0));
      return get(result.future());
    }

    getPostgresClient(defaultTenant).execute(sql, executeResult -> {
      if (executeResult.failed()) {
        result.fail(executeResult.cause());
        log.info("Error: {}", executeResult.cause().getMessage());
      } else {
        result.complete(executeResult.result());
        log.info("Successfully executed: {}", executeResult.result());
      }
    });

    log.info("Finished executeSql: {}", result.future());
    return get(result.future());
  }

  private int getItemsForUpdateCount() {
    log.info("Starting of the getItemsForUpdateCount...");

    RowSet<Row> result = executeSql(SQL_SELECT_ITEMS_AMOUNT_FOR_UPDATE);
    int itemsCount = result.iterator().next().getInteger(0);

    log.info("There are {} items ready for updates", itemsCount);
    log.info("Finishing of the getItemsForUpdateCount");
    return itemsCount;
}

  private int removeItemsProperty(String tenant) {
    log.info("Starting of the removeItemsProperty...");

    RowSet<Row> result = executeSql(SQL_UPDATE_REMOVE_ITEMS_PROPERTY);
    int itemsAffected = result.rowCount();

    log.info("There were {} items updates", itemsAffected);
    log.info("Finishing of the removeItemsProperty");
    return itemsAffected;
 }

}
