package org.folio.rest.api;

import static org.folio.rest.support.HttpResponseMatchers.statusCodeIs;
import static org.folio.rest.support.http.InterfaceUrls.itemsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.loanTypesStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.locCampusStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.locInstitutionStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.locLibraryStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.locationsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.materialTypesStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.servicePointsUrl;
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


public class LocationsTest {
  private static Logger logger = LoggerFactory.getLogger(LocationUnitTest.class);
  private static final String SUPPORTED_CONTENT_TYPE_JSON_DEF = "application/json";
  private String canCirculateLoanTypeID;
  private String journalMaterialTypeID;
  private static UUID instID;
  private static UUID campID;
  private static UUID libID;

  protected static void createLocUnits(boolean force) {
    try {
      if (force || instID == null) {
        instID = UUID.randomUUID();
        LocationUnitTest.createInst(instID, "Primary Institution", "PI");
        campID = UUID.randomUUID();
        LocationUnitTest.createCamp(campID, "Central Campus", "CC", instID);
        libID = UUID.randomUUID();
        LocationUnitTest.createLib(libID, "Main Library", "ML", campID);
      }
    } catch (Exception e) { // should not happen
      throw new AssertionError("CreateLocUnits failed:", e);
    }
  }

  @Before
  public void beforeEach()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    StorageTestSuite.deleteAll(itemsStorageUrl(""));
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
    assertThat(item.getJsonObject("metadata").getString("createdByUserId"),
      is("test_user"));  // The userId header triggers creation of metadata
  }

  @Test
  public void canListLocations()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    createLocation(null, "Main Library", instID, campID, libID, "PI/CC/ML");
    createLocation(null, "Annex Library", instID, campID, libID, "PI/CC/AL");
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
  public void cannotUpdateId()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();
    createLocation(id, "Main Library", instID, campID, libID, "PI/CC/ML/X");
    JsonObject updateRequest = new JsonObject()
      .put("id", UUID.randomUUID().toString())
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
    assertThat(updateResponse, statusCodeIs(HttpURLConnection.HTTP_BAD_REQUEST));
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

	@Test
	public void canCascadeOnDelete()
			throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {
		UUID locId = UUID.randomUUID();
		createLocation(locId, "Main Library", instID, campID, libID, "PI/CC/ML/X");

		UUID servicePointOneId = UUID.randomUUID();
		UUID servicePointTwoId = UUID.randomUUID();

		CompletableFuture<Response> createServiceOnePoint = new CompletableFuture<>();
		CompletableFuture<Response> createServiceTwoPoint = new CompletableFuture<>();

		List<String> locationIds = new ArrayList<String>();
		locationIds.add(locId.toString());

		JsonObject requestOne = new JsonObject();
		requestOne.put("name", "Test Servicepoint One").put("code", "TSP1")
				.put("discoveryDisplayName", "Test Servicepoint One")
				.put("id", servicePointOneId.toString()).put("locationIds", new JsonArray(locationIds));

		JsonObject requestTwo = new JsonObject();
		requestTwo.put("name", "Test Servicepoint Two").put("code", "TSP2")
				.put("discoveryDisplayName", "Test Servicepoint Two").put("id", servicePointTwoId.toString())
				.put("locationIds", new JsonArray(locationIds));

		send(servicePointsUrl(""), HttpMethod.POST, requestOne.toString(), SUPPORTED_CONTENT_TYPE_JSON_DEF,
				ResponseHandler.json(createServiceOnePoint));
		createServiceOnePoint.get(5, TimeUnit.SECONDS);

		send(servicePointsUrl(""), HttpMethod.POST, requestTwo.toString(), SUPPORTED_CONTENT_TYPE_JSON_DEF,
				ResponseHandler.json(createServiceTwoPoint));
		createServiceTwoPoint.get(5, TimeUnit.SECONDS);

		CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();
		send(locationsStorageUrl("/" + locId.toString()), HttpMethod.DELETE, null, SUPPORTED_CONTENT_TYPE_JSON_DEF,
				ResponseHandler.any(deleteCompleted));
		deleteCompleted.get(5, TimeUnit.SECONDS);

		CompletableFuture<Response> getOneCompleted = new CompletableFuture<>();
		send(servicePointsUrl("/" + servicePointOneId.toString()), HttpMethod.GET, null, SUPPORTED_CONTENT_TYPE_JSON_DEF,
				ResponseHandler.json(getOneCompleted));
		Response servicePointOneResponse = getOneCompleted.get(5, TimeUnit.SECONDS);

		CompletableFuture<Response> getTwoCompleted = new CompletableFuture<>();
		send(servicePointsUrl("/" + servicePointTwoId.toString()), HttpMethod.GET, null, SUPPORTED_CONTENT_TYPE_JSON_DEF,
				ResponseHandler.json(getOneCompleted));
		Response servicePointTwoResponse = getTwoCompleted.get(5, TimeUnit.SECONDS);

		assertThat(servicePointOneResponse.getJson().getJsonArray("locationIds").contains(servicePointOneId.toString()),
				is(false));
		assertThat(servicePointTwoResponse.getJson().getJsonArray("locationIds").contains(servicePointOneId.toString()),
				is(false));
	}

  @Test
  public void cannotDeleteALocationAssociatedWithAnItem()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();
    createLocation(id, "Main Library", instID, campID, libID, "PI/CC/ML/X");
    JsonObject item = createItemRequest(id.toString());
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

  public static Response createLocation(UUID id, String name,
    UUID inst, UUID camp, UUID lib, String code)
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
   * @return
   */
  public static UUID createLocation(UUID id, String name, String code) {
    try {
      createLocUnits(false);
      if (id == null) {
        id = UUID.randomUUID();
      }
      createLocation(id, name, instID, campID, libID, code);
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

