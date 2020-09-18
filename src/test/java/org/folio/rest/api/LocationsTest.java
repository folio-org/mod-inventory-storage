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
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.folio.rest.support.AdditionalHttpStatusCodes;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.client.LoanTypesClient;
import org.folio.rest.support.client.MaterialTypesClient;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/* TODO: Missing tests
   - Bad inst/camp/lib in PUT
 */


public class LocationsTest extends TestBaseWithInventoryUtil {
  private static final Logger logger = LoggerFactory.getLogger(LocationUnitTest.class);
  private static final String SUPPORTED_CONTENT_TYPE_JSON_DEF = "application/json";
  private static UUID instID;
  private static UUID campID;
  private static UUID libID;
  private static final List<UUID> servicePointIDs = new ArrayList<>();

  protected static void createLocUnits(boolean force) {
    try {
      if (force || instID == null) {
        instID = UUID.randomUUID();
        LocationUnitTest.createInst(instID, "Primary Institution", "PI");
        campID = UUID.randomUUID();
        LocationUnitTest.createCamp(campID, "Central Campus", "CC", instID);
        libID = UUID.randomUUID();
        LocationUnitTest.createLib(libID, "Main Library", "ML", campID);
        UUID spID = UUID.randomUUID();
        servicePointIDs.add(spID);
        ServicePointTest.createServicePoint(spID, "Service Point", "SP", "Service Point",
            "SP Description", 0, false, null);
      }
    } catch (Exception e) { // should not happen
      throw new AssertionError("CreateLocUnits failed:", e);
    }
  }

  @Before
  public void beforeEach() {

    StorageTestSuite.deleteAll(itemsStorageUrl(""));
    StorageTestSuite.deleteAll(holdingsStorageUrl(""));
    StorageTestSuite.deleteAll(instancesStorageUrl(""));
    StorageTestSuite.deleteAll(locationsStorageUrl(""));
    StorageTestSuite.deleteAll(locLibraryStorageUrl(""));
    StorageTestSuite.deleteAll(locCampusStorageUrl(""));
    StorageTestSuite.deleteAll(locInstitutionStorageUrl(""));
    StorageTestSuite.deleteAll(loanTypesStorageUrl(""));
    StorageTestSuite.deleteAll(materialTypesStorageUrl(""));

    canCirculateLoanTypeID = new LoanTypesClient(
      new org.folio.rest.support.HttpClient(StorageTestSuite.getVertx()),
      loanTypesStorageUrl("")).create("Can Circulate");

    journalMaterialTypeID = new MaterialTypesClient(
      new org.folio.rest.support.HttpClient(StorageTestSuite.getVertx()),
      materialTypesStorageUrl("")).create("Journal");

    createLocUnits(true);
  }

  @Test
  public void canCreateLocation() {
    Response response = createLocation(null, "Main Library", instID, campID, libID, "PI/CC/ML/X", servicePointIDs);
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
    assertThat(response.getJson().getString("id"), notNullValue());
    assertThat(response.getJson().getString("name"), is("Main Library"));
  }

  @Test
  public void cannotCreateLocationWithoutUnits() {
    Response response = createLocation(null, "Main Library", null, null, null, "PI/CC/ML/X", servicePointIDs);
    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void cannotCreateLocationWithoutCode() {
    Response response = createLocation(null, "Main Library", instID, campID, libID, null, servicePointIDs);
    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void cannotCreateLocationWithSameName() {
    createLocation(null, "Main Library", "PI/CC/ML/X");
    Response response = createLocation(null, "Main Library", instID, campID, libID, "AA/BB", servicePointIDs);
    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void cannotCreateLocationWithSameCode() {
    createLocation(null, "Main Library", "PI/CC/ML/X");
    Response response = createLocation(null, "Some Other Library", instID, campID, libID, "PI/CC/ML/X",
        servicePointIDs);
    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void cannotCreateLocationWithSameId() {
    UUID id = UUID.randomUUID();
    createLocation(id, "Main Library", "PI/CC/ML/X");
    Response response = createLocation(id, "Some Other Library", instID, campID, libID, "AA/BB", servicePointIDs);
    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void canGetALocationById() {

    UUID id = UUID.randomUUID();
    createLocation(id, "Main Library", "PI/CC/ML/X");
    Response getResponse = getById(id);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    JsonObject item = getResponse.getJson();
    assertThat(item.getString("id"), is(id.toString()));
    assertThat(item.getString("name"), is("Main Library"));
    assertThat(item.getJsonObject("metadata").getString("createdByUserId"),
      is("test_user"));  // The userId header triggers creation of metadata
  }

  @Test
  public void canListLocations() {

    createLocation(null, "Main Library", "PI/CC/ML");
    createLocation(null, "Annex Library", "PI/CC/AL");
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    send(locationsStorageUrl("/"), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(getCompleted));
    Response getResponse = get(getCompleted);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    JsonObject item = getResponse.getJson();
    assertThat(item.getInteger("totalRecords"), is(2));
  }

  @Test
  public void canUpdateALocation() {

    UUID id = UUID.randomUUID();
    createLocation(id, "Main Library", "PI/CC/ML/X");
    JsonObject updateRequest = new JsonObject()
        .put("id", id.toString())
      .put("name", "Annex Library")
      .put("institutionId", instID.toString())
      .put("campusId", campID.toString())
      .put("libraryId", libID.toString())
      .put("isActive", true)
      .put("code", "AA/BB")
        .put("primaryServicePoint", servicePointIDs.get(0).toString())
        .put("servicePointIds", new JsonArray(servicePointIDs));
    CompletableFuture<Response> updated = new CompletableFuture<>();
    send(locationsStorageUrl("/" + id.toString()), HttpMethod.PUT,
      updateRequest.toString(), SUPPORTED_CONTENT_TYPE_JSON_DEF,
      ResponseHandler.any(updated));
    Response updateResponse = get(updated);
    assertThat(updateResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
    Response getResponse = getById(id);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    JsonObject item = getResponse.getJson();
    assertThat(item.getString("id"), is(id.toString()));
    assertThat(item.getString("name"), is("Annex Library"));
  }

  @Test
  public void cannotUpdateId() {

    UUID id = UUID.randomUUID();
    createLocation(id, "Main Library", "PI/CC/ML/X");
    JsonObject updateRequest = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("name", "Annex Library")
      .put("institutionId", instID.toString())
      .put("campusId", campID.toString())
      .put("libraryId", libID.toString())
      .put("isActive", true)
        .put("code", "AA/BB").put("primaryServicePoint", servicePointIDs.get(0).toString())
        .put("servicePointIds", new JsonArray(servicePointIDs));
    CompletableFuture<Response> updated = new CompletableFuture<>();
    send(locationsStorageUrl("/" + id.toString()), HttpMethod.PUT,
      updateRequest.toString(), SUPPORTED_CONTENT_TYPE_JSON_DEF,
      ResponseHandler.any(updated));
    Response updateResponse = get(updated);
    assertThat(updateResponse.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void canDeleteALocation() {
    UUID id = UUID.randomUUID();
    createLocation(id, "Main Library", "PI/CC/ML/X");
    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();
    send(locationsStorageUrl("/" + id.toString()), HttpMethod.DELETE, null,
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(deleteCompleted));
    Response deleteResponse = get(deleteCompleted);
    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
  }

  @Test
  public void cannotDeleteALocationAssociatedWithAnItem() {

    UUID id = UUID.randomUUID();
    createLocation(id, "Main Library", "PI/CC/ML/X");
    UUID holdingsRecordId = createInstanceAndHolding(id);
    JsonObject item = createItemRequest(holdingsRecordId.toString(), id.toString());
    CompletableFuture<Response> createItemCompleted = new CompletableFuture<>();
    send(itemsStorageUrl(""), HttpMethod.POST, item.toString(),
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(createItemCompleted));
    Response createItemResponse = get(createItemCompleted);
    assertThat(createItemResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();
    send(locationsStorageUrl("/" + id.toString()),
      HttpMethod.DELETE, null, SUPPORTED_CONTENT_TYPE_JSON_DEF,
      ResponseHandler.any(deleteCompleted));
    Response deleteResponse = get(deleteCompleted);
    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
  }

  @Test
  public void canSearchByPrimaryServicePoint() throws Exception {
    final UUID firstServicePointId = UUID.randomUUID();
    final UUID secondServicePointId = UUID.randomUUID();
    final UUID expectedLocationId = UUID.randomUUID();

    createLocation(expectedLocationId, "Main", instID, campID, libID, "main",
      Collections.singletonList(firstServicePointId));
    createLocation(null, "Main two", instID, campID, libID, "main/tw",
      Collections.singletonList(secondServicePointId));

    final List<JsonObject> locations = getMany("primaryServicePoint==\"%s\"", firstServicePointId);

    assertThat(locations.size(), is(1));
    assertThat(locations.get(0).getString("id"), is(expectedLocationId.toString()));
  }

  ///////////////////////////// helpers
  private static void send(URL url, HttpMethod method, String content,
                           String contentType, Handler<HttpClientResponse> handler) {

    HttpClient client = StorageTestSuite.getVertx().createHttpClient();
    HttpClientRequest request;

    if(content == null){
      content = "";
    }
    Buffer buffer = Buffer.buffer(content);

    if (method == HttpMethod.POST) {
      request = client.postAbs(url.toString());
    }
    else if (method == HttpMethod.DELETE) {
      request = client.deleteAbs(url.toString());
    }
    else if (method == HttpMethod.GET) {
      request = client.getAbs(url.toString());
    }
    else {
      request = client.putAbs(url.toString());
    }
    request.exceptionHandler(error -> {
      Assert.fail(error.getLocalizedMessage());
    })
    .handler(handler);

    request.putHeader("X-Okapi-Tenant", "test_tenant");
    request.putHeader("X-Okapi-User-Id", "test_user");
    request.putHeader("Accept", "application/json,text/plain");
    request.putHeader("Content-type", contentType);
    request.end(buffer);
  }

  private List<JsonObject> getMany(String cql, Object... args) {

    final CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    send(locationsStorageUrl("?query=" + String.format(cql, args)),
      HttpMethod.GET, null, SUPPORTED_CONTENT_TYPE_JSON_DEF,
      ResponseHandler.json(getCompleted));

    return get(getCompleted).getJson()
      .getJsonArray("locations").stream()
      .map(obj -> (JsonObject) obj)
      .collect(Collectors.toList());
  }

  private JsonObject createItemRequest(String holdingsRecordId, String temporaryLocationId) {
    JsonObject item = new JsonObject();

    item.put("status", new JsonObject().put("name", "Available"));
    item.put("holdingsRecordId", holdingsRecordId);
    item.put("barcode", "12345");
    item.put("permanentLoanTypeId", canCirculateLoanTypeID);
    item.put("materialTypeId", journalMaterialTypeID);
    item.put("temporaryLocationId", temporaryLocationId);
    return item;
  }

  private static void putIfNotNull(JsonObject js, String key, String value) {
    if (value != null) {
      js.put(key, value);
    }
  }

  private static void putIfNotNull(JsonObject js, String key, UUID value) {
    if (value != null) {
      js.put(key, value.toString());
    }
  }

  private static void putIfNotNull(JsonObject js, String key, JsonArray value) {
    if (value != null) {
      js.put(key, value);
    }
  }

  public static Response createLocation(UUID id, String name,
      UUID inst, UUID camp, UUID lib, String code, List<UUID> servicePoints) {

    CompletableFuture<Response> createLocation = new CompletableFuture<>();
    JsonObject request = new JsonObject()
      .put("name", name)
      .put("discoveryDisplayName", "d:" + name)
      .put("description", "something like " + name);
    putIfNotNull(request, "id", id);
    putIfNotNull(request, "institutionId", inst);
    putIfNotNull(request, "campusId", camp);
    putIfNotNull(request, "libraryId", lib);
    putIfNotNull(request, "code", code);
    putIfNotNull(request, "primaryServicePoint", servicePoints.get(0));
    putIfNotNull(request, "isActive", "true");
    UUID spID = UUID.randomUUID();
    servicePointIDs.add(spID);
    putIfNotNull(request, "servicePointIds", new JsonArray(servicePoints));
    send(locationsStorageUrl(""), HttpMethod.POST, request.toString(),
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(createLocation));
    return get(createLocation);
  }

  /**
   * Helper to create a Location record the way old shelfLocations were created.
   * Used mostly while migrating to new Locations
   *
   * @param id
   * @param name
   * @param servicePoint
   * @param libID2
   * @param campID2
   * @param instID2
   * @return
   */
  public static UUID createLocation(UUID id, String name, String code) {
    try {
      createLocUnits(false);
      if (id == null) {
        id = UUID.randomUUID();
      }
      createLocation(id, name, instID, campID, libID, code, servicePointIDs);
    } catch (Exception e) {
      throw new AssertionError("CreateLocation failed:", e);
    }
    logger.debug("createLocation " + id + " '" + name + "' i=" + instID);
    return id;
  }

  private Response getById(UUID id) {

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    send(locationsStorageUrl("/" + id.toString()), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(getCompleted));

    return get(getCompleted);
  }

}
