package org.folio.rest.api;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.text.IsEmptyString.isEmptyString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.ObjectUtils;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.http.InterfaceUrls;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.JsonObject;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

/**
 * Test cases to verify effectiveLocationId property calculation that implemented
 * as two triggers for holdings_record and item tables (see itemEffectiveLocation.sql)
 */
@RunWith(JUnitParamsRunner.class)
public class ItemEffectiveLocationTest extends TestBaseWithInventoryUtil {
  private static UUID instanceId = UUID.randomUUID();
  private static Map<UUID, String> locationIdToNameMap = buildLocationIdToNameMap();

  // for @BeforeClass beforeAny() see TestBaseWithInventoryUtil

  @BeforeClass
  public static void createInstance() throws Exception {
    // Create once to be used by the many parameterized unit test in
    // canCalculateEffectiveLocationOnIHoldingUpdate(PermTemp, PermTemp, PermTemp)
    // canCalculateEffectiveLocationOnItemUpdate(PermTemp, PermTemp, PermTemp)
    instancesClient.create(instance(instanceId));
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

  @Test
  public void removesPropertyWhenEffectiveLocationIsNull() throws Exception {
    UUID holdingsRecordId = createHolding(instanceId, null, null);

    Item item = buildItem(holdingsRecordId, null, null);
    JsonObject createdItem = createItem(item).getJson();

    assertFalse(createdItem.containsKey("effectiveLocationId"));
  }

  @Test
  @Parameters(method = "parameters")
  public void canCalculateEffectiveLocationOnItemUpdate(
      PermTemp holdingLoc, PermTemp itemStartLoc, PermTemp itemEndLoc) throws Exception {

    UUID holdingsRecordId = createHolding(instanceId, holdingLoc.perm, holdingLoc.temp);

    Item item = buildItem(holdingsRecordId, itemStartLoc.perm, itemStartLoc.temp);
    UUID itemId = UUID.fromString(item.getId());
    IndividualResource itemResource = createItem(item);
    JsonObject item2 = itemResource.getJson();
    assertEffectiveLocation(item2, holdingLoc, itemStartLoc);

    setPermanentTemporaryLocation(item2, itemEndLoc);
    itemsClient.replace(itemId, item2);
    JsonObject item3 = itemsClient.getById(itemId).getJson();
    assertEffectiveLocation(item3, holdingLoc, itemEndLoc);
  }

  @Test
  @Parameters(method = "parameters")   // res-use swapping item and holding
  public void canCalculateEffectiveLocationOnIHoldingUpdate(
      PermTemp itemLoc, PermTemp holdingStartLoc, PermTemp holdingEndLoc) throws Exception {

    UUID holdingsRecordId = createHolding(instanceId, holdingStartLoc.perm, holdingStartLoc.temp);

    Item item = buildItem(holdingsRecordId, itemLoc.perm, itemLoc.temp);
    UUID itemId = UUID.fromString(item.getId());
    JsonObject item2 = createItem(item).getJson();
    assertEffectiveLocation(item2, holdingStartLoc, itemLoc);

    JsonObject holding2 = holdingsClient.getById(holdingsRecordId).getJson();
    setPermanentTemporaryLocation(holding2, holdingEndLoc);
    holdingsClient.replace(holdingsRecordId, holding2);
    JsonObject item3 = itemsClient.getById(itemId).getJson();
    assertEffectiveLocation(item3, holdingEndLoc, itemLoc);
  }

  @Test
  public void responseContainsAllRequiredHeaders() throws Exception {
    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId, annexLibraryLocationId);

    CompletableFuture<HttpClientResponse> createCompleted = new CompletableFuture<>();
    Item item = buildItem(holdingsRecordId, null, null);

    client
      .post(InterfaceUrls.itemsStorageUrl(""), item,
        StorageTestSuite.TENANT_ID, createCompleted::complete);

    HttpClientResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.statusCode(), is(201));
    assertThat(response.getHeader("location"), not(isEmptyString()));
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

  private Item getItem(String id) throws Exception {
    return itemsClient.getById(UUID.fromString(id)).getJson().mapTo(Item.class);
  }

  /** Store a permanent location UUID and a temporary location UUID. */
  private static class PermTemp {
    /** permanent location UUID */
    UUID perm;
    /** temporary location UUID */
    UUID temp;
    /**
     * @param perm permanent location UUID
     * @param temp temporary location UUID
     */
    PermTemp(UUID perm, UUID temp) {
      this.perm = perm;
      this.temp = temp;
    }
    /**
     * The first character of the perm UUID and of the temp UUID; use _ for null.
     * IDE's JUnit views show this String as the unit test parameter.
     */
    @Override
    public String toString() {
      return locationIdToNameMap.get(perm) + ", " + locationIdToNameMap.get(temp);
    }
  }

@SuppressWarnings("unused")  // is actually used by @Parameters(method = "parameters")
private List<PermTemp[]> parameters() {
    List<PermTemp[]> list = new ArrayList<>();
    PermTemp[] holdingLocationsList = {
      new PermTemp(null, null),
      new PermTemp(null, mainLibraryLocationId),
      new PermTemp(mainLibraryLocationId, null),
      new PermTemp(mainLibraryLocationId, annexLibraryLocationId),
    };
    PermTemp[] itemStartLocationsList = {
      new PermTemp(null, null),
      new PermTemp(null, onlineLocationId),
      new PermTemp(onlineLocationId, null),
      new PermTemp(onlineLocationId, secondFloorLocationId),
    };
    PermTemp[] itemEndLocationsList = {
      new PermTemp(null, null),
      new PermTemp(null, onlineLocationId),
      new PermTemp(onlineLocationId, null),
      new PermTemp(onlineLocationId, secondFloorLocationId),
      new PermTemp(null, thirdFloorLocationId),
      new PermTemp(thirdFloorLocationId, null),
      new PermTemp(thirdFloorLocationId, fourthFloorLocationId),
    };

    for (PermTemp holdingLocations : holdingLocationsList) {
      for (PermTemp itemStartLocations : itemStartLocationsList) {
        for (PermTemp itemEndLocations : itemEndLocationsList) {
          list.add(new PermTemp[]{holdingLocations, itemStartLocations, itemEndLocations});
        }
      }
    }
    return list;
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

  private void assertEffectiveLocation(
      JsonObject item, PermTemp holdingLocations, PermTemp itemLocations) {

    UUID expectedEffectiveLocation = ObjectUtils
        .firstNonNull(itemLocations.temp, itemLocations.perm, holdingLocations.temp, holdingLocations.perm);
    if (expectedEffectiveLocation == null) {
      // { "effectiveLocationId": null } is not allowed, the complete property must have been removed
      assertThat(item.containsKey(EFFECTIVE_LOCATION_ID_KEY), is(false));
      return;
    }
    assertThat(item.getString(EFFECTIVE_LOCATION_ID_KEY), is(expectedEffectiveLocation.toString()));
  }

  private static Map<UUID, String> buildLocationIdToNameMap() {
    HashMap<UUID, String> idToNameMap = new HashMap<>();

    idToNameMap.put(mainLibraryLocationId, MAIN_LIBRARY_LOCATION);
    idToNameMap.put(annexLibraryLocationId, ANNEX_LIBRARY_LOCATION);
    idToNameMap.put(onlineLocationId, ONLINE_LOCATION);
    idToNameMap.put(secondFloorLocationId, SECOND_FLOOR_LOCATION);
    idToNameMap.put(thirdFloorLocationId, THIRD_FLOOR_LOCATION);
    idToNameMap.put(fourthFloorLocationId, FOURTH_FLOOR_LOCATION);

    return idToNameMap;
  }
}
