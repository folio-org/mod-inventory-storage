package org.folio.rest.api;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.folio.rest.support.ResponseHandler.json;
import static org.folio.rest.support.http.InterfaceUrls.holdingsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.instancesStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.itemsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.dereferencedItemStorage;
import static org.folio.util.StringUtil.urlEncode;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.rest.jaxrs.model.DereferencedItem;
import org.folio.rest.jaxrs.model.DereferencedItems;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;


import io.vertx.core.json.JsonObject;
import junitparams.JUnitParamsRunner;


@RunWith(JUnitParamsRunner.class)
public class DereferencedItemStorageTest extends TestBaseWithInventoryUtil {
  private static final UUID smallAngryPlanetId = UUID.randomUUID();

  // see also @BeforeClass TestBaseWithInventoryUtil.beforeAny()

  @BeforeClass
  public static void beforeTests() throws InterruptedException, ExecutionException, TimeoutException {
    StorageTestSuite.deleteAll(itemsStorageUrl(""));
    StorageTestSuite.deleteAll(holdingsStorageUrl(""));
    StorageTestSuite.deleteAll(instancesStorageUrl(""));

    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    JsonObject smallAngryPlanet = smallAngryPlanet(smallAngryPlanetId, holdingsRecordId);
    JsonObject nod = nod(UUID.randomUUID(), holdingsRecordId);
    JsonObject uprooted = uprooted(UUID.randomUUID(), holdingsRecordId);

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    getCompleted = client.post(itemsStorageUrl(""), smallAngryPlanet, StorageTestSuite.TENANT_ID);
    Response response = getCompleted.get(5, SECONDS);
    assertThat(response.getStatusCode(), is(201));
    getCompleted = client.post(itemsStorageUrl(""), nod, StorageTestSuite.TENANT_ID);
    response = getCompleted.get(5, SECONDS);
    assertThat(response.getStatusCode(), is(201));
    getCompleted = client.post(itemsStorageUrl(""), uprooted, StorageTestSuite.TENANT_ID);
    response = getCompleted.get(5, SECONDS);
    assertThat(response.getStatusCode(), is(201));
  }

  @AfterClass
  public static void cleanUpDatabase() {
    StorageTestSuite.deleteAll(itemsStorageUrl(""));
    StorageTestSuite.deleteAll(holdingsStorageUrl(""));
    StorageTestSuite.deleteAll(instancesStorageUrl(""));
  }

  @Test
  public void CanGetRecordByCQLSearch() {
    String queryString = "barcode=036000291452";
    
    DereferencedItems items = findByCql(queryString);

    assertThat(items.getTotalRecords(), is(1));

    DereferencedItem item = items.getDereferencedItems().get(0);

    assertThat(item.getBarcode(), is("036000291452"));
    assertThat(item.getId(), is(smallAngryPlanetId.toString()));
    assertThat(item.getInstanceRecord().getTitle(), is("Long Way to a Small Angry Planet"));
    assertThat(item.getPermanentLoanType().getName(), is("Can Circulate"));
    assertThat(item.getMaterialType().getName(), is("journal"));
    assertThat(item.getHoldingsRecord().getInstanceId(), is(item.getInstanceRecord().getId()));
  }

  public void ResturnsAllRecordsWhenNoCQLQuery() {
    DereferencedItems items = getAll();

    assertThat(items.getTotalRecords(), is(3));
  }

  public void Returns404WhenNoItemsFound() {
    String queryString = "barcode=647671342075";
    
    Response response = attemptFindByCql(queryString);

    assertThat(response.getStatusCode(), is(404));
  }

  public void Returns400WhenCqlSearchInvalid() {
    String queryString = "barcode&647671342075";
    
    Response response = attemptFindByCql(queryString);

    assertThat(response.getStatusCode(), is(400));
  }

  public void CanGetRecordById() {
    DereferencedItem item = findById(smallAngryPlanetId.toString());

    assertThat(item.getBarcode(), is("036000291452"));
    assertThat(item.getId(), is(smallAngryPlanetId.toString()));
    assertThat(item.getInstanceRecord().getTitle(), is("Long Way to a Small Angry Planet"));
    assertThat(item.getPermanentLoanType().getName(), is("Can Circulate"));
    assertThat(item.getMaterialType().getName(), is("journal"));
    assertThat(item.getHoldingsRecord().getInstanceId(), is(item.getInstanceRecord().getId()));
  }

  public void Returns404WhenNoItemFoundForId() {
    String Id = UUID.randomUUID().toString();
    
    Response response = attemptFindById(Id);

    assertThat(response.getStatusCode(), is(404));
  }

  public void Returns400WhenInvalidUUID() {
    String Id = "w325b3dc4";
    
    Response response = attemptFindById(Id);

    assertThat(response.getStatusCode(), is(400));
  }


  private static JsonObject createItemRequest(
      UUID id,
      UUID holdingsRecordId,
      String barcode) {

    return createItemRequest(id, holdingsRecordId, barcode, journalMaterialTypeID);
  }

  private static JsonObject createItemRequest(
    UUID id,
    UUID holdingsRecordId,
    String barcode,
    String materialType) {

    JsonObject itemToCreate = new JsonObject();

    if(id != null) {
      itemToCreate.put("id", id.toString());
    }

    itemToCreate.put("holdingsRecordId", holdingsRecordId.toString());
    itemToCreate.put("barcode", barcode);
    itemToCreate.put("status", new JsonObject().put("name", "Available"));
    itemToCreate.put("materialTypeId", materialType);
    itemToCreate.put("permanentLoanTypeId", canCirculateLoanTypeID);
    itemToCreate.put("temporaryLocationId", annexLibraryLocationId.toString());
    itemToCreate.put("_version", 1);

    return itemToCreate;
  }

  private static JsonObject smallAngryPlanet(UUID itemId, UUID holdingsRecordId) {
    return createItemRequest(itemId, holdingsRecordId, "036000291452");
  }

  static JsonObject nod(UUID itemId, UUID holdingsRecordId) {
    return createItemRequest(itemId, holdingsRecordId, "565578437802");
  }

  static JsonObject nod(UUID holdingsRecordId) {
    return nod(UUID.randomUUID(), holdingsRecordId);
  }

  private static JsonObject uprooted(UUID itemId, UUID holdingsRecordId) {
    return createItemRequest(itemId, holdingsRecordId, "657670342075");
  }

  private Response attemptFindByCql(String badSearchQuery) {
    CompletableFuture<Response> searchCompleted = new CompletableFuture<>();

    client.get(dereferencedItemStorage("?query=") + urlEncode(badSearchQuery),
      StorageTestSuite.TENANT_ID, ResponseHandler.json(searchCompleted));
    try{
      return searchCompleted.get(5, TimeUnit.SECONDS);
    } catch(Exception e) {
      throw new RuntimeException(e);
    }
  }

  private Response attemptFindById(String badId) {
    CompletableFuture<Response> searchCompleted = new CompletableFuture<>();

    client.get(dereferencedItemStorage("/") + urlEncode(badId),
      StorageTestSuite.TENANT_ID, ResponseHandler.json(searchCompleted));
    try{
      return searchCompleted.get(5, TimeUnit.SECONDS);
    } catch(Exception e) {
      throw new RuntimeException(e);
    }
  }

  private DereferencedItems findByCql(String searchQuery) {
    CompletableFuture<Response> searchCompleted = new CompletableFuture<>();

    client.get(dereferencedItemStorage("?query=") + urlEncode(searchQuery),
      StorageTestSuite.TENANT_ID, ResponseHandler.json(searchCompleted));
    try{
      return searchCompleted.get(5, TimeUnit.SECONDS).getJson()
      .mapTo(DereferencedItems.class);
    } catch(Exception e) {
      throw new RuntimeException(e);
    }
  }

  private DereferencedItem findById(String id) {
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    client.get(dereferencedItemStorage("/" + id), TENANT_ID, json(getCompleted));
    try {
      return getCompleted.get(5, SECONDS).getJson()
      .mapTo(DereferencedItem.class);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }
  private DereferencedItems getAll() {
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    client.get(dereferencedItemStorage(""), TENANT_ID, json(getCompleted));
    try {
      return getCompleted.get(5, SECONDS).getJson()
      .mapTo(DereferencedItems.class);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

}
