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
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.JsonObject;

/**
 * Test cases to verify effectiveLocationId property calculation that implemented
 * as two triggers for holdings_record and item tables (see itemEffectiveLocation.sql)
 */
public class ItemEffectiveLocationTest extends TestBaseWithInventoryUtil {
  private static String journalMaterialTypeID;
  private static String canCirculateLoanTypeID;
  private static UUID mainLibraryLocationId;
  private static UUID annexLibraryLocationId;
  private static UUID onlineLocationId;
  private static UUID secondFloorLocationId;

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

    LocationsTest.createLocUnits(true);
    mainLibraryLocationId = LocationsTest.createLocation(null, "Main Library (Item)", "It/M");
    annexLibraryLocationId = LocationsTest.createLocation(null, "Annex Library (item)", "It/A");
    onlineLocationId = LocationsTest.createLocation(null, "Online (item)", "It/O");
    secondFloorLocationId = LocationsTest.createLocation(null, "Second Floor (item)", "It/SF");
  }

  @Before
  public void beforeEach() {
    StorageTestSuite.deleteAll(itemsStorageUrl(""));
    StorageTestSuite.deleteAll(holdingsStorageUrl(""));
    StorageTestSuite.deleteAll(instancesStorageUrl(""));
  }

  @After
  public void checkIdsAfterEach() {
    StorageTestSuite.checkForMismatchedIDs("item");
    StorageTestSuite.checkForMismatchedIDs("holdings_record");
  }

  @Test
  public void canCalculateEffectiveLocationOnItemInsertWithPermLocationOnHolding() throws Exception {
    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);
    Item itemToCreate = buildItem(holdingsRecordId, null, null);

    IndividualResource createdItem = createItem(itemToCreate);
    assertTrue(createdItem.getJson().containsKey("effectiveLocationId"));

    Item fetchedItem = getItem(itemToCreate.getId());
    assertEquals(fetchedItem.getEffectiveLocationId(), mainLibraryLocationId.toString());
  }

  @Test
  public void canCalculateEffectiveLocationOnItemInsertWithTempLocationOnHolding() throws Exception {
    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId,
      annexLibraryLocationId);
    Item itemToCreate = buildItem(holdingsRecordId, null, null);

    IndividualResource createdItem = createItem(itemToCreate);
    assertTrue(createdItem.getJson().containsKey("effectiveLocationId"));

    Item fetchedItem = getItem(itemToCreate.getId());
    assertEquals(fetchedItem.getEffectiveLocationId(), annexLibraryLocationId.toString());
  }

  @Test
  public void canCalculateEffectiveLocationOnItemInsertWithPermLocationOnItem() throws Exception {
    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId,
      annexLibraryLocationId);
    Item itemToCreate = buildItem(holdingsRecordId, null, onlineLocationId);

    IndividualResource createdItem = createItem(itemToCreate);
    assertTrue(createdItem.getJson().containsKey("effectiveLocationId"));

    Item fetchedItem = getItem(itemToCreate.getId());
    assertEquals(fetchedItem.getEffectiveLocationId(), onlineLocationId.toString());
  }

  @Test
  public void canCalculateEffectiveLocationOnItemInsertWithTempLocationOnItem() throws Exception {
    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId,
      annexLibraryLocationId);
    Item itemToCreate = buildItem(holdingsRecordId, onlineLocationId,
      secondFloorLocationId);

    IndividualResource createdItem = createItem(itemToCreate);
    assertTrue(createdItem.getJson().containsKey("effectiveLocationId"));

    Item fetchedItem = getItem(itemToCreate.getId());
    assertEquals(fetchedItem.getEffectiveLocationId(), secondFloorLocationId.toString());
  }

  @Test
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
      assertEquals(fetchedItem.getEffectiveLocationId(), mainLibraryLocationId.toString());
    }
  }

  @Test
  public void canCalculateEffectiveLocationOnHoldingUpdateWhenSomeItemsHasLocation() throws Exception {
    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    Item itemWithPermLocation = buildItem(holdingsRecordId, null, onlineLocationId);
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
  public void canCalculateEffectiveLocationOnItemPermLocationUpdate() throws Exception {
    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId, annexLibraryLocationId);

    Item item = buildItem(holdingsRecordId, null, null);
    createItem(item);

    Item itemFetched = getItem(item.getId());
    assertEquals(itemFetched.getEffectiveLocationId(), annexLibraryLocationId.toString());

    itemsClient.replace(UUID.fromString(itemFetched.getId()),
      JsonObject.mapFrom(itemFetched).copy()
        .put("permanentLocationId", onlineLocationId.toString())
    );

    assertEquals(getItem(item.getId()).getEffectiveLocationId(), onlineLocationId.toString());
  }

  @Test
  public void canCalculateEffectiveLocationOnItemTempLocationUpdate() throws Exception {
    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId, annexLibraryLocationId);

    Item item = buildItem(holdingsRecordId, null, null);
    createItem(item);

    Item itemFetched = getItem(item.getId());
    assertEquals(itemFetched.getEffectiveLocationId(), annexLibraryLocationId.toString());

    itemsClient.replace(UUID.fromString(itemFetched.getId()),
      JsonObject.mapFrom(itemFetched).copy()
        .put("temporaryLocationId", onlineLocationId.toString())
    );

    assertEquals(getItem(item.getId()).getEffectiveLocationId(), onlineLocationId.toString());
  }

  @Test
  public void canCalculateEffectiveLocationOnItemAllLocationUpdate() throws Exception {
    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId, annexLibraryLocationId);

    Item item = buildItem(holdingsRecordId, null, null);
    createItem(item);

    Item itemFetched = getItem(item.getId());
    assertEquals(itemFetched.getEffectiveLocationId(), annexLibraryLocationId.toString());

    itemsClient.replace(UUID.fromString(itemFetched.getId()),
      JsonObject.mapFrom(itemFetched).copy()
        .put("temporaryLocationId", onlineLocationId.toString())
        .put("permanentLocationId", secondFloorLocationId.toString())
    );

    assertEquals(getItem(item.getId()).getEffectiveLocationId(), onlineLocationId.toString());
  }

  @Test
  public void canSearchItemByEffectiveLocation() throws Exception {
    UUID holdingsWithPermLocation = createInstanceAndHolding(mainLibraryLocationId);
    UUID holdingsWithTempLocation = createInstanceAndHolding(mainLibraryLocationId, annexLibraryLocationId);

    Item itemWithHoldingPermLocation = buildItem(holdingsWithPermLocation, null, null);
    Item itemWithHoldingTempLocation = buildItem(holdingsWithTempLocation, null, null);
    Item itemWithTempLocation = buildItem(holdingsWithPermLocation, null, onlineLocationId);
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

    Item item = buildItem(initialHoldingsRecordId, null, onlineLocationId);
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
