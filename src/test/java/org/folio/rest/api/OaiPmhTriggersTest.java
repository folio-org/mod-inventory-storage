package org.folio.rest.api;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.folio.rest.api.StorageTestSuite.deleteAll;
import static org.folio.rest.support.http.InterfaceUrls.holdingsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.instancesStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.itemsStorageUrl;
import static org.folio.utility.ModuleUtility.getVertx;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.builders.ItemRequestBuilder;
import org.folio.rest.tools.utils.TenantTool;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class OaiPmhTriggersTest extends TestBaseWithInventoryUtil {

  private final PostgresClient postgresClient = PostgresClient.getInstance(getVertx(),
    TenantTool.calculateTenantId(TENANT_ID));

  @SneakyThrows
  @After
  public void afterEach() {
    deleteAll(itemsStorageUrl(""));
    deleteAll(holdingsStorageUrl(""));
    deleteAll(instancesStorageUrl(""));
  }

  @Test
  public void createInstanceTest() {
    instancesClient.create(instance(UUID.randomUUID()));
    verifyCompleteUpdatedDate(null);
  }

  @SneakyThrows
  @Test
  public void createItemTest() {
    var holdingId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
    CompletableFuture<LocalDateTime> future = new CompletableFuture<>();
    getCompleteUpdatedDate(future);
    var dateBeforeCreatingItem = future.get(80, TimeUnit.SECONDS);
    createItem(journalMaterialTypeId, holdingId);
    verifyCompleteUpdatedDate(dateBeforeCreatingItem);
  }

  @SneakyThrows
  @Test
  public void createHoldingsRecordTest() {
    var instanceId = UUID.randomUUID();
    instancesClient.create(instance(instanceId));
    CompletableFuture<LocalDateTime> future = new CompletableFuture<>();
    getCompleteUpdatedDate(future);
    var dateBeforeCreatingHolding = future.get(80, TimeUnit.SECONDS);
    createHolding(instanceId, MAIN_LIBRARY_LOCATION_ID, null);
    verifyCompleteUpdatedDate(dateBeforeCreatingHolding);
  }

  @SneakyThrows
  @Test
  public void updateInstanceTest() {
    var holdingId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
    createItem(journalMaterialTypeId, holdingId);
    CompletableFuture<LocalDateTime> futureDateBefore = new CompletableFuture<>();
    getCompleteUpdatedDate(futureDateBefore);
    var dateBeforeUpdatingInstance = futureDateBefore.get(80, TimeUnit.SECONDS);
    CompletableFuture<Void> future = new CompletableFuture<>();
    updateTable("instance", future);
    future.get(80, TimeUnit.SECONDS);
    verifyCompleteUpdatedDate(dateBeforeUpdatingInstance);
  }

  @SneakyThrows
  @Test
  public void updateItemTest() {
    var holdingId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
    createItem(journalMaterialTypeId, holdingId);
    CompletableFuture<LocalDateTime> futureDateBefore = new CompletableFuture<>();
    getCompleteUpdatedDate(futureDateBefore);
    var dateBeforeUpdatingItem = futureDateBefore.get(80, TimeUnit.SECONDS);
    CompletableFuture<Void> future = new CompletableFuture<>();
    updateTable("item", future);
    future.get(80, TimeUnit.SECONDS);
    verifyCompleteUpdatedDate(dateBeforeUpdatingItem);
  }

  @SneakyThrows
  @Test
  public void updateHoldingsRecordTest() {
    createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
    CompletableFuture<LocalDateTime> futureDateBefore = new CompletableFuture<>();
    getCompleteUpdatedDate(futureDateBefore);
    var dateBeforeUpdatingHolding = futureDateBefore.get(80, TimeUnit.SECONDS);
    CompletableFuture<Void> future = new CompletableFuture<>();
    updateTable("holdings_record", future);
    future.get(80, TimeUnit.SECONDS);
    verifyCompleteUpdatedDate(dateBeforeUpdatingHolding);
  }

  @SneakyThrows
  @Test
  public void deleteHoldingsRecordTest() {
    createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
    CompletableFuture<LocalDateTime> futureDateBefore = new CompletableFuture<>();
    getCompleteUpdatedDate(futureDateBefore);
    var dateBeforeDeletingHolding = futureDateBefore.get(80, TimeUnit.SECONDS);
    deleteAll(holdingsStorageUrl(""));
    verifyCompleteUpdatedDate(dateBeforeDeletingHolding);
  }

  @SneakyThrows
  @Test
  public void deleteItemTest() {
    var holdingId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
    createItem(journalMaterialTypeId, holdingId);
    CompletableFuture<LocalDateTime> futureDateBefore = new CompletableFuture<>();
    getCompleteUpdatedDate(futureDateBefore);
    var dateBeforeDeletingItem = futureDateBefore.get(80, TimeUnit.SECONDS);
    deleteAll(itemsStorageUrl(""));
    verifyCompleteUpdatedDate(dateBeforeDeletingItem);
  }

  private void updateTable(String table, CompletableFuture<Void> future) {
    postgresClient.execute("UPDATE " + TENANT_ID + "_mod_inventory_storage." + table + " SET created_by = 'some user'")
      .onComplete(handler -> future.complete(null));
  }

  private void getCompleteUpdatedDate(CompletableFuture<LocalDateTime> future) {
    postgresClient.select("SELECT * FROM " + TENANT_ID + "_mod_inventory_storage.instance", handler -> {
      if (handler.succeeded()) {
        var result = handler.result();
        if (result.size() == 1) {
          var row = handler.result()
            .iterator()
            .next();
          if (row.toJson()
            .containsKey("complete_updated_date")) {
            var completeUpdatedDate = row.toJson()
              .getString("complete_updated_date");
            if (isNull(completeUpdatedDate)) {
              future.complete(null);
            } else {
              future.complete(LocalDateTime.parse(completeUpdatedDate, DateTimeFormatter.ISO_ZONED_DATE_TIME));
            }
          }
        }
      }
      future.completeExceptionally(new Exception("Cannot get from Instance"));
    });
  }

  private void verifyCompleteUpdatedDate(LocalDateTime dateBefore) {
    postgresClient.select("SELECT * FROM " + TENANT_ID + "_mod_inventory_storage.instance", handler -> {
      if (handler.succeeded()) {
        assertEquals(1, handler.result()
          .size());
        var row = handler.result()
          .iterator()
          .next();
        assertTrue(row.toJson()
          .containsKey("complete_updated_date"));
        var completeUpdatedDate = row.toJson()
          .getString("complete_updated_date");
        assertNotNull(completeUpdatedDate);
        if (nonNull(dateBefore)) {
          assertTrue(dateBefore.isBefore(LocalDateTime.parse(completeUpdatedDate,
            DateTimeFormatter.ISO_ZONED_DATE_TIME)));
        }
      }
    });
  }

  private void createItem(UUID journalMaterialTypeId, UUID holdingId) {
    super.createItem(createItemRequest(journalMaterialTypeId, holdingId).create());
  }

  private ItemRequestBuilder createItemRequest(UUID materialTypeId, UUID holdingId) {
    return new ItemRequestBuilder().forHolding(holdingId)
      .withPermanentLoanType(canCirculateLoanTypeId)
      .withTemporaryLocation(TestBaseWithInventoryUtil.MAIN_LIBRARY_LOCATION_ID)
      .withBarcode("item barcode")
      .withItemLevelCallNumber("item effective call number 1")
      .withMaterialType(materialTypeId);
  }
}
