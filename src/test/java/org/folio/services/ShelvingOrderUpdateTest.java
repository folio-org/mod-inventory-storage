package org.folio.services;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import static org.folio.rest.api.StorageTestSuite.prepareTenant;
import static org.folio.rest.api.StorageTestSuite.removeTenant;
import static org.folio.rest.api.StorageTestSuite.tenantOp;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import javax.annotation.concurrent.NotThreadSafe;

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
import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.http.InterfaceUrls;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import org.folio.rest.api.StorageTestSuite;
import org.folio.rest.api.TestBaseWithInventoryUtil;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.helpers.LocalRowSet;
import org.folio.rest.support.HttpClient;
import org.folio.rest.support.ShelvingOrderUpdate;

@NotThreadSafe
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(VertxUnitRunner.class)
public class ShelvingOrderUpdateTest extends TestBaseWithInventoryUtil {

  private static final Logger log = LogManager.getLogger();

  private static final String VERSION_WITH_SHELVING_ORDER = "mod-inventory-storage-20.1.0";
  private static final String FROM_MODULE_DO_UPGRADE = "mod-inventory-storage-19.9.0";
  private static final String FROM_MODULE_SKIP_UPGRADE = "mod-inventory-storage-20.2.0";

  private static String SQL_UPDATE_REMOVE_ITEMS_PROPERTY =
      "UPDATE item " +
      "SET jsonb = jsonb - 'effectiveShelvingOrder' ";

  private static String SQL_SELECT_ITEMS_COUNT =
      "SELECT COUNT(*) AS cnt " +
      "FROM item ";

  private static String SQL_SELECT_ITEMS_AMOUNT_FOR_UPDATE =
      "SELECT COUNT(*) AS cnt " +
      "FROM item " +
      "WHERE NOT (jsonb ? 'effectiveShelvingOrder') ";

  // Items raw data
  // (1)expectedShelvingOrder, (2)defaultExpectedDesiredShelvesOrder, (3)prefix, (4)callNumber,
  // (5)volume, (6)enumeration, (7)chronology, (8)copy, (9)suffix
  private static String[] ITEMS_DATA = new String[] {
      "0,1,PRFX1,CN1,VL1,EN1,CHR1,CPN1,SFX1",
      "0,1,PRFX2,CN2,VL2,EN2,CHR2,CPN2,SFX2",
      "0,1,PRFX3,CN3,VL3,EN3,CHR3,CPN3,SFX3",
      "0,1,PRFX4,CN4,VL4,EN4,CHR4,CPN4,SFX4",
      "0,1,PRFX5,CN5,VL5,EN5,CHR5,CPN5,SFX5",
      "0,1,PRFX6,CN6,VL6,EN6,CHR6,CPN6,SFX6",
      "0,1,PRFX7,CN7,VL7,EN7,CHR7,CPN7,SFX7",
      "0,1,PRFX8,CN8,VL8,EN8,CHR8,CPN8,SFX8",
      "0,1,PRFX9,CN9,VL9,EN9,CHR9,CPN9,SFX9",
      "0,1,PRFX10,CN10,VL10,EN10,CHR10,CPN10,SFX10",
      "0,1,PRFX11,CN11,VL11,EN11,CHR11,CPN11,SFX11",
      "0,1,PRFX12,CN12,VL12,EN12,CHR12,CPN12,SFX12",
      "0,1,PRFX13,CN13,VL13,EN13,CHR13,CPN13,SFX13",
      "0,1,PRFX14,CN14,VL14,EN14,CHR14,CPN14,SFX14",
      "0,1,PRFX15,CN15,VL15,EN15,CHR15,CPN15,SFX15",
      "0,1,PRFX16,CN16,VL16,EN16,CHR16,CPN16,SFX16",
      "0,1,PRFX17,CN17,VL17,EN17,CHR17,CPN17,SFX17",
      "0,1,PRFX18,CN18,VL18,EN18,CHR18,CPN18,SFX18",
      "0,1,PRFX19,CN19,VL19,EN19,CHR19,CPN19,SFX19",
      "0,1,PRFX20,CN20,VL20,EN20,CHR20,CPN20,SFX20"
  };

  private static String defaultTenant;
  private static ShelvingOrderUpdate shelvingOrderUpdate;
  private static HttpClient client;

  @BeforeClass
  @SneakyThrows
  public static void beforeClass(TestContext testContext) {
    shelvingOrderUpdate = ShelvingOrderUpdate.getInstance();
    defaultTenant = generateTenantValue();
//    defaultTenant = StorageTestSuite.TENANT_ID;
//    hackTestSuiteTenant(defaultTenant);

    // Insert items from tst data
//    insertTestingData(testContext);

    client = new HttpClient(Vertx.vertx());

    log.info("Default tenant initialization started: {}", defaultTenant);
    initTenant(defaultTenant, VERSION_WITH_SHELVING_ORDER);
    log.info("Default tenant initialization finished");
  }

  @AfterClass
  @SneakyThrows
  public static void afterClass(TestContext context) {
    removeTenant(defaultTenant);
  }

  @Test()
  public void testOrder1_checkingShouldPassForOlderModuleVersions() {
    log.info("The test \"checkingShouldPassForOlderModuleVersions\" started...");
    final TenantAttributes tenantAttributes = getTenantAttributes(FROM_MODULE_DO_UPGRADE);
    boolean result = shelvingOrderUpdate.isAllowedToUpdate(tenantAttributes);
    assertTrue("Module version eligible to upgrade", result);
    log.info("The test \"checkingShouldPassForOlderModuleVersions\" done");
  }

  @Test
  public void testOrder2_checkingShouldFailForNewerModuleVersions() {
    log.info("The test \"checkingShouldFailForNewerModuleVersions\" started...");
    final TenantAttributes tenantAttributes = getTenantAttributes(FROM_MODULE_SKIP_UPGRADE);
    boolean result = shelvingOrderUpdate.isAllowedToUpdate(tenantAttributes);
    assertFalse("Module version isn't eligible to upgrade", result);
    log.info("The test \"checkingShouldFailForNewerModuleVersions\" done");
  }

  @Test
  public void testOrder3_checkingShouldFailForEmptyModuleVersionPassed() {
    log.info("The test \"checkingShouldFailForEmptyModuleVersionPassed\" started...");
    final TenantAttributes tenantAttributes = getTenantAttributes(StringUtils.EMPTY);
    boolean result = shelvingOrderUpdate.isAllowedToUpdate(tenantAttributes);
    assertFalse("Empty module version isn't eligible to upgrade", result);
    log.info("The test \"checkingShouldFailForEmptyModuleVersionPassed\" done");
  }

  @Test
  public void testOrder4_checkingShouldFailForNullModuleVersionPassed() {
    log.info("The test \"checkingShouldFailForNullModuleVersionPassed\" started...");
    final TenantAttributes tenantAttributes = getTenantAttributes(null);
    boolean result = shelvingOrderUpdate.isAllowedToUpdate(tenantAttributes);
    assertFalse("Empty module version isn't eligible to upgrade", result);
    log.info("The test \"checkingShouldFailForNullModuleVersionPassed\" done");
  }

  @Test
  public void testOrder5_checkingShouldFailForMissedModuleVersionPassed() {
    log.info("The test \"checkingShouldFailForMissedModuleVersionPassed\" started...");
    final TenantAttributes tenantAttributes = new TenantAttributes();
    boolean result = shelvingOrderUpdate.isAllowedToUpdate(tenantAttributes);
    assertFalse("Request without specified module version isn't eligible to upgrade", result);
    log.info("The test \"checkingShouldFailForMissedModuleVersionPassed\" done");
  }

  @Ignore
  @Test
  public void testOrder6_shouldSucceedItemsUpdateForExpectedModuleVersion(TestContext testContext) {
    log.info("The test \"shouldSucceedItemsUpdateForExpectedModuleVersion\" started...");

    // Check total items
    executeCountSQL(SQL_SELECT_ITEMS_COUNT)
        .onComplete(testContext.asyncAssertSuccess(value -> log.info("There are {} items total", value)));

    // Prepare items for update
    executeUpdateSQL(SQL_UPDATE_REMOVE_ITEMS_PROPERTY)
        .onComplete(testContext.asyncAssertSuccess(value -> log.info("There were {} items processed with removing property routine...", value)));

    // Check amount of items for update
    executeCountSQL(SQL_SELECT_ITEMS_AMOUNT_FOR_UPDATE)
        .onComplete(testContext.asyncAssertSuccess(value -> {
          log.info("There are {} items to update", value);
          assertTrue("The are expected items for update", value > 0);
        }));

    // Get operation result
    doModuleUpgrade(defaultTenant, FROM_MODULE_DO_UPGRADE);

    // Check amount of items after update
    executeCountSQL(SQL_SELECT_ITEMS_AMOUNT_FOR_UPDATE)
        .onComplete(testContext.asyncAssertSuccess(value -> {
          log.info("There are {} items after update", value);
          assertTrue("No items should remain after update", value == 0);
        }));

    log.info("The test \"shouldSucceedItemsUpdateForExpectedModuleVersion\" finished");
  }

  @Ignore
  @Test
  public void testOrder7_shouldFailItemsUpdateForUnexpectedModuleVersion(TestContext testContext) {
    log.info("The test \"shouldFailItemsUpdateForUnexpectedModuleVersion\" started...");

    // Check total items
    executeCountSQL(SQL_SELECT_ITEMS_COUNT)
        .onComplete(testContext.asyncAssertSuccess(value -> log.info("There are {} items total", value)));

    // Prepare items for update
    executeUpdateSQL(SQL_UPDATE_REMOVE_ITEMS_PROPERTY)
        .onComplete(testContext.asyncAssertSuccess(value ->
            log.info("There were {} items processed with removing property routine...", value)));

    // Check amount of items for update
    executeCountSQL(SQL_SELECT_ITEMS_AMOUNT_FOR_UPDATE)
        .onComplete(testContext.asyncAssertSuccess(value -> {
          log.info("There are {} items to update", value);
          assertTrue("The are expected items for update", value > 0);
        }));

    // Get operation result
    doModuleUpgrade(defaultTenant, FROM_MODULE_SKIP_UPGRADE);

    // Check amount of items after update
    executeCountSQL(SQL_SELECT_ITEMS_AMOUNT_FOR_UPDATE)
        .onComplete(testContext.asyncAssertSuccess(value -> {
          log.info("There are {} items after update", value);
          assertTrue("No items should remain after update", value == 0);
        }));

    log.info("The test \"shouldFailItemsUpdateForUnexpectedModuleVersion\" finished");
  }

  @Test
  public void testOrder8_shouldSucceedInsertedItemsUpdateWithExpectedModuleFrom(TestContext testContext) {
    log.info("The test \"shouldSucceedInsertedItemsUpdateWithExpectedModuleFrom\" started...");

    // Check total items
    executeCountSQL(SQL_SELECT_ITEMS_COUNT)
        .onComplete(testContext.asyncAssertSuccess(value -> log.info("There are {} items total", value)));
//    log.info("There are {} items total", executeCountSQL(SQL_SELECT_ITEMS_COUNT));

    // Prepare items for update
    executeCountSQL(SQL_SELECT_ITEMS_AMOUNT_FOR_UPDATE)
      .onComplete(testContext.asyncAssertSuccess(value -> log.info("Before property removing amount of items: {}", value)))
    .compose(countForUpdateValue -> executeUpdateSQL(SQL_UPDATE_REMOVE_ITEMS_PROPERTY))
      .onComplete(testContext.asyncAssertSuccess(value ->  log.info("There were {} items processed with removing property routine...", value)))
    .compose(amountOfUpdatedValue -> executeCountSQL(SQL_SELECT_ITEMS_AMOUNT_FOR_UPDATE))
      .onComplete(testContext.asyncAssertSuccess(value -> log.info("After property removing amount of items: {}", value)));


    // Insert items test data
//    insertTestingData(testContext);

     // Check amount of items for update
    executeCountSQL(SQL_SELECT_ITEMS_AMOUNT_FOR_UPDATE)
        .onComplete(testContext.asyncAssertSuccess(value -> {
          log.info("There are {} items to update", value);
//          assertTrue("The are expected items for update", value > 0);
    }));

    // Get operation result
    log.info("Starting startUpdatingOfItems, default tenant: {}", defaultTenant);
    Future<Integer> updatedAmountFuture = ShelvingOrderUpdate
        .getInstance()
        .withLimit(3)
        .startUpdatingOfItems(getTenantAttributes(FROM_MODULE_DO_UPGRADE),
            Map.of("x-okapi-tenant", defaultTenant), Vertx.vertx().getOrCreateContext())
            .onComplete(testContext.asyncAssertSuccess(value -> log.info("There were {} items updated", value)))
        .compose(amountOfUpdatedValue -> executeCountSQL(SQL_SELECT_ITEMS_AMOUNT_FOR_UPDATE))
            .onComplete(testContext.asyncAssertSuccess(value -> {
              log.info("There are {} items after update", value);
              assertTrue("No items should remain after update", value == 0);
              log.info("The test \"shouldSucceedInsertedItemsUpdateWithExpectedModuleFrom\" finished");
            }));

    log.info("Finished startUpdatingOfItems, updatedAmountFuture: {}", updatedAmountFuture);
  }

  @Ignore
  @Test
  public void testOrder9_acquiringOfConnectionShouldFail() {
    PostgresClient postgresClient =  Mockito.mock(PostgresClient.class);
    AsyncResult<PgConnection> asyncResult = Mockito.mock(AsyncResult.class);

    Mockito.doAnswer((Answer<AsyncResult<PgConnection>>) invocationOnMock -> {
      //((Handler<AsyncResult<PgConnection>>) invocationOnMock.getArgument(0)).handle(asyncResult);
      throw new RuntimeException("Connection can't be acquired by testing purposes");
    }).when(postgresClient).getConnection(Mockito.any());

    // Try to execute processing
    Future<Integer> future = shelvingOrderUpdate.fetchAndUpdateAll(Vertx.vertx(), postgresClient);
    assertTrue("Connection acquiring should fail", future.failed());
  }

  private static void insertTestingData(TestContext testContext) {
    // Insert items from tst data
    executeCountSQL(SQL_SELECT_ITEMS_AMOUNT_FOR_UPDATE)
        .onComplete(testContext.asyncAssertSuccess(value -> {
          log.info("Before insert amount of items: {}", value);
          insertItemsFromData();
        }))
        .compose(countForUpdateValue -> executeCountSQL(SQL_SELECT_ITEMS_AMOUNT_FOR_UPDATE))
        .onComplete(testContext.asyncAssertSuccess(value -> log.info("After insert amount of items: {}", value)));
  }

  /*
  * Internal purposes functions below
  */

  private static <T> T getFutureResult(Future<T> future) {
    log.info("Started \"getFutureResult\", future: {} ...", future);
    return getFutureResult(future.toCompletionStage().toCompletableFuture());
  }

  private static <T> T getFutureResult(CompletableFuture<T> completableFuture) {
    log.info("Started \"getFutureResult\", completableFuture: {} ...", completableFuture);
    try {
      return completableFuture.get(TIMEOUT, TimeUnit.SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      log.error("Error occurs in getFutureResult: {}", e.getMessage());
      throw new RuntimeException(e);
    }
  }

  private static IndividualResource createItemInternal(Item item) {
    CompletableFuture<Response> createCompleted = new CompletableFuture<>();
    JsonObject requestPayload = JsonObject.mapFrom(item);
    try {
      client.post(InterfaceUrls.itemsStorageUrl(StringUtils.EMPTY), requestPayload, defaultTenant,
          ResponseHandler.any(createCompleted));
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage(), e);
    }
    Response response = getFutureResult(createCompleted);
    return new IndividualResource(response);
  }

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
    log.info("Started \"postTenantOperation\", tenant: {}, fromModuleVersion: {} ...", tenant, fromModuleValue);
    tenantOp(tenant, JsonObject.mapFrom(getTenantAttributes(fromModuleValue)));
    log.info("Finished \"postTenantOperation\"");
  }

  @SneakyThrows
  private static void initTenant(String tenant, String initialModuleVersion) {
    log.info("Started \"initTenant\", tenant: {}, initialModuleVersion: {} ...", tenant, initialModuleVersion);
    prepareTenant(tenant, null, initialModuleVersion, true);
    log.info("Finished \"initTenant\"");
  }

  private static PostgresClient getPostgresClient(String tenant) {
    return PostgresClient.getInstance(Vertx.vertx(), tenant);
  }

  private void doModuleUpgrade(String tenant, String fromModuleVersion) {
    log.info("Started execution of the doModuleUpgrade, tenant: {}, fromModuleVersion: {} ...", tenant, fromModuleVersion);

    // Initiate post tenant operation
    log.info("Module upgrade started, fromModuleVersion: {}", fromModuleVersion);
    postTenantOperation(tenant, fromModuleVersion);

    log.info("doModuleUpgrade post tenant operation completed");
  }

  @SneakyThrows
  private static Future<RowSet<Row>> executeSql(String sql) {
    log.info("Started executeSql: {}", sql);

    RowSet<Row> result = new LocalRowSet(0);
    if (StringUtils.isNotBlank(sql)) {
      Future<RowSet<Row>> resultFuture = getPostgresClient(defaultTenant).execute(sql);
      log.info("Resulting execute SQL future: {}", resultFuture);
      return resultFuture;
      } else {
        log.info("Empty SQL was passed");
      return Future.succeededFuture();
      }
  }

  private static Future<Integer> executeCountSQL(String countSql) {
    log.info("Starting of the executeCountSQL, countSql: {}", countSql);
    Promise<Integer> promise = Promise.promise();

    executeSql(countSql).onComplete(ar -> {
      if (ar.succeeded()) {
        int rowCountResult = ar.result().iterator().next().getInteger(0);
        log.info("There is {} amount of entries returned", rowCountResult);
        promise.complete(rowCountResult);
      } else {
        log.info("Error occurs in executeCountSQL: {}", ar.cause().getMessage());
        promise.fail(ar.cause());
      }
      log.info("Finishing of the executeCountSQL");
    });

    return promise.future();
  }

  private Future<Integer> executeUpdateSQL(String updateSql) {
    log.info("Starting of the executeUpdateSQL, updateSql: {}", updateSql);
    Promise<Integer> promise = Promise.promise();

    executeSql(updateSql).onComplete(ar -> {
      if (ar.succeeded()) {
        int affectedCount = ar.result().rowCount();
        log.info("There were {} entries affected", affectedCount);
        promise.complete(affectedCount);
      } else {
        log.info("Error occurs in executeUpdateSQL: {}", ar.cause().getMessage());
        promise.fail(ar.cause());
      }
      log.info("Finishing of the executeUpdateSQL");
    });

    return promise.future();
  }

  private static void insertItemsFromData() {
    log.info("Starting of the insertItemsFromData...");
    // Prepare item list
    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    List<Item> items = Arrays.stream(ITEMS_DATA).map(e -> {
      log.info("map's e: {}", e);
      String[] itemData = StringUtils.split(e, ",");
      log.info("itemData: {}", itemData);
      Item item = buildItem(holdingsRecordId, null, null);
      log.info(": {}", item);

      String callNumberPrefix = itemData[2];
      String callNumber = itemData[3];
      String volume = itemData[4];
      String enumeration = itemData[5];
      String chronology = itemData[6];
      String copyNumber = itemData[7];
      String callNumberSuffix = itemData[8];
      log.info("callNumberPrefix: {}, callNumber: {}, volume: {}, enumeration: {}, chronology: {}, copyNumber: {}, callNumberSuffix: {}", callNumberPrefix, callNumber, volume, enumeration, chronology, copyNumber, callNumberSuffix);

      item.withItemLevelCallNumberPrefix(callNumberPrefix)
          .withItemLevelCallNumber(callNumber)
          .withVolume(volume)
          .withEnumeration(enumeration)
          .withChronology(chronology)
          .withCopyNumber(copyNumber)
          .withItemLevelCallNumberSuffix(callNumberSuffix);

      log.info("An item {} has been prepared", item);
      return item;
    }).collect(Collectors.toList());

    log.info("Items prepared, items: {}, count: {}", items, items.size());


    items.stream().forEach(i -> {
      createItemInternal(i);
      log.info("An item {} has been created...", i);
    });

    log.info("Finishing of the insertItemsFromData...");
  }

  /**
   * This is a kind of workaround of hardcoded TENANT_ID provided by StorageTetSuite.
   * Don't do this in common a way. Used by current tests only.
   *
   * @param tenant
   */
  private static void hackTestSuiteTenant(String tenant) {
    log.info("Started hackTestSuiteTenant, tenant {}", tenant);
    if (StringUtils.isBlank(tenant)) {
      log.info("Set up tenant is empty");
      return;
    }
    try {
      Field tenantField = StorageTestSuite.class.getDeclaredField("TENANT_ID");
      tenantField.setAccessible(true);

      // Clear final specifier
      Field modifiers = Field.class.getDeclaredField("modifiers");
      modifiers.setAccessible(true);
      modifiers.setInt(tenantField, tenantField.getModifiers() & ~Modifier.FINAL);
      tenantField.set(null, tenant);

      // Return back final specifier and accessible for field
      modifiers.setInt(tenantField, tenantField.getModifiers() & Modifier.FINAL);
      tenantField.setAccessible(false);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      log.error("There is an error occurred when changing suite's TENANT_ID to value: {}", tenant);
    }
    log.info("Finished hackTestSuiteTenant...");
  }

}
