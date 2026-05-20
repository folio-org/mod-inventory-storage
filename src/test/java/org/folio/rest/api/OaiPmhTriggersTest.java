package org.folio.rest.api;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.folio.rest.api.StorageTestSuite.deleteAll;
import static org.folio.rest.support.http.InterfaceUrls.boundWithPartsUrl;
import static org.folio.rest.support.http.InterfaceUrls.holdingsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.instancesStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.itemsStorageUrl;
import static org.folio.utility.ModuleUtility.getVertx;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.builders.BoundWithPartBuilder;
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
  public void updateBoundWithItemTest() {
    var holdingId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
    var holding2Id = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
    var itemJson = createItem(journalMaterialTypeId, holdingId);
    final var boundWith = boundWithClient.create(new BoundWithPartBuilder(holding2Id,
      UUID.fromString(itemJson.getString("id"))));
    CompletableFuture<Map<String, LocalDateTime>> futureDates = new CompletableFuture<>();
    getCompleteUpdatedDates(futureDates);
    final var datesBeforeUpdatingItem = futureDates.get(80, TimeUnit.SECONDS);
    CompletableFuture<Void> future = new CompletableFuture<>();
    updateTable("item", future);
    future.get(80, TimeUnit.SECONDS);
    futureDates = new CompletableFuture<>();
    getCompleteUpdatedDates(futureDates);
    var datesAfterUpdatingItem = futureDates.get(80, TimeUnit.SECONDS);

    assertEquals(datesBeforeUpdatingItem.keySet(), datesAfterUpdatingItem.keySet());
    assertTrue(datesBeforeUpdatingItem.entrySet().stream()
      .allMatch(entry -> entry.getValue().isBefore(datesAfterUpdatingItem.get(entry.getKey()))));

    deleteAll(boundWithPartsUrl("/" + boundWith.getId()));
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
  public void moveHoldingsRecordToAnotherInstanceTest() {
    var sourceInstanceId = UUID.randomUUID();
    var targetInstanceId = UUID.randomUUID();
    instancesClient.create(instance(sourceInstanceId));
    instancesClient.create(instance(targetInstanceId));
    var holdingId = createHolding(sourceInstanceId, MAIN_LIBRARY_LOCATION_ID, null);

    var futureDates = new CompletableFuture<Map<String, LocalDateTime>>();
    getCompleteUpdatedDates(futureDates);
    final var datesBefore = futureDates.get(80, TimeUnit.SECONDS);

    // Move the holding to the target instance
    var holdingJson = holdingsClient.getById(holdingId).getJson();
    holdingJson.put("instanceId", targetInstanceId.toString());
    updateHoldingRecord(holdingId, holdingJson);

    futureDates = new CompletableFuture<>();
    getCompleteUpdatedDates(futureDates);
    var datesAfter = futureDates.get(80, TimeUnit.SECONDS);

    assertEquals(datesBefore.keySet(), datesAfter.keySet());
    assertTrue("Source instance complete_updated_date should have been updated",
      datesBefore.get(sourceInstanceId.toString()).isBefore(datesAfter.get(sourceInstanceId.toString())));
    assertTrue("Target instance complete_updated_date should have been updated",
      datesBefore.get(targetInstanceId.toString()).isBefore(datesAfter.get(targetInstanceId.toString())));
  }

  @SneakyThrows
  @Test
  public void moveItemToAnotherHoldingsRecordTest() {
    var sourceHoldingId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
    var targetHoldingId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
    var itemJson = createItem(journalMaterialTypeId, sourceHoldingId);

    var futureDates = new CompletableFuture<Map<String, LocalDateTime>>();
    getCompleteUpdatedDates(futureDates);
    final var datesBefore = futureDates.get(80, TimeUnit.SECONDS);

    // Move the item to the target holding
    itemJson.put("holdingsRecordId", targetHoldingId.toString());
    var itemId = itemJson.getString("id");
    var putResponse = itemsClient.attemptToReplace(itemId, itemJson);
    assertThat("Failed to move item: " + putResponse.getBody(),
      putResponse.getStatusCode(), is(204));

    futureDates = new CompletableFuture<>();
    getCompleteUpdatedDates(futureDates);
    var datesAfter = futureDates.get(80, TimeUnit.SECONDS);

    // Resolve parent instance IDs from holdings
    var sourceInstanceId = holdingsClient.getById(sourceHoldingId).getJson().getString("instanceId");
    var targetInstanceId = holdingsClient.getById(targetHoldingId).getJson().getString("instanceId");

    assertEquals(datesBefore.keySet(), datesAfter.keySet());
    assertTrue("Source instance complete_updated_date should have been updated after item move",
      datesBefore.get(sourceInstanceId).isBefore(datesAfter.get(sourceInstanceId)));
    assertTrue("Target instance complete_updated_date should have been updated after item move",
      datesBefore.get(targetInstanceId).isBefore(datesAfter.get(targetInstanceId)));
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

  private void getCompleteUpdatedDates(CompletableFuture<Map<String, LocalDateTime>> future) {
    postgresClient.select("SELECT * FROM " + TENANT_ID + "_mod_inventory_storage.instance", handler -> {
      Map<String, LocalDateTime> res = new HashMap<>();
      if (handler.succeeded()) {
        handler.result().iterator().forEachRemaining(row -> {
          var rowJson = row.toJson();
          if (rowJson.containsKey("complete_updated_date")) {
            var completeUpdatedDate = rowJson.getString("complete_updated_date");
            if (nonNull(completeUpdatedDate)) {
              res.put(rowJson.getString("id"),
                LocalDateTime.parse(completeUpdatedDate, DateTimeFormatter.ISO_ZONED_DATE_TIME));
            }
          }
        });
        future.complete(res);
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

  private JsonObject createItem(UUID journalMaterialTypeId, UUID holdingId) {
    return super.createItem(createItemRequest(journalMaterialTypeId, holdingId).create());
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
