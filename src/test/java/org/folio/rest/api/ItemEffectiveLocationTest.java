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

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.sqlclient.Row;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import lombok.SneakyThrows;
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

/**
 * Test cases to verify effectiveLocationId property calculation that implemented
 * as two triggers for holdings_record and item tables (see itemEffectiveLocation.sql)
 */
@RunWith(JUnitParamsRunner.class)
public class ItemEffectiveLocationTest extends TestBaseWithInventoryUtil {
  private static final UUID INSTANCE_ID = UUID.randomUUID();

  private final HoldingsEventMessageChecks holdingsMessageChecks
    = new HoldingsEventMessageChecks(KAFKA_CONSUMER);

  private final ItemEventMessageChecks itemMessageChecks
    = new ItemEventMessageChecks(KAFKA_CONSUMER);

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
    instancesClient.create(instance(INSTANCE_ID));

    removeAllEvents();
  }

  @After
  public void checkIdsAfterEach() {
    StorageTestSuite.checkForMismatchedIds("item");
    StorageTestSuite.checkForMismatchedIds("holdings_record");
  }

  public void canCalculateEffectiveLocationOnHoldingUpdate() throws Exception {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

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
    holding.put("temporaryLocationId", SECOND_FLOOR_LOCATION_ID.toString());
    holdingsClient.replace(holdingsRecordId, holding);

    for (Item item : itemsToCreate) {
      Item fetchedItem = getItem(item.getId());
      assertEquals(fetchedItem.getEffectiveLocationId(), SECOND_FLOOR_LOCATION_ID.toString());
    }
  }

  @Test
  public void canCalculateEffectiveLocationOnHoldingRemoveTempLocationShouldBeHoldingPermLocation() throws Exception {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID, ANNEX_LIBRARY_LOCATION_ID);

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
      assertEquals(fetchedItem.toString(), fetchedItem.getEffectiveLocationId(), MAIN_LIBRARY_LOCATION_ID.toString());
    }
  }

  @Test
  public void canCalculateEffectiveLocationOnHoldingUpdateWhenSomeItemsHasLocation() throws Exception {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

    Item itemWithPermLocation = buildItem(holdingsRecordId, ONLINE_LOCATION_ID, null);
    Item itemNoLocation = buildItem(holdingsRecordId, null, null);
    Item itemWithTempLocation = buildItem(holdingsRecordId, null, ANNEX_LIBRARY_LOCATION_ID);

    createItem(itemWithPermLocation);
    createItem(itemNoLocation);
    createItem(itemWithTempLocation);

    JsonObject holding = holdingsClient.getById(holdingsRecordId).getJson();
    holding.put("temporaryLocationId", SECOND_FLOOR_LOCATION_ID.toString());
    holdingsClient.replace(holdingsRecordId, holding);

    // fetch items
    Item itemWithPermLocationFetched = getItem(itemWithPermLocation.getId());
    Item itemNoLocationFetched = getItem(itemNoLocation.getId());
    Item itemWithTempLocationFetched = getItem(itemWithTempLocation.getId());

    // Assert that itemWithPermLocationFetched was not updated
    assertEquals(itemWithPermLocationFetched.getEffectiveLocationId(), ONLINE_LOCATION_ID.toString());

    // Assert that itemNoLocationFetched was updated
    assertEquals(itemNoLocationFetched.getEffectiveLocationId(), SECOND_FLOOR_LOCATION_ID.toString());

    // Assert that itemWithPermLocationFetched was not updated
    assertEquals(itemWithTempLocationFetched.getEffectiveLocationId(), ANNEX_LIBRARY_LOCATION_ID.toString());
  }

  /**
   * Test that creating an item and updating an item correctly sets the effectiveLocationId.
   * This only succeeds if the two triggers update_effective_location and
   * update_item_references (assigning item.effectiveLocationId = item.jsonb->>'effectiveLocationId')
   * run in correct order.
   *
   * @param holdingLoc   permanent and temporary location of the holding
   * @param itemStartLoc permanent and temporary location of the item before the update
   * @param itemEndLoc   permanent and temporary location of the item after the update
   */
  @Test
  @Parameters(source = ItemEffectiveLocationTestDataProvider.class,
              method = "canCalculateEffectiveLocationOnItemUpdateParams")
  public void canCalculateEffectiveLocationOnItemUpdate(
    PermTemp holdingLoc, PermTemp itemStartLoc, PermTemp itemEndLoc) throws Exception {

    UUID holdingsRecordId = createHolding(INSTANCE_ID, holdingLoc.perm, holdingLoc.temp);

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
  public void canCalculateEffectiveLocationHoldingUpdate(
    PermTemp itemLoc, PermTemp holdingStartLoc, PermTemp holdingEndLoc) {

    UUID holdingsRecordId = createHolding(INSTANCE_ID, holdingStartLoc.perm, holdingStartLoc.temp);
    JsonObject createdHolding = holdingsClient.getById(holdingsRecordId).getJson();

    Item item = buildItem(holdingsRecordId, itemLoc.perm, itemLoc.temp);
    final UUID itemId = UUID.fromString(item.getId());

    JsonObject createdItem = createItem(item).getJson();
    assertThat(createdItem.getString(EFFECTIVE_LOCATION_ID_KEY),
      is(effectiveLocation(holdingStartLoc, itemLoc)));

    JsonObject holdingToUpdate = createdHolding.copy();
    holdingToUpdate.remove("holdingsItems");
    holdingToUpdate.remove("bareHoldingsItems");
    setPermanentTemporaryLocation(holdingToUpdate, holdingEndLoc);
    holdingsClient.replace(holdingsRecordId, holdingToUpdate);

    JsonObject associatedItem = itemsClient.getById(itemId).getJson();
    assertThat(associatedItem.getString(EFFECTIVE_LOCATION_ID_KEY),
      is(effectiveLocation(holdingEndLoc, itemLoc)));

    itemMessageChecks.updatedMessagePublished(createdItem, associatedItem);

    JsonObject holdings = holdingsClient.getById(holdingsRecordId).getJson();
    holdingsMessageChecks.updatedMessagePublished(createdHolding, holdings);
  }

  @Test
  public void responseContainsAllRequiredHeaders() throws Exception {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID, ANNEX_LIBRARY_LOCATION_ID);

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
    UUID initialHoldingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID, ANNEX_LIBRARY_LOCATION_ID);
    UUID updatedHoldingRecordId = createInstanceAndHolding(ONLINE_LOCATION_ID, SECOND_FLOOR_LOCATION_ID);

    Item item = buildItem(initialHoldingsRecordId, null, null);
    createItem(item);

    Item itemFetched = getItem(item.getId());
    assertEquals(itemFetched.getEffectiveLocationId(), ANNEX_LIBRARY_LOCATION_ID.toString());

    itemsClient.replace(UUID.fromString(itemFetched.getId()),
      JsonObject.mapFrom(itemFetched).copy()
        .put("holdingsRecordId", updatedHoldingRecordId.toString())
    );

    assertEquals(getItem(item.getId()).getEffectiveLocationId(), SECOND_FLOOR_LOCATION_ID.toString());
  }

  @Test
  public void canCalculateEffectiveLocationWhenItemHasPermLocationAndAssociatedToAnotherHolding() throws Exception {
    UUID initialHoldingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID, ANNEX_LIBRARY_LOCATION_ID);
    UUID updatedHoldingRecordId = createInstanceAndHolding(SECOND_FLOOR_LOCATION_ID);

    Item item = buildItem(initialHoldingsRecordId, ONLINE_LOCATION_ID, null);
    createItem(item);

    Item itemFetched = getItem(item.getId());
    assertEquals(itemFetched.getEffectiveLocationId(), ONLINE_LOCATION_ID.toString());

    itemsClient.replace(UUID.fromString(itemFetched.getId()),
      JsonObject.mapFrom(itemFetched).copy()
        .put("holdingsRecordId", updatedHoldingRecordId.toString())
    );

    assertEquals(getItem(item.getId()).getEffectiveLocationId(), ONLINE_LOCATION_ID.toString());
  }

  /**
   * Does "UPDATE item" correctly set both item.jsonb->>'effectiveLocationId' and item.effectiveLocationId?
   */
  @Test
  public void canSetTableFieldOnItemUpdate() throws Exception {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID, ANNEX_LIBRARY_LOCATION_ID);
    Item item = buildItem(holdingsRecordId, ONLINE_LOCATION_ID, null);
    createItem(item);
    item = getItem(item.getId());

    item.setTemporaryLocationId(SECOND_FLOOR_LOCATION_ID.toString());
    itemsClient.replace(UUID.fromString(item.getId()), JsonObject.mapFrom(item));

    Row result = runSql(
      "SELECT jsonb, effectiveLocationId "
        + "FROM test_tenant_mod_inventory_storage.item "
        + "WHERE id='" + item.getId() + "'");
    JsonObject jsonb = (JsonObject) result.getValue(0);
    String effectiveLocationId = result.getUUID(1).toString();
    assertThat(jsonb.getString("effectiveLocationId"), is(SECOND_FLOOR_LOCATION_ID.toString()));
    assertThat(effectiveLocationId, is(SECOND_FLOOR_LOCATION_ID.toString()));
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
      return future.get(TIMEOUT, TimeUnit.SECONDS);
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
