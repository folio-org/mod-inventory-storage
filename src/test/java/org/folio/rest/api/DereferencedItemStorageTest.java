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
import lombok.SneakyThrows;


@RunWith(JUnitParamsRunner.class)
public class DereferencedItemStorageTest extends TestBaseWithInventoryUtil {
  private static final UUID smallAngryPlanetId = UUID.randomUUID();


  @BeforeClass
  public static void beforeTests() throws InterruptedException, ExecutionException, TimeoutException {
    StorageTestSuite.deleteAll(itemsStorageUrl(""));
    StorageTestSuite.deleteAll(holdingsStorageUrl(""));
    StorageTestSuite.deleteAll(instancesStorageUrl(""));

    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    JsonObject smallAngryPlanet = smallAngryPlanet(smallAngryPlanetId, holdingsRecordId);
    JsonObject nod = nod(UUID.randomUUID(), holdingsRecordId);
    JsonObject uprooted = uprooted(UUID.randomUUID(), holdingsRecordId);

    postItem(smallAngryPlanet);
    postItem(nod);
    postItem(uprooted);
  }

  @AfterClass
  public static void cleanUpDatabase() {
    StorageTestSuite.deleteAll(itemsStorageUrl(""));
    StorageTestSuite.deleteAll(holdingsStorageUrl(""));
    StorageTestSuite.deleteAll(instancesStorageUrl(""));
  }

  
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
    assertThat(item.getPermanentLocation().getName(), is("Annex Library"));
  }

  @Test
  public void ResturnsAllRecordsWhenNoCQLQuery() {
    DereferencedItems items = getAll();

    assertThat(items.getTotalRecords(), is(3));
  }

  @Test
  public void Returns404WhenNoItemsFound() {
    String queryString = "barcode=647671342075";
    
    Response response = attemptFindByCql(queryString);

    assertThat(response.getStatusCode(), is(404));
  }

  @Test
  public void Returns400WhenCqlSearchInvalid() {
    String queryString = "barcode&647671342075";
    
    Response response = attemptFindByCql(queryString);

    assertThat(response.getStatusCode(), is(400));
  }

  @Test
  public void CanGetRecordById() {
    DereferencedItem item = findById(smallAngryPlanetId.toString());

    assertThat(item.getBarcode(), is("036000291452"));
    assertThat(item.getId(), is(smallAngryPlanetId.toString()));
    assertThat(item.getInstanceRecord().getTitle(), is("Long Way to a Small Angry Planet"));
    assertThat(item.getPermanentLoanType().getName(), is("Can Circulate"));
    assertThat(item.getMaterialType().getName(), is("journal"));
    assertThat(item.getHoldingsRecord().getInstanceId(), is(item.getInstanceRecord().getId()));
    assertThat(item.getPermanentLocation().getName(), is("Annex Library"));
  }

  @Test
  public void Returns404WhenNoItemFoundForId() {
    String Id = UUID.randomUUID().toString();
    
    Response response = attemptFindById(Id);

    assertThat(response.getStatusCode(), is(404));
  }

  @Test
  public void Returns400WhenInvalidUUID() {
    String Id = "w325b3dc4";
    
    Response response = attemptFindById(Id);

    assertThat(response.getStatusCode(), is(400));
  }

  @SneakyThrows
  private static void postItem(JsonObject itemRecord) {
    CompletableFuture<Response> postCompleted = new CompletableFuture<>();
    postCompleted = client.post(itemsStorageUrl(""), itemRecord, StorageTestSuite.TENANT_ID);
    Response response = postCompleted.get(5, SECONDS);
    assertThat(response.getStatusCode(), is(201));
  }


  private static JsonObject createItemRequest(UUID id, UUID holdingsRecordId, String barcode) {

    return createItemRequest(id, holdingsRecordId, barcode, journalMaterialTypeID);
  }

  private static JsonObject createItemRequest(
    UUID id, UUID holdingsRecordId, String barcode, String materialType) {

    JsonObject itemToCreate = new JsonObject();

    if(id != null) {
      itemToCreate.put("id", id.toString());
    }

    itemToCreate.put("holdingsRecordId", holdingsRecordId.toString());
    itemToCreate.put("barcode", barcode);
    itemToCreate.put("status", new JsonObject().put("name", "Available"));
    itemToCreate.put("materialTypeId", materialType);
    itemToCreate.put("permanentLoanTypeId", canCirculateLoanTypeID);
    itemToCreate.put("permanentLocationId", annexLibraryLocationId.toString());
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

  @SneakyThrows
  private Response attemptFindByCql(String badSearchQuery) {
    CompletableFuture<Response> searchCompleted = new CompletableFuture<>();
    client.get(dereferencedItemStorage("?query=") + urlEncode(badSearchQuery),
      StorageTestSuite.TENANT_ID, ResponseHandler.text(searchCompleted));

    return searchCompleted.get(5, TimeUnit.SECONDS);
  }

  @SneakyThrows
  private Response attemptFindById(String badId) {
    CompletableFuture<Response> searchCompleted = new CompletableFuture<>();
    client.get(dereferencedItemStorage("/") + urlEncode(badId),
      StorageTestSuite.TENANT_ID, ResponseHandler.text(searchCompleted));

    return searchCompleted.get(5, TimeUnit.SECONDS);
  }

  @SneakyThrows
  private DereferencedItems findByCql(String searchQuery) {
    CompletableFuture<Response> searchCompleted = new CompletableFuture<>();
    client.get(dereferencedItemStorage("?query=") + urlEncode(searchQuery),
      StorageTestSuite.TENANT_ID, ResponseHandler.json(searchCompleted));

    return searchCompleted.get(5, TimeUnit.SECONDS).getJson()
      .mapTo(DereferencedItems.class);
  }

  @SneakyThrows
  private DereferencedItem findById(String id) {
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    client.get(dereferencedItemStorage("/" + id), TENANT_ID, json(getCompleted));

    return getCompleted.get(5, SECONDS).getJson()
      .mapTo(DereferencedItem.class);
  }

  @SneakyThrows
  private DereferencedItems getAll() {
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    client.get(dereferencedItemStorage(""), TENANT_ID, json(getCompleted));

    return getCompleted.get(5, SECONDS).getJson()
      .mapTo(DereferencedItems.class);
  }

}
