package org.folio.rest.api;

import static org.folio.utility.ModuleUtility.getClient;
import static org.folio.utility.ModuleUtility.getVertx;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang3.ObjectUtils;
import org.folio.rest.api.testdata.ItemEffectiveLocationTestDataProvider;
import org.folio.rest.api.testdata.ItemEffectiveLocationTestDataProvider.PermTemp;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.http.InterfaceUrls;
import org.folio.rest.support.messages.HoldingsEventMessageChecks;
import org.folio.rest.support.messages.ItemEventMessageChecks;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.sqlclient.Row;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import lombok.SneakyThrows;

/**
 * Test cases to verify effectiveLocationId property calculation that implemented
 * as two triggers for holdings_record and item tables (see itemEffectiveLocation.sql)
 */
@RunWith(JUnitParamsRunner.class)
public class ItemEffectiveLocationTest extends TestBaseWithInventoryUtil {
  private static final UUID instanceId = UUID.randomUUID();

  private final HoldingsEventMessageChecks holdingsMessageChecks
    = new HoldingsEventMessageChecks(kafkaConsumer);

  private final ItemEventMessageChecks itemMessageChecks
    = new ItemEventMessageChecks(kafkaConsumer);

  @SneakyThrows
  @Before
  public void beforeEach() {
    clearData();
    setupMaterialTypes();
    setupLoanTypes();
    setupLocations();

    // Create once to be used by the many parameterized unit test in
    // canCalculateEffectiveLocationOnIHoldingUpdate(PermTemp, PermTemp, PermTemp)
    // canCalculateEffectiveLocationOnItemUpdate(PermTemp, PermTemp, PermTemp)
    instancesClient.create(instance(instanceId));

    removeAllEvents();
  }

  @After
  public void checkIdsAfterEach() {
    StorageTestSuite.checkForMismatchedIDs("item");
    StorageTestSuite.checkForMismatchedIDs("holdings_record");
  }

  public void canCalculateEffectiveLocationOnHoldingUpdate() throws Exception {
    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    final Item[] itemsToCreate = {
      buildItem(holdingsRecordId, null, null),
      buildItem(holdingsRecordId, null, null),
      buildItem(holdingsRecordId, null, null)
    };

    for (Item item : itemsToCreate) {
      IndividualResource createdItem = createItem(item);
      assertTrue(createdItem.getJson().containsKey("effectiveLocationId"));
    }

    JsonObject holding = holdingsClient.getById(holdingsRecordId).getJson();
    holding.put("temporaryLocationId", secondFloorLocationId.toString());
    holdingsClient.replace(holdingsRecordId, holding);

    for (Item item : itemsToCreate) {
      Item fetchedItem = getItem(item.getId());
      assertEquals(fetchedItem.getEffectiveLocationId(), secondFloorLocationId.toString());
    }
  }

  @Test
  public void canCalculateEffectiveLocationOnHoldingRemoveTempLocationShouldBeHoldingPermLocation() throws Exception {
    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId, annexLibraryLocationId);

    final Item[] itemsToCreate = {
      buildItem(holdingsRecordId, null, null),
      buildItem(holdingsRecordId, null, null),
      buildItem(holdingsRecordId, null, null)
    };

    for (Item item : itemsToCreate) {
      IndividualResource createdItem = createItem(item);
      assertTrue(createdItem.getJson().containsKey("effectiveLocationId"));
    }

    JsonObject holding = holdingsClient.getById(holdingsRecordId).getJson();
    holding.remove("temporaryLocationId");
    holdingsClient.replace(holdingsRecordId, holding);

    for (Item item : itemsToCreate) {
      Item fetchedItem = getItem(item.getId());
      assertEquals(fetchedItem.toString(), fetchedItem.getEffectiveLocationId(), mainLibraryLocationId.toString());
    }
  }

  @Test
  public void canCalculateEffectiveLocationOnHoldingUpdateWhenSomeItemsHasLocation() throws Exception {
    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    Item itemWithPermLocation = buildItem(holdingsRecordId, onlineLocationId, null);
    Item itemNoLocation = buildItem(holdingsRecordId, null, null);
    Item itemWithTempLocation = buildItem(holdingsRecordId, null, annexLibraryLocationId);

    createItem(itemWithPermLocation);
    createItem(itemNoLocation);
    createItem(itemWithTempLocation);

    JsonObject holding = holdingsClient.getById(holdingsRecordId).getJson();
    holding.put("temporaryLocationId", secondFloorLocationId.toString());
    holdingsClient.replace(holdingsRecordId, holding);

    // fetch items
    Item itemWithPermLocationFetched = getItem(itemWithPermLocation.getId());
    Item itemNoLocationFetched = getItem(itemNoLocation.getId());
    Item itemWithTempLocationFetched = getItem(itemWithTempLocation.getId());

    // Assert that itemWithPermLocationFetched was not updated
    assertEquals(itemWithPermLocationFetched.getEffectiveLocationId(), onlineLocationId.toString());

    // Assert that itemNoLocationFetched was updated
    assertEquals(itemNoLocationFetched.getEffectiveLocationId(), secondFloorLocationId.toString());

    // Assert that itemWithPermLocationFetched was not updated
    assertEquals(itemWithTempLocationFetched.getEffectiveLocationId(), annexLibraryLocationId.toString());
  }

  /**
   * Test that creating an item and updating an item correctly sets the effectiveLocationId.
   *
   * This only succeeds if the two triggers update_effective_location and
   * update_item_references (assigning item.effectiveLocationId = item.jsonb->>'effectiveLocationId')
   * run in correct order.
   *
   * @param holdingLoc permanent and temporary location of the holding
   * @param itemStartLoc permanent and temporary location of the item before the update
   * @param itemEndLoc permanent and temporary location of the item after the update
   */
  @Test
  @Parameters(source = ItemEffectiveLocationTestDataProvider.class,
  method = "canCalculateEffectiveLocationOnItemUpdateParams")
  public void canCalculateEffectiveLocationOnItemUpdate(
      PermTemp holdingLoc, PermTemp itemStartLoc, PermTemp itemEndLoc) throws Exception {

    UUID holdingsRecordId = createHolding(instanceId, holdingLoc.perm, holdingLoc.temp);

    Item item = buildItem(holdingsRecordId, itemStartLoc.perm, itemStartLoc.temp);
    UUID itemId = UUID.fromString(item.getId());

    JsonObject createdItem = createItem(item).getJson();
    assertThat(createdItem.getString(EFFECTIVE_LOCATION_ID_KEY),
      is(effectiveLocation(holdingLoc, itemStartLoc)));

    setPermanentTemporaryLocation(createdItem, itemEndLoc);
    itemsClient.replace(itemId, createdItem);

    JsonObject updatedItem = itemsClient.getById(itemId).getJson();
    assertThat(updatedItem.getString(EFFECTIVE_LOCATION_ID_KEY),
      is(effectiveLocation(holdingLoc, itemEndLoc)));
  }

  @Test
  @Parameters(source = ItemEffectiveLocationTestDataProvider.class,
    method = "canCalculateEffectiveLocationOnHoldingUpdateParams")
  public void canCalculateEffectiveLocationOnHoldingUpdate(
      PermTemp itemLoc, PermTemp holdingStartLoc, PermTemp holdingEndLoc) {

    UUID holdingsRecordId = createHolding(instanceId, holdingStartLoc.perm, holdingStartLoc.temp);
    JsonObject createdHolding = holdingsClient.getById(holdingsRecordId).getJson();

    Item item = buildItem(holdingsRecordId, itemLoc.perm, itemLoc.temp);
    UUID itemId = UUID.fromString(item.getId());

    JsonObject createdItem = createItem(item).getJson();
    assertThat(createdItem.getString(EFFECTIVE_LOCATION_ID_KEY),
      is(effectiveLocation(holdingStartLoc, itemLoc)));

    JsonObject holdingToUpdate = createdHolding.copy();
    setPermanentTemporaryLocation(holdingToUpdate, holdingEndLoc);
    holdingsClient.replace(holdingsRecordId, holdingToUpdate);

    JsonObject associatedItem = itemsClient.getById(itemId).getJson();
    assertThat(associatedItem.getString(EFFECTIVE_LOCATION_ID_KEY),
      is(effectiveLocation(holdingEndLoc, itemLoc)));

    itemMessageChecks.updatedMessagePublished(createdItem, associatedItem);

    holdingsMessageChecks.updatedMessagePublished(createdHolding,
      holdingsClient.getById(holdingsRecordId).getJson());
  }

  @Test
  public void responseContainsAllRequiredHeaders() throws Exception {
    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId, annexLibraryLocationId);

    CompletableFuture<HttpResponse<Buffer>> createCompleted = new CompletableFuture<>();
    Item item = buildItem(holdingsRecordId, null, null);

    getClient()
      .post(InterfaceUrls.itemsStorageUrl(""), item, TENANT_ID,
          createCompleted::complete);

    HttpResponse<Buffer> response = createCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(response.statusCode(), is(201));
    assertThat(response.getHeader("location"), not(is(emptyString())));
  }

  @Test
  public void canCalculateEffectiveLocationWhenItemAssociatedToAnotherHolding() throws Exception {
    UUID initialHoldingsRecordId = createInstanceAndHolding(mainLibraryLocationId, annexLibraryLocationId);
    UUID updatedHoldingRecordId = createInstanceAndHolding(onlineLocationId, secondFloorLocationId);

    Item item = buildItem(initialHoldingsRecordId, null, null);
    createItem(item);

    Item itemFetched = getItem(item.getId());
    assertEquals(itemFetched.getEffectiveLocationId(), annexLibraryLocationId.toString());

    itemsClient.replace(UUID.fromString(itemFetched.getId()),
      JsonObject.mapFrom(itemFetched).copy()
        .put("holdingsRecordId", updatedHoldingRecordId.toString())
    );

    assertEquals(getItem(item.getId()).getEffectiveLocationId(), secondFloorLocationId.toString());
  }

  @Test
  public void canCalculateEffectiveLocationWhenItemHasPermLocationAndAssociatedToAnotherHolding() throws Exception {
    UUID initialHoldingsRecordId = createInstanceAndHolding(mainLibraryLocationId, annexLibraryLocationId);
    UUID updatedHoldingRecordId = createInstanceAndHolding(secondFloorLocationId);

    Item item = buildItem(initialHoldingsRecordId, onlineLocationId, null);
    createItem(item);

    Item itemFetched = getItem(item.getId());
    assertEquals(itemFetched.getEffectiveLocationId(), onlineLocationId.toString());

    itemsClient.replace(UUID.fromString(itemFetched.getId()),
      JsonObject.mapFrom(itemFetched).copy()
        .put("holdingsRecordId", updatedHoldingRecordId.toString())
    );

    assertEquals(getItem(item.getId()).getEffectiveLocationId(), onlineLocationId.toString());
  }

  /**
   * Does "UPDATE item" correctly set both item.jsonb->>'effectiveLocationId' and item.effectiveLocationId?
   */
  @Test
  public void canSetTableFieldOnItemUpdate() throws Exception {
    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId, annexLibraryLocationId);
    Item item = buildItem(holdingsRecordId, onlineLocationId, null);
    createItem(item);
    item = getItem(item.getId());

    item.setTemporaryLocationId(secondFloorLocationId.toString());
    itemsClient.replace(UUID.fromString(item.getId()), JsonObject.mapFrom(item));

    Row result = runSql(
          "SELECT jsonb, effectiveLocationId "
        + "FROM test_tenant_mod_inventory_storage.item "
        + "WHERE id='" + item.getId() + "'");
    JsonObject jsonb = (JsonObject) result.getValue(0);
    String effectiveLocationId = result.getUUID(1).toString();
    assertThat(jsonb.getString("effectiveLocationId"), is(secondFloorLocationId.toString()));
    assertThat(effectiveLocationId, is(secondFloorLocationId.toString()));
  }

  private Row runSql(String sql) {
    CompletableFuture<Row> future = new CompletableFuture<>();

    PostgresClient.getInstance(getVertx()).selectSingle(sql, handler -> {
      if (handler.failed()) {
        future.completeExceptionally(handler.cause());
        return;
      }
      future.complete(handler.result());
    });

    try {
      return future.get(1, TimeUnit.SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  private Item getItem(String id) throws Exception {
    return itemsClient.getById(UUID.fromString(id)).getJson().mapTo(Item.class);
  }

  private void setPermanentTemporaryLocation(JsonObject json, PermTemp locations) {
    if (locations.perm == null) {
      json.remove(PERMANENT_LOCATION_ID_KEY);
    } else {
      json.put(PERMANENT_LOCATION_ID_KEY, locations.perm.toString());
    }
    if (locations.temp == null) {
      json.remove(TEMPORARY_LOCATION_ID_KEY);
    } else {
      json.put(TEMPORARY_LOCATION_ID_KEY, locations.temp.toString());
    }
  }

  private String effectiveLocation(PermTemp holdingLocations, PermTemp itemLocations) {
    return ObjectUtils
      .firstNonNull(itemLocations.temp, itemLocations.perm, holdingLocations.temp, holdingLocations.perm)
      // No NPE, as holdings.permanentLocation is required
      .toString();
  }
}
