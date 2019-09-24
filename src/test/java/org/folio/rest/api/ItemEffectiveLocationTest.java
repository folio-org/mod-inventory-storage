package org.folio.rest.api;

import static org.folio.rest.support.http.InterfaceUrls.holdingsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.instancesStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.itemsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.loanTypesStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.locCampusStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.locInstitutionStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.locLibraryStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.locationsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.materialTypesStorageUrl;
import static org.folio.util.StringUtil.urlEncode;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.text.IsEmptyString.isEmptyString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.Items;
import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.client.LoanTypesClient;
import org.folio.rest.support.client.MaterialTypesClient;
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
  private static String journalMaterialTypeID;
  private static String canCirculateLoanTypeID;
  private static UUID instanceId;
  private static UUID mainLibraryLocationId;
  private static UUID annexLibraryLocationId;
  private static UUID onlineLocationId;
  private static UUID secondFloorLocationId;
  private static UUID thirdFloorLocationId;
  private static UUID fourthFloorLocationId;
  private static UUID [] location;

  @BeforeClass
  public static void beforeAny() throws Exception {
    StorageTestSuite.deleteAll(itemsStorageUrl(""));
    StorageTestSuite.deleteAll(holdingsStorageUrl(""));
    StorageTestSuite.deleteAll(instancesStorageUrl(""));

    StorageTestSuite.deleteAll(materialTypesStorageUrl(""));
    StorageTestSuite.deleteAll(locationsStorageUrl(""));
    StorageTestSuite.deleteAll(locLibraryStorageUrl(""));
    StorageTestSuite.deleteAll(locCampusStorageUrl(""));
    StorageTestSuite.deleteAll(locInstitutionStorageUrl(""));
    StorageTestSuite.deleteAll(loanTypesStorageUrl(""));

    MaterialTypesClient materialTypesClient = new MaterialTypesClient(client, materialTypesStorageUrl(""));
    journalMaterialTypeID = materialTypesClient.create("journal");
    canCirculateLoanTypeID = new LoanTypesClient(client, loanTypesStorageUrl("")).create("Can Circulate");

    instanceId = instancesClient.create(instance(UUID.randomUUID())).getId();

    LocationsTest.createLocUnits(true);
    mainLibraryLocationId = LocationsTest.createLocation(null, "Main Library (Item)", "It/M");
    annexLibraryLocationId = LocationsTest.createLocation(null, "Annex Library (item)", "It/A");
    onlineLocationId = LocationsTest.createLocation(null, "Online (item)", "It/O");
    secondFloorLocationId = LocationsTest.createLocation(null, "Second Floor (item)", "It/SF");
    thirdFloorLocationId = LocationsTest.createLocation(null, "Third Floor (item)", "It/TF");
    fourthFloorLocationId = LocationsTest.createLocation(null, "Fourth Floor (item)", "It/FoF");
    location = new UUID [] {
        null,
        mainLibraryLocationId,
        secondFloorLocationId,
        thirdFloorLocationId,
        fourthFloorLocationId,
        annexLibraryLocationId,
        onlineLocationId
    };
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
  public void canCalculateEffectiveLocationOnHoldingRemoveTempLocation() throws Exception {
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

  private void setPermTempLocation(JsonObject json, int perm, int temp) {
    if (perm == 0) {
      json.remove("permanentLocationId");
    } else {
      json.put("permanentLocationId", location[perm].toString());
    }
    if (temp == 0) {
      json.remove("temporaryLocationId");
    } else {
      json.put("temporaryLocationId", location[temp].toString());
    }
  }

  private String effectiveLocation(int holdingPerm, int holdingTemp, int itemPerm, int itemTemp) {
    int loc = 0;
    if (itemTemp != 0) {
      loc = itemTemp;
    } else if (itemPerm != 0) {
      loc = itemPerm;
    } else if (holdingTemp != 0) {
      loc = holdingTemp;
    } else {
      loc = holdingPerm;
    }
    UUID uuid = location[loc];
    return uuid == null ? null : uuid.toString();
  }

  private void assertEffectiveLocation(JsonObject item, int holdingPerm, int holdingTemp, int itemPerm, int itemTemp) throws Exception {
    String effectiveLocation = effectiveLocation(holdingPerm, holdingTemp, itemPerm, itemTemp);
    if (effectiveLocation == null) {
      // { "effectiveLocationId": null } is not allowed, the property must have been removed
      assertThat(item.containsKey("effectiveLocationId"), is(false));
      return;
    }
    assertThat(item.getString("effectiveLocationId"), is(effectiveLocation));
  }

  @SuppressWarnings("unused")  // is actually used by @Parameters(method = "parameters")
  private Object parameters() throws Exception {
    List<Integer []> list = new ArrayList<>();
    int holdingPerm = 0;
    int holdingTemp = 0;
    int itemPerm = 0;
    int itemTemp = 0;
    int newItemPerm = 0;
    int newItemTemp = 0;
    for (int i=0; i<=3; i++) {
      switch (i) {
      case 0: holdingPerm = 0; holdingTemp = 0; break;
      case 1: holdingPerm = 0; holdingTemp = 1; break;
      case 2: holdingPerm = 1; holdingTemp = 0; break;
      case 3: holdingPerm = 1; holdingTemp = 2; break;
      }
      for (int j=0; j<=3; j++) {
        switch (j) {
        case 0: itemPerm = 0; itemTemp = 0; break;
        case 1: itemPerm = 0; itemTemp = 3; break;
        case 2: itemPerm = 3; itemTemp = 0; break;
        case 3: itemPerm = 3; itemTemp = 4; break;
        }
        for (int k=0; k<=6; k++) {
          switch (k) {
          case 0:  newItemPerm = 0; newItemTemp = 0; break;
          case 1:  newItemPerm = 0; newItemTemp = 3; break;
          case 2:  newItemPerm = 3; newItemTemp = 0; break;
          case 3:  newItemPerm = 3; newItemTemp = 4; break;
          case 4:  newItemPerm = 0; newItemTemp = 5; break;
          case 5:  newItemPerm = 5; newItemTemp = 0; break;
          case 6:  newItemPerm = 5; newItemTemp = 6; break;
          }
          list.add(new Integer [] { holdingPerm, holdingTemp, itemPerm, itemTemp, newItemPerm, newItemTemp });
        }
      }
    }
    return list;
  }

  @Test
  @Parameters(method = "parameters")
  public void canCalculateEffecticeLocationOnItemUpdate(
      int holdingPerm, int holdingTemp, int itemPerm, int itemTemp, int newItemPerm, int newItemTemp) throws Exception {

    UUID holdingsRecordId = createHolding(instanceId, location[holdingPerm], location[holdingTemp]);

    Item item = buildItem(holdingsRecordId, location[itemPerm], location[itemTemp]);
    UUID itemId = UUID.fromString(item.getId());
    IndividualResource itemResource = createItem(item);
    JsonObject item2 = itemResource.getJson();
    assertEffectiveLocation(item2, holdingPerm, holdingTemp, itemPerm, itemTemp);

    setPermTempLocation(item2, newItemPerm, newItemTemp);
    itemsClient.replace(itemId, item2);
    JsonObject item3 = itemsClient.getById(itemId).getJson();
    assertEffectiveLocation(item3, holdingPerm, holdingTemp, newItemPerm, newItemTemp);
  }

  @Test
  @Parameters(method = "parameters")   // res-use swapping item and holding
  public void canCalculateEffecticeLocationOnHoldingsUpdate(
      int itemPerm, int itemTemp, int holdingPerm, int holdingTemp, int newHoldingPerm, int newHoldingTemp) throws Exception {

    UUID holdingsRecordId = createHolding(instanceId, location[holdingPerm], location[holdingTemp]);

    Item item = buildItem(holdingsRecordId, location[itemPerm], location[itemTemp]);
    UUID itemId = UUID.fromString(item.getId());
    JsonObject item2 = createItem(item).getJson();
    assertEffectiveLocation(item2, holdingPerm, holdingTemp, itemPerm, itemTemp);

    JsonObject holding2 = holdingsClient.getById(holdingsRecordId).getJson();
    setPermTempLocation(holding2, newHoldingPerm, newHoldingTemp);
    holdingsClient.replace(holdingsRecordId, holding2);
    JsonObject item3 = itemsClient.getById(itemId).getJson();
    assertEffectiveLocation(item3, newHoldingPerm, newHoldingTemp, itemPerm, itemTemp);
  }

  @Test
  public void canSearchItemByEffectiveLocation() throws Exception {
    StorageTestSuite.deleteAll(itemsStorageUrl(""));
    StorageTestSuite.deleteAll(holdingsStorageUrl(""));

    UUID holdingsWithPermLocation = createInstanceAndHolding(mainLibraryLocationId);
    UUID holdingsWithTempLocation = createInstanceAndHolding(mainLibraryLocationId, annexLibraryLocationId);

    Item itemWithHoldingPermLocation = buildItem(holdingsWithPermLocation, null, null);
    Item itemWithHoldingTempLocation = buildItem(holdingsWithTempLocation, null, null);
    Item itemWithTempLocation = buildItem(holdingsWithPermLocation, onlineLocationId, null);
    Item itemWithPermLocation = buildItem(holdingsWithTempLocation, null, secondFloorLocationId);
    Item itemWithAllLocation = buildItem(holdingsWithTempLocation, secondFloorLocationId, onlineLocationId);

    Item[] itemsToCreate = {itemWithHoldingPermLocation, itemWithHoldingTempLocation,
      itemWithTempLocation, itemWithPermLocation, itemWithAllLocation};

    for (Item item : itemsToCreate) {
      IndividualResource createdItem = createItem(item);
      assertTrue(createdItem.getJson().containsKey("effectiveLocationId"));
    }

    Items mainLibraryItems = findItems("effectiveLocationId=" + mainLibraryLocationId);
    Items annexLibraryItems = findItems("effectiveLocationId=" + annexLibraryLocationId);
    Items onlineLibraryItems = findItems("effectiveLocationId=" + onlineLocationId);
    Items secondFloorLibraryItems = findItems("effectiveLocationId=" + secondFloorLocationId);

    assertEquals(1, mainLibraryItems.getTotalRecords().intValue());
    assertThat(mainLibraryItems.getItems().get(0).getId(), is(itemWithHoldingPermLocation.getId()));

    assertEquals(1, annexLibraryItems.getTotalRecords().intValue());
    assertThat(annexLibraryItems.getItems().get(0).getId(), is(itemWithHoldingTempLocation.getId()));

    assertEquals(2, onlineLibraryItems.getTotalRecords().intValue());

    assertThat(onlineLibraryItems.getItems()
        .stream()
        .map(Item::getId)
        .collect(Collectors.toList()),
      hasItems(itemWithTempLocation.getId(), itemWithAllLocation.getId()));

    assertEquals(1, secondFloorLibraryItems.getTotalRecords().intValue());
    assertThat(secondFloorLibraryItems.getItems().get(0).getId(), is(itemWithPermLocation.getId()));
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

  private Item buildItem(UUID holdingsRecordId,
                         UUID permLocation,
                         UUID tempLocation) {
    JsonObject itemToCreate = new JsonObject();

    itemToCreate.put("id", UUID.randomUUID().toString());
    itemToCreate.put("holdingsRecordId", holdingsRecordId.toString());
    itemToCreate.put("barcode", Long.toString(new Random().nextLong()));
    itemToCreate.put("status", new JsonObject().put("name", "Available"));
    itemToCreate.put("materialTypeId", journalMaterialTypeID);
    itemToCreate.put("permanentLoanTypeId", canCirculateLoanTypeID);
    if (tempLocation != null) {
      itemToCreate.put("temporaryLocationId", tempLocation.toString());
    }
    if (permLocation != null) {
      itemToCreate.put("permanentLocationId", permLocation.toString());
    }

    return itemToCreate.mapTo(Item.class);
  }

  private Item getItem(String id) throws Exception {
    return itemsClient.getById(UUID.fromString(id)).getJson().mapTo(Item.class);
  }

  private Items findItems(String searchQuery) throws Exception {
    CompletableFuture<Response> searchCompleted = new CompletableFuture<>();

    client.get(itemsStorageUrl("?query=") + urlEncode(searchQuery),
      StorageTestSuite.TENANT_ID, ResponseHandler.json(searchCompleted));

    return searchCompleted.get(5, TimeUnit.SECONDS).getJson()
      .mapTo(Items.class);
  }

  private IndividualResource createItem(Item item) throws Exception {
    return itemsClient.create(JsonObject.mapFrom(item));
  }
}
