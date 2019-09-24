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

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
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
  private static final String MAIN_LIBRARY_LOCATION = "Main Library (Item)";
  private static final String SECOND_FLOOR_LOCATION = "Second Floor (item)";
  private static final String THIRD_FLOOR_LOCATION = "Third Floor (item)";
  private static final String FOURTH_FLOOR_LOCATION = "Fourth Floor (item)";
  private static final String ANNEX_LIBRARY_LOCATION = "Annex Library (item)";
  private static final String ONLINE_LOCATION = "Online (item)";

  private static String journalMaterialTypeID;
  private static String canCirculateLoanTypeID;
  private static UUID instanceId;
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

    instanceId = instancesClient.create(instance(UUID.randomUUID())).getId();

    LocationsTest.createLocUnits(true);
    mainLibraryLocationId = LocationsTest.createLocation(null, MAIN_LIBRARY_LOCATION, "It/M");
    annexLibraryLocationId = LocationsTest.createLocation(null, ANNEX_LIBRARY_LOCATION, "It/A");
    onlineLocationId = LocationsTest.createLocation(null, ONLINE_LOCATION, "It/O");
    secondFloorLocationId = LocationsTest.createLocation(null, SECOND_FLOOR_LOCATION, "It/SF");
    LocationsTest.createLocation(null, THIRD_FLOOR_LOCATION, "It/TF");
    LocationsTest.createLocation(null, FOURTH_FLOOR_LOCATION, "It/FoF");
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

  @Test
  @Parameters(method = "parameters")
  public void canCalculateEffectiveLocationOnItemUpdate(
      String holdingPerm, String holdingTemp, String itemPerm, String itemTemp,
      String newItemPerm, String newItemTemp)
    throws Exception {

    UUID holdingPermId = lookupLocationByName(holdingPerm);
    UUID holdingTempId = lookupLocationByName(holdingTemp);
    UUID itemPermId = lookupLocationByName(itemPerm);
    UUID itemTempId = lookupLocationByName(itemTemp);
    UUID newItemPermId = lookupLocationByName(newItemPerm);
    UUID newItemTempId = lookupLocationByName(newItemTemp);

    UUID holdingsRecordId = createHolding(instanceId, holdingPermId, holdingTempId);

    Item item = buildItem(holdingsRecordId, itemPermId, itemTempId);
    UUID itemId = UUID.fromString(item.getId());
    IndividualResource itemResource = createItem(item);
    JsonObject item2 = itemResource.getJson();
    assertEffectiveLocation(item2, holdingPermId, holdingTempId, itemPermId, itemTempId);

    setPermanentTemporaryLocation(item2, newItemPermId, newItemTempId);
    itemsClient.replace(itemId, item2);
    JsonObject item3 = itemsClient.getById(itemId).getJson();
    assertEffectiveLocation(item3, holdingPermId, holdingTempId, newItemPermId, newItemTempId);
  }

  // re-use swapping item and holding
  @Test
  @Parameters(method = "parameters")
  public void canCalculateEffectiveLocationOnHoldingsUpdate(
      String itemPerm, String itemTemp, String holdingPerm, String holdingTemp,
      String newHoldingPerm, String newHoldingTemp) throws Exception {

    UUID holdingPermId = lookupLocationByName(holdingPerm);
    UUID holdingTempId = lookupLocationByName(holdingTemp);
    UUID itemPermId = lookupLocationByName(itemPerm);
    UUID itemTempId = lookupLocationByName(itemTemp);
    UUID newHoldingPermId = lookupLocationByName(newHoldingPerm);
    UUID newHoldingTempId = lookupLocationByName(newHoldingTemp);

    UUID holdingsRecordId = createHolding(instanceId, holdingPermId, holdingTempId);

    Item item = buildItem(holdingsRecordId, itemPermId, itemTempId);
    UUID itemId = UUID.fromString(item.getId());
    JsonObject item2 = createItem(item).getJson();
    assertEffectiveLocation(item2, holdingPermId, holdingTempId, itemPermId, itemTempId);

    JsonObject holding2 = holdingsClient.getById(holdingsRecordId).getJson();
    setPermanentTemporaryLocation(holding2, newHoldingPermId, newHoldingTempId);
    holdingsClient.replace(holdingsRecordId, holding2);
    JsonObject item3 = itemsClient.getById(itemId).getJson();
    assertEffectiveLocation(item3, newHoldingPermId, newHoldingTempId, itemPermId, itemTempId);
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

  // is actually used by @Parameters(method = "parameters")
  @SuppressWarnings("unused")
  private List<String[]> parameters() {
    List<String[]> holdingLocations = Arrays.asList(
      new String[]{null, null},
      new String[]{null, MAIN_LIBRARY_LOCATION},
      new String[]{SECOND_FLOOR_LOCATION, null},
      new String[]{SECOND_FLOOR_LOCATION, MAIN_LIBRARY_LOCATION}
    );

    List<String[]> itemLocations = Arrays.asList(
      new String[]{null, null},
      new String[]{null, THIRD_FLOOR_LOCATION},
      new String[]{FOURTH_FLOOR_LOCATION, null},
      new String[]{FOURTH_FLOOR_LOCATION, THIRD_FLOOR_LOCATION}
    );

    List<String[]> newItemLocations = Arrays.asList(
      new String[]{null, null},
      new String[]{null, THIRD_FLOOR_LOCATION},
      new String[]{THIRD_FLOOR_LOCATION, null},
      new String[]{THIRD_FLOOR_LOCATION, FOURTH_FLOOR_LOCATION},
      new String[]{null, ANNEX_LIBRARY_LOCATION},
      new String[]{ANNEX_LIBRARY_LOCATION, null},
      new String[]{ANNEX_LIBRARY_LOCATION, ONLINE_LOCATION}
    );

    // Combine the locations
    List<String[]> parameters = new ArrayList<>();
    for (String[] holdingsLocation : holdingLocations) {
      for (String[] itemLocation : itemLocations) {
        for (String[] newItemLocation : newItemLocations) {

          parameters.add(new String[]{
            holdingsLocation[0], holdingsLocation[1],
            itemLocation[0], itemLocation[1],
            newItemLocation[1], newItemLocation[1]
          });
        }
      }
    }

    return parameters;
  }

  private UUID lookupLocationByName(String name)
    throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {
    if (name == null) {
      return null;
    }

    return locationsClient.getAll().stream()
      .filter(json -> json.getString("name").equals(name))
      .map(json -> UUID.fromString(json.getString("id")))
      .findAny()
      .orElse(null);
  }

  private void setPermanentTemporaryLocation(
    JsonObject json, UUID permanentLocationId, UUID tempLocationId) {
    String permanentLocationIdFieldName = "permanentLocationId";
    String temporaryLocationIdFieldName = "temporaryLocationId";

    json.remove(permanentLocationIdFieldName);
    json.remove(temporaryLocationIdFieldName);

    if (permanentLocationId != null) {
      json.put(permanentLocationIdFieldName, permanentLocationId.toString());
    }

    if (tempLocationId != null) {
      json.put(temporaryLocationIdFieldName, tempLocationId.toString());
    }
  }

  private void assertEffectiveLocation(JsonObject item, UUID holdingPerm, UUID holdingTemp, UUID itemPerm, UUID itemTemp) {
    UUID expectedEffectiveLocation = ObjectUtils
      .firstNonNull(itemTemp, itemPerm, holdingTemp, holdingPerm);

    if (expectedEffectiveLocation == null) {
      // { "effectiveLocationId": null } is not allowed, the property must have been removed
      assertThat(item.containsKey("effectiveLocationId"), is(false));
      return;
    }
    assertThat(item.getString("effectiveLocationId"), is(expectedEffectiveLocation.toString()));
  }
}
