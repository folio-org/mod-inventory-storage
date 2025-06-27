package org.folio.rest.api;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.rest.support.ResponseHandler.json;
import static org.folio.rest.support.http.InterfaceUrls.dereferencedItemStorage;
import static org.folio.rest.support.http.InterfaceUrls.holdingsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.instancesStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.itemsStorageUrl;
import static org.folio.util.StringUtil.urlEncode;
import static org.folio.utility.ModuleUtility.getClient;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNull;

import io.vertx.core.json.JsonObject;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import org.folio.rest.jaxrs.model.DereferencedItem;
import org.folio.rest.jaxrs.model.DereferencedItems;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class DereferencedItemStorageTest extends TestBaseWithInventoryUtil {
  private static final UUID SMALL_ANGRY_PLANET_ID = UUID.randomUUID();
  private static final UUID UPROOTED_ID = UUID.randomUUID();

  @SneakyThrows
  @BeforeClass
  public static void beforeAll() {
    TestBase.beforeAll();

    StorageTestSuite.deleteAll(itemsStorageUrl(""));
    StorageTestSuite.deleteAll(holdingsStorageUrl(""));
    StorageTestSuite.deleteAll(instancesStorageUrl(""));

    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

    JsonObject smallAngryPlanet = smallAngryPlanet(SMALL_ANGRY_PLANET_ID, holdingsRecordId);
    JsonObject nod = nod(UUID.randomUUID(), holdingsRecordId);
    JsonObject uprooted = uprooted(UPROOTED_ID, holdingsRecordId);

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

  @SneakyThrows
  private static void postItem(JsonObject itemRecord) {
    CompletableFuture<Response> postCompleted = getClient().post(itemsStorageUrl(""), itemRecord, TENANT_ID);
    Response response = postCompleted.get(10, SECONDS);
    assertThat(response.getStatusCode(), is(201));
  }

  private static JsonObject createItemRequest(
    UUID id, UUID holdingsRecordId, String barcode, Boolean includeOptionalFields) {

    return createItemRequest(id, holdingsRecordId, barcode, journalMaterialTypeID, includeOptionalFields);
  }

  private static JsonObject createItemRequest(
    UUID id, UUID holdingsRecordId, String barcode,
    String materialType, Boolean includeOptionalFields) {

    JsonObject itemToCreate = new JsonObject();

    if (id != null) {
      itemToCreate.put("id", id.toString());
    }

    itemToCreate.put("holdingsRecordId", holdingsRecordId.toString());
    itemToCreate.put("barcode", barcode);
    itemToCreate.put("status", new JsonObject().put("name", "Available"));
    itemToCreate.put("materialTypeId", materialType);
    itemToCreate.put("permanentLoanTypeId", canCirculateLoanTypeID);
    itemToCreate.put("permanentLocationId", ANNEX_LIBRARY_LOCATION_ID.toString());
    itemToCreate.put("_version", 1);

    if (includeOptionalFields) {
      itemToCreate.put("temporaryLoanTypeId", nonCirculatingLoanTypeID);
      itemToCreate.put("temporaryLocationId", SECOND_FLOOR_LOCATION_ID);
    }

    return itemToCreate;
  }

  private static JsonObject smallAngryPlanet(UUID itemId, UUID holdingsRecordId) {
    return createItemRequest(itemId, holdingsRecordId, "036000291452", false);
  }

  static JsonObject nod(UUID itemId, UUID holdingsRecordId) {
    return createItemRequest(itemId, holdingsRecordId, "565578437802", false);
  }

  private static JsonObject uprooted(UUID itemId, UUID holdingsRecordId) {
    return createItemRequest(itemId, holdingsRecordId, "657670342075", true);
  }

  @Test
  public void canGetRecordByBarcode() {
    String queryString = "barcode==036000291452";
    String queryString2 = "barcode==657670342075";

    DereferencedItems items = findByCql(queryString);

    assertThat(items.getTotalRecords(), is(1));

    DereferencedItem item = items.getDereferencedItems().getFirst();

    testSmallAngryPlanet(item);

    items = findByCql(queryString2);

    assertThat(items.getTotalRecords(), is(1));

    item = items.getDereferencedItems().getFirst();

    testUprooted(item);
  }

  @Test
  public void resturnsAllRecordsWhenNoCqlQuery() {
    DereferencedItems items = getAll();

    assertThat(items.getTotalRecords(), is(3));
  }

  @Test
  public void returnsEmptyCollectionWhenNoItemsFound() {
    String queryString = "barcode==647671342075";

    DereferencedItems items = findByCql(queryString);

    assertThat(items.getTotalRecords(), is(0));
  }

  @Test
  public void returns400WhenCqlSearchInvalid() {
    String queryString = "barcode&647671342075";

    Response response = attemptFindByCql(queryString);

    assertThat(response.getStatusCode(), is(400));
  }

  @Test
  public void canGetRecordById() {
    testSmallAngryPlanet(findById(SMALL_ANGRY_PLANET_ID.toString()));

    testUprooted(findById(UPROOTED_ID.toString()));
  }

  @Test
  public void returns404WhenNoItemFoundForId() {
    String id = UUID.randomUUID().toString();

    Response response = attemptFindById(id);

    assertThat(response.getStatusCode(), is(404));
  }

  @Test
  public void returns400WhenInvalidUuid() {
    String id = "w325b3dc4";

    Response response = attemptFindById(id);

    assertThat(response.getStatusCode(), is(400));
  }

  private void testSmallAngryPlanet(DereferencedItem item) {
    assertThat(item.getBarcode(), is("036000291452"));
    assertThat(item.getId(), is(SMALL_ANGRY_PLANET_ID.toString()));
    assertThat(item.getInstanceRecord().getTitle(), is("Long Way to a Small Angry Planet"));
    assertThat(item.getPermanentLoanType().getName(), is("Can Circulate"));
    assertThat(item.getMaterialType().getName(), is("journal"));
    assertThat(item.getHoldingsRecord().getInstanceId(), is(item.getInstanceRecord().getId()));
    assertThat(item.getPermanentLocation().getName(), is("Annex Library"));
    assertNull(item.getTemporaryLocation());
    assertThat(item.getEffectiveLocation().getName(), is(item.getPermanentLocation().getName()));
    assertNull(item.getTemporaryLoanType());
  }

  private void testUprooted(DereferencedItem item) {
    assertThat(item.getBarcode(), is("657670342075"));
    assertThat(item.getId(), is(UPROOTED_ID.toString()));
    assertThat(item.getInstanceRecord().getTitle(), is("Long Way to a Small Angry Planet"));
    assertThat(item.getPermanentLoanType().getName(), is("Can Circulate"));
    assertThat(item.getMaterialType().getName(), is("journal"));
    assertThat(item.getHoldingsRecord().getInstanceId(), is(item.getInstanceRecord().getId()));
    assertThat(item.getPermanentLocation().getName(), is("Annex Library"));
    assertThat(item.getTemporaryLocation().getName(), is("Second Floor"));
    assertThat(item.getEffectiveLocation().getName(), is(item.getTemporaryLocation().getName()));
    assertThat(item.getTemporaryLoanType().getName(), is("Non-Circulating"));
  }

  @SneakyThrows
  private Response attemptFindByCql(String badSearchQuery) {
    CompletableFuture<Response> searchCompleted = new CompletableFuture<>();
    getClient().get(dereferencedItemStorage("?query=") + urlEncode(badSearchQuery),
      TENANT_ID, ResponseHandler.text(searchCompleted));

    return searchCompleted.get(TIMEOUT, TimeUnit.SECONDS);
  }

  @SneakyThrows
  private Response attemptFindById(String badId) {
    CompletableFuture<Response> searchCompleted = new CompletableFuture<>();
    getClient().get(dereferencedItemStorage("/") + urlEncode(badId),
      TENANT_ID, ResponseHandler.text(searchCompleted));

    return searchCompleted.get(TIMEOUT, TimeUnit.SECONDS);
  }

  @SneakyThrows
  private DereferencedItems findByCql(String searchQuery) {
    CompletableFuture<Response> searchCompleted = new CompletableFuture<>();
    getClient().get(dereferencedItemStorage("?query=") + urlEncode(searchQuery),
      TENANT_ID, ResponseHandler.json(searchCompleted));

    return searchCompleted.get(TIMEOUT, TimeUnit.SECONDS).getJson()
      .mapTo(DereferencedItems.class);
  }

  @SneakyThrows
  private DereferencedItem findById(String id) {
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    getClient().get(dereferencedItemStorage("/" + id), TENANT_ID, json(getCompleted));

    return getCompleted.get(10, SECONDS).getJson()
      .mapTo(DereferencedItem.class);
  }

  @SneakyThrows
  private DereferencedItems getAll() {
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    getClient().get(dereferencedItemStorage(""), TENANT_ID, json(getCompleted));

    return getCompleted.get(10, SECONDS).getJson()
      .mapTo(DereferencedItems.class);
  }
}
