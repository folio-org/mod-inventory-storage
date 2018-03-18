package org.folio.rest.api;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.client.LoanTypesClient;
import org.folio.rest.support.client.MaterialTypesClient;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.folio.rest.support.AdditionalHttpStatusCodes;

import static org.folio.rest.support.HttpResponseMatchers.statusCodeIs;
import static org.folio.rest.support.http.InterfaceUrls.*;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

/* TODO: Missing tests
   - Bad inst/camp/lib in PUT
 */


public class LocationsTest {
  private Logger logger = LoggerFactory.getLogger(LocationUnitTest.class);
  private static final String SUPPORTED_CONTENT_TYPE_JSON_DEF = "application/json";
  private String canCirculateLoanTypeID;
  private String journalMaterialTypeID;
  private UUID instID;
  private UUID campID;
  private UUID libID;

  @Before
  public void beforeEach()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    StorageTestSuite.deleteAll(itemsStorageUrl(""));
    StorageTestSuite.deleteAll(locationsStorageUrl(""));
    StorageTestSuite.deleteAll(locInstitutionStorageUrl(""));
    StorageTestSuite.deleteAll(locCampusStorageUrl(""));
    StorageTestSuite.deleteAll(locLibraryStorageUrl(""));
    StorageTestSuite.deleteAll(loanTypesStorageUrl(""));
    StorageTestSuite.deleteAll(materialTypesStorageUrl(""));

    canCirculateLoanTypeID = new LoanTypesClient(
      new org.folio.rest.support.HttpClient(StorageTestSuite.getVertx()),
      loanTypesStorageUrl("")).create("Can Circulate");

    journalMaterialTypeID = new MaterialTypesClient(
      new org.folio.rest.support.HttpClient(StorageTestSuite.getVertx()),
      materialTypesStorageUrl("")).create("Journal");

    instID = UUID.randomUUID();
    LocationUnitTest.createInst(instID, "Primary Institution", "PI");
    campID = UUID.randomUUID();
    LocationUnitTest.createCamp(campID, "Central Campus", "CC", instID);
    libID = UUID.randomUUID();
    LocationUnitTest.createLib(libID, "Main Library", "ML", campID);

  }

  @Test
  public void canCreateLocation()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {
    Response response = createLocation(null, "Main Library", instID, campID, libID, "PI/CC/ML/X");
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
    Response response = createLocation(null, "Main Library", null, null, null, "PI/CC/ML/X");
    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void cannotCreateLocationWithoutCode()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {
    Response response = createLocation(null, "Main Library", instID, campID, libID, null);
    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void cannotCreateLocationWithSameName()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {
    createLocation(null, "Main Library", instID, campID, libID, "PI/CC/ML/X");
    Response response = createLocation(null, "Main Library", instID, campID, libID, "AA/BB");
    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void cannotCreateLocationWithSameCode()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {
    createLocation(null, "Main Library", instID, campID, libID, "PI/CC/ML/X");
    Response response = createLocation(null, "Some Other Library", instID, campID, libID, "PI/CC/ML/X");
    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void cannotCreateLocationWithSameId()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {
    UUID id = UUID.randomUUID();
    createLocation(id, "Main Library", instID, campID, libID, "PI/CC/ML/X");
    Response response = createLocation(id, "Some Other Library", instID, campID, libID, "AA/BB");
    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void canGetALocationById()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();
    createLocation(id, "Main Library", instID, campID, libID, "PI/CC/ML/X");
    Response getResponse = getById(id);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    JsonObject item = getResponse.getJson();
    assertThat(item.getString("id"), is(id.toString()));
    assertThat(item.getString("name"), is("Main Library"));
  }

  @Test
  public void canUpdateALocation()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();
    createLocation(id, "Main Library", instID, campID, libID, "PI/CC/ML/X");
    JsonObject updateRequest = new JsonObject()
      .put("id", id.toString())
      .put("name", "Annex Library")
      .put("institutionId", instID.toString())
      .put("campusId", campID.toString())
      .put("libraryId", libID.toString())
      .put("isActive", true)
      .put("code", "AA/BB");
    CompletableFuture<Response> updated = new CompletableFuture<>();
    send(locationsStorageUrl("/" + id.toString()), HttpMethod.PUT,
      updateRequest.toString(), SUPPORTED_CONTENT_TYPE_JSON_DEF,
      ResponseHandler.any(updated));
    Response updateResponse = updated.get(5, TimeUnit.SECONDS);
    assertThat(updateResponse, statusCodeIs(HttpURLConnection.HTTP_NO_CONTENT));
    Response getResponse = getById(id);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    JsonObject item = getResponse.getJson();
    assertThat(item.getString("id"), is(id.toString()));
    assertThat(item.getString("name"), is("Annex Library"));
  }

  @Test
  public void canDeleteALocation()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {
    UUID id = UUID.randomUUID();
    createLocation(id, "Main Library", instID, campID, libID, "PI/CC/ML/X");
    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();
    send(locationsStorageUrl("/" + id.toString()), HttpMethod.DELETE, null,
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(deleteCompleted));
    Response deleteResponse = deleteCompleted.get(5, TimeUnit.SECONDS);
    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
  }

  /*
  This test does not work! It tries to create an item object that points to
  our new kind of Location object. But RMB sees that the UUID for the location
  does not point to a valid (old-style) shelf-location item, and helpfully
  refuses to create the item...

  When we switch items over to pointing to the new Locations, re-enable this
  test.

  @Test
  public void cannotDeleteALocationAssociatedWithAnItem()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    logger.warn("cannotDeleteALocationAssociatedWithAnItem starting XXXX");
    UUID id = UUID.randomUUID();
    createLocation(id, "Main Library", instID, campID, libID, "PI/CC/ML/X");
    JsonObject item = createItemRequest(id.toString());
    CompletableFuture<Response> createItemCompleted = new CompletableFuture<>();
    send(itemsStorageUrl(""), HttpMethod.POST, item.toString(),
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(createItemCompleted));
    Response createItemResponse = createItemCompleted.get(5, TimeUnit.SECONDS);
    assertThat(createItemResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();
    send(locationsStorageUrl(id.toString()),
      HttpMethod.DELETE, null, SUPPORTED_CONTENT_TYPE_JSON_DEF,
      ResponseHandler.any(deleteCompleted));
    Response deleteResponse = deleteCompleted.get(5, TimeUnit.SECONDS);
    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
    logger.warn("cannotDeleteALocationAssociatedWithAnItem done XXXX");
  }
*/
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

    request.putHeader("Authorization", "test_tenant");
    request.putHeader("x-okapi-tenant", "test_tenant");
    request.putHeader("Accept", "application/json,text/plain");
    request.putHeader("Content-type", contentType);
    request.end(buffer);
  }

  private JsonObject createItemRequest(String temporaryLocationId) {
    JsonObject item = new JsonObject();
    item.put("holdingsRecordId", UUID.randomUUID().toString());
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

  public Response createLocation(UUID id, String name,
    UUID inst, UUID camp, UUID lib, String code)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<Response> createLocation = new CompletableFuture<>();
    JsonObject request = new JsonObject()
      .put("name", name);
    putIfNotNull(request, "id", id);
    putIfNotNull(request, "institutionId", inst);
    putIfNotNull(request, "campusId", camp);
    putIfNotNull(request, "libraryId", lib);
    putIfNotNull(request, "code", code);
    send(locationsStorageUrl(""), HttpMethod.POST, request.toString(),
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(createLocation));
    return createLocation.get(5, TimeUnit.SECONDS);
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

