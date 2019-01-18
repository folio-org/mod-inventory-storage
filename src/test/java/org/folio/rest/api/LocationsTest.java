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
import static org.junit.Assert.assertThat;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.rest.support.AdditionalHttpStatusCodes;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.client.LoanTypesClient;
import org.folio.rest.support.client.MaterialTypesClient;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
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
  private static Logger logger = LoggerFactory.getLogger(LocationUnitTest.class);
  private static final String SUPPORTED_CONTENT_TYPE_JSON_DEF = "application/json";
  private String canCirculateLoanTypeID;
  private String journalMaterialTypeID;
  private static UUID instID;
  private static UUID campID;
  private static UUID libID;
  private static List<UUID> servicePointIDs = new ArrayList<UUID>();

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
            "SP Description", 0, false);
      }
    } catch (Exception e) { // should not happen
      throw new AssertionError("CreateLocUnits failed:", e);
    }
  }

  @BeforeClass
  public static void beforeAny()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    StorageTestSuite.deleteAll(itemsStorageUrl(""));
    StorageTestSuite.deleteAll(holdingsStorageUrl(""));
    StorageTestSuite.deleteAll(instancesStorageUrl(""));
  }

  @Before
  public void beforeEach()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

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
  public void canCreateLocation()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {
    Response response = createLocation(null, "Main Library", instID, campID, libID, "PI/CC/ML/X", servicePointIDs);
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
    assertThat(response.getJson().getString("id"), notNullValue());
    assertThat(response.getJson().getString("name"), is("Main Library"));
  }

  @Test
  public void cannotCreateLocationWithoutUnits()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {
    Response response = createLocation(null, "Main Library", null, null, null, "PI/CC/ML/X", servicePointIDs);
    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void cannotCreateLocationWithoutCode()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {
    Response response = createLocation(null, "Main Library", instID, campID, libID, null, servicePointIDs);
    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void cannotCreateLocationWithSameName()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {
    createLocation(null, "Main Library", "PI/CC/ML/X");
    Response response = createLocation(null, "Main Library", instID, campID, libID, "AA/BB", servicePointIDs);
    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void cannotCreateLocationWithSameCode()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {
    createLocation(null, "Main Library", "PI/CC/ML/X");
    Response response = createLocation(null, "Some Other Library", instID, campID, libID, "PI/CC/ML/X",
        servicePointIDs);
    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void cannotCreateLocationWithSameId()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {
    UUID id = UUID.randomUUID();
    createLocation(id, "Main Library", "PI/CC/ML/X");
    Response response = createLocation(id, "Some Other Library", instID, campID, libID, "AA/BB", servicePointIDs);
    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void canGetALocationById()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

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
  public void canListLocations()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    createLocation(null, "Main Library", "PI/CC/ML");
    createLocation(null, "Annex Library", "PI/CC/AL");
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    send(locationsStorageUrl("/"), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(getCompleted));
    Response getResponse = getCompleted.get(5, TimeUnit.SECONDS);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    JsonObject item = getResponse.getJson();
    assertThat(item.getInteger("totalRecords"), is(2));
  }

  @Test
  public void canUpdateALocation()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

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
    Response updateResponse = updated.get(5, TimeUnit.SECONDS);
    assertThat(updateResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
    Response getResponse = getById(id);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    JsonObject item = getResponse.getJson();
    assertThat(item.getString("id"), is(id.toString()));
    assertThat(item.getString("name"), is("Annex Library"));
  }
  @Test
  public void cannotUpdateId()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

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
    Response updateResponse = updated.get(5, TimeUnit.SECONDS);
    assertThat(updateResponse.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void canDeleteALocation()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {
    UUID id = UUID.randomUUID();
    createLocation(id, "Main Library", "PI/CC/ML/X");
    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();
    send(locationsStorageUrl("/" + id.toString()), HttpMethod.DELETE, null,
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(deleteCompleted));
    Response deleteResponse = deleteCompleted.get(5, TimeUnit.SECONDS);
    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
  }

  @Test
  public void cannotDeleteALocationAssociatedWithAnItem()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();
    createLocation(id, "Main Library", "PI/CC/ML/X");
    UUID holdingsRecordId = createInstanceAndHolding(id);
    JsonObject item = createItemRequest(holdingsRecordId.toString(), id.toString());
    CompletableFuture<Response> createItemCompleted = new CompletableFuture<>();
    send(itemsStorageUrl(""), HttpMethod.POST, item.toString(),
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(createItemCompleted));
    Response createItemResponse = createItemCompleted.get(5, TimeUnit.SECONDS);
    assertThat(createItemResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();
    send(locationsStorageUrl("/" + id.toString()),
      HttpMethod.DELETE, null, SUPPORTED_CONTENT_TYPE_JSON_DEF,
      ResponseHandler.any(deleteCompleted));
    Response deleteResponse = deleteCompleted.get(5, TimeUnit.SECONDS);
    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
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

  private JsonObject createItemRequest(String holdingsRecordId, String temporaryLocationId) {
    JsonObject item = new JsonObject();
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
      UUID inst, UUID camp, UUID lib, String code, List<UUID> servicePoints)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

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
    UUID spID = UUID.randomUUID();
    servicePointIDs.add(spID);
    putIfNotNull(request, "servicePointIds", new JsonArray(servicePoints));
    send(locationsStorageUrl(""), HttpMethod.POST, request.toString(),
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(createLocation));
    return createLocation.get(5, TimeUnit.SECONDS);
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

  private Response getById(UUID id)
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    send(locationsStorageUrl("/" + id.toString()), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(getCompleted));

    return getCompleted.get(5, TimeUnit.SECONDS);
  }

}

