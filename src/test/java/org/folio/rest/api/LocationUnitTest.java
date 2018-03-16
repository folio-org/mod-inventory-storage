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

// Missing tests:
// - Add/update a campus that points to a non-existing inst
// - delete an inst that is in use by a campus
// - Add/update a library that points to a non-existing campus
// - delete a campus that is in use by a library
// (The code for these checks is missing!)
//
public class LocationUnitTest {

  private static final String SUPPORTED_CONTENT_TYPE_JSON_DEF = "application/json";
  private String canCirculateLoanTypeID;
  private String journalMaterialTypeID;
  private final Logger logger = LoggerFactory.getLogger(LocationUnitTest.class);


  @Before
  public void beforeEach()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    StorageTestSuite.deleteAll(itemsStorageUrl(""));
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
  }

  ////////////////// General helpers
  /*
  private JsonObject createItemRequest(String temporaryLocationId) {

    JsonObject item = new JsonObject();

    item.put("holdingsRecordId", UUID.randomUUID().toString());
    item.put("barcode", "12345");
    item.put("permanentLoanTypeId", canCirculateLoanTypeID);
    item.put("materialTypeId", journalMaterialTypeID);
    item.put("temporaryLocationId", temporaryLocationId);

    return item;
  }
   */
  private static void send(URL url, HttpMethod method, String content,
    String contentType, Handler<HttpClientResponse> handler) {

    HttpClient client = StorageTestSuite.getVertx().createHttpClient();
    HttpClientRequest request;

    if (content == null) {
      content = "";
    }
    Buffer buffer = Buffer.buffer(content);

    if (method == HttpMethod.POST) {
      request = client.postAbs(url.toString());
    } else if (method == HttpMethod.DELETE) {
      request = client.deleteAbs(url.toString());
    } else if (method == HttpMethod.GET) {
      request = client.getAbs(url.toString());
    } else {
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

  ///////////////////////////////////////////////////////  Inst test helpers
  // May also be used from other tests
  public static Response createInst(String name, String code)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<Response> createLocationUnit = new CompletableFuture<>();

    JsonObject request = new JsonObject()
      .put("name", name)
      .put("shortcode", code);

    send(locInstitutionStorageUrl(""), HttpMethod.POST, request.toString(),
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(createLocationUnit));

    return createLocationUnit.get(5, TimeUnit.SECONDS);
  }

  public static Response createInst(UUID id, String name, String code)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<Response> createLocationUnit = new CompletableFuture<>();

    JsonObject request = new JsonObject()
      .put("id", id.toString())
      .put("name", name)
      .put("shortcode", code);

    send(locInstitutionStorageUrl(""), HttpMethod.POST, request.toString(),
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(createLocationUnit));

    return createLocationUnit.get(5, TimeUnit.SECONDS);
  }

  private Response getInstById(UUID id)
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    send(locInstitutionStorageUrl("/" + id.toString()), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(getCompleted));

    return getCompleted.get(5, TimeUnit.SECONDS);
  }

/////////////////////// Inst tests
  @Test
  public void canCreateAnInst()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    Response response = createInst("Institute of MetaPhysics", "MPI");

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
    assertThat(response.getJson().getString("id"), notNullValue());
    assertThat(response.getJson().getString("name"), is("Institute of MetaPhysics"));
    assertThat(response.getJson().getString("shortcode"), is("MPI"));
  }

  @Test
  public void cannotCreateInstWithSameName()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    createInst("Institute of MetaPhysics", "MPI");
    Response response = createInst("Institute of MetaPhysics", "MPI");
    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void cannotCreateInstSameId()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();
    createInst(id, "Institute of MetaPhysics", "MPI");
    Response response = createInst(id, "The Other Institute", "MPI");
    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void canGetAnInstById()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();
    createInst(id, "Institute of MetaPhysics", "MPI");
    Response getResponse = getInstById(id);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    JsonObject item = getResponse.getJson();
    assertThat(item.getString("id"), is(id.toString()));
    assertThat(item.getString("name"), is("Institute of MetaPhysics"));
  }

  @Test
  public void canUpdateAnInst()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();
    createInst(id, "Institute of MetaPhysics", "MPI");

    JsonObject updateRequest = new JsonObject()
      .put("id", id.toString())
      .put("name", "The Other Institute");

    CompletableFuture<Response> updated = new CompletableFuture<>();

    send(locInstitutionStorageUrl("/" + id.toString()), HttpMethod.PUT,
      updateRequest.toString(), SUPPORTED_CONTENT_TYPE_JSON_DEF,
      ResponseHandler.any(updated));

    Response updateResponse = updated.get(5, TimeUnit.SECONDS);

    assertThat(updateResponse, statusCodeIs(HttpURLConnection.HTTP_NO_CONTENT));
    assertThat(updateResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
    Response getResponse = getInstById(id);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    JsonObject item = getResponse.getJson();
    assertThat(item.getString("id"), is(id.toString()));
    assertThat(item.getString("name"), is("The Other Institute"));
  }

  @Test
  public void canDeleteAnInst()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    createInst(id, "Institute of MetaPhysics", "MPI");

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();

    send(locInstitutionStorageUrl("/" + id.toString()), HttpMethod.DELETE, null,
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(deleteCompleted));

    Response deleteResponse = deleteCompleted.get(5, TimeUnit.SECONDS);
    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
  }

  /*
  TODO - A test that checks thatn institution can not be deleted if used by
  a location.
  @Test
  public void cannotDeleteAnInstAssociatedWithAnItem()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID locationId = UUID.randomUUID();

    createInst(locationId, "Institute of MetaPhysics", "MPI");

    JsonObject item = createItemRequest(locationId.toString());
    CompletableFuture<Response> createItemCompleted = new CompletableFuture<>();

    send(itemsStorageUrl(""), HttpMethod.POST, item.toString(),
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(createItemCompleted));

    Response createItemResponse = createItemCompleted.get(5, TimeUnit.SECONDS);
    assertThat(createItemResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();

    send(locInstitutionStorageUrl(locationId.toString()),
      HttpMethod.DELETE, null, SUPPORTED_CONTENT_TYPE_JSON_DEF,
      ResponseHandler.any(deleteCompleted));

    Response deleteResponse = deleteCompleted.get(5, TimeUnit.SECONDS);
    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
  }
*/
////////////////////////////////////// Campus test helpers
  public static Response createCamp(UUID id, String name, String code, UUID instId)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<Response> createLocationUnit = new CompletableFuture<>();

    JsonObject request = new JsonObject()
      .put("name", name)
      .put("shortcode", code)
      .put("institutionId", instId.toString());
    if (id != null) {
      request.put("id", id.toString());
    }

    send(locCampusStorageUrl(""), HttpMethod.POST, request.toString(),
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(createLocationUnit));

    return createLocationUnit.get(5, TimeUnit.SECONDS);
  }

  private Response getCampById(UUID id)
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    send(locCampusStorageUrl("/" + id.toString()), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(getCompleted));

    return getCompleted.get(5, TimeUnit.SECONDS);
  }

////////////// Campus tests
  @Test
  public void canCreateACamp()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID instId = UUID.randomUUID();
    createInst(instId, "Institute of MetaPhysics", "MPI");

    Response response = createCamp(null, "Riverside Campus", "RS", instId);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
    assertThat(response.getJson().getString("id"), notNullValue());
    assertThat(response.getJson().getString("name"), is("Riverside Campus"));
    assertThat(response.getJson().getString("shortcode"), is("RS"));
  }

  @Test
  public void cannotCreateCampWithSameName()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID instId = UUID.randomUUID();
    createInst(instId, "Institute of MetaPhysics", "MPI");

    createCamp(null, "Riverside Campus", "RS", instId);
    Response response = createCamp(null, "Riverside Campus", "RS", instId);
    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void cannotCreateCampSameId()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID instId = UUID.randomUUID();
    createInst(instId, "Institute of MetaPhysics", "MPI");

    UUID id = UUID.randomUUID();
    createCamp(id, "Riverside Campus", "RS", instId);
    Response response = createCamp(id, "Campus on the other Side of the River", "OS", instId);
    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void canGetACampById()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID instId = UUID.randomUUID();
    createInst(instId, "Institute of MetaPhysics", "MPI");

    UUID id = UUID.randomUUID();
    createCamp(id, "Riverside Campus", "RS", instId);
    Response getResponse = getCampById(id);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    JsonObject item = getResponse.getJson();
    assertThat(item.getString("id"), is(id.toString()));
    assertThat(item.getString("name"), is("Riverside Campus"));
  }

  @Test
  public void canUpdateACamp()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID instId = UUID.randomUUID();
    createInst(instId, "Institute of MetaPhysics", "MPI");

    UUID id = UUID.randomUUID();
    createCamp(id, "Riverside Campus", "MPI", instId);

    JsonObject updateRequest = new JsonObject()
      .put("id", id.toString())
      .put("name", "The Other Campus")
      .put("institutionId", instId.toString());

    CompletableFuture<Response> updated = new CompletableFuture<>();

    send(locCampusStorageUrl("/" + id.toString()), HttpMethod.PUT,
      updateRequest.toString(), SUPPORTED_CONTENT_TYPE_JSON_DEF,
      ResponseHandler.any(updated));
    Response updateResponse = updated.get(5, TimeUnit.SECONDS);
    assertThat(updateResponse, statusCodeIs(HttpURLConnection.HTTP_NO_CONTENT));
    assertThat(updateResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    Response getResponse = getCampById(id);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    JsonObject item = getResponse.getJson();
    assertThat(item.getString("id"), is(id.toString()));
    assertThat(item.getString("name"), is("The Other Campus"));
  }

  @Test
  public void canDeleteACamp()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID instId = UUID.randomUUID();
    createInst(instId, "Institute of MetaPhysics", "MPI");

    UUID id = UUID.randomUUID();

    createCamp(id, "Riverside Campus", "RS", instId);

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();

    send(locCampusStorageUrl("/" + id.toString()), HttpMethod.DELETE, null,
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(deleteCompleted));

    Response deleteResponse = deleteCompleted.get(5, TimeUnit.SECONDS);
    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
  }
////////////////////////////////////// Library test helpers

  public static Response createLib(UUID id, String name, String code, UUID campId)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<Response> createLocationUnit = new CompletableFuture<>();

    JsonObject request = new JsonObject()
      .put("name", name)
      .put("shortcode", code)
      .put("campusId", campId.toString());
    if (id != null) {
      request.put("id", id.toString());
    }

    send(locLibraryStorageUrl(""), HttpMethod.POST, request.toString(),
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(createLocationUnit));

    return createLocationUnit.get(5, TimeUnit.SECONDS);
  }

  private Response getLibById(UUID id)
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    send(locLibraryStorageUrl("/" + id.toString()), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(getCompleted));

    return getCompleted.get(5, TimeUnit.SECONDS);
  }

////////////// Library tests
  @Test
  public void canCreateALib()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID instId = UUID.randomUUID();
    createInst(instId, "Institute of MetaPhysics", "MPI");
    UUID campId = UUID.randomUUID();
    createCamp(campId, "Riverside Campus", "RS", instId);

    Response response = createLib(null, "Main Library", "RS", campId);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
    assertThat(response.getJson().getString("id"), notNullValue());
    assertThat(response.getJson().getString("name"), is("Main Library"));
    assertThat(response.getJson().getString("shortcode"), is("RS"));
  }

  @Test
  public void cannotCreateLibWithSameName()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID instId = UUID.randomUUID();
    createInst(instId, "Institute of MetaPhysics", "MPI");
    UUID campId = UUID.randomUUID();
    createCamp(campId, "Riverside Campus", "RS", instId);

    createLib(null, "Main Library", "RS", campId);
    Response response = createLib(null, "Main Library", "RS", campId);
    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void cannotCreateLibSameId()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID instId = UUID.randomUUID();
    createInst(instId, "Institute of MetaPhysics", "MPI");
    UUID campId = UUID.randomUUID();
    createCamp(campId, "Riverside Campus", "RS", instId);

    UUID id = UUID.randomUUID();
    createLib(id, "Main Library", "RS", campId);
    Response response = createLib(id, "Library on the other Side of the River", "OS", campId);
    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void canGetALibById()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID instId = UUID.randomUUID();
    createInst(instId, "Institute of MetaPhysics", "MPI");
    UUID campId = UUID.randomUUID();
    createCamp(campId, "Riverside Campus", "RS", instId);

    UUID id = UUID.randomUUID();
    createLib(id, "Main Library", "RS", campId);
    Response getResponse = getLibById(id);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    JsonObject item = getResponse.getJson();
    assertThat(item.getString("id"), is(id.toString()));
    assertThat(item.getString("name"), is("Main Library"));
    assertThat(item.getString("campusId"), is(campId.toString()));
  }

  @Test
  public void canUpdateALib()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID instId = UUID.randomUUID();
    createInst(instId, "Institute of MetaPhysics", "MPI");
    UUID campId = UUID.randomUUID();
    createCamp(campId, "Riverside Campus", "RS", instId);

    UUID id = UUID.randomUUID();
    createLib(id, "Main Library", "MPI", campId);

    JsonObject updateRequest = new JsonObject()
      .put("id", id.toString())
      .put("name", "The Other Library")
      .put("campusId", campId.toString());

    CompletableFuture<Response> updated = new CompletableFuture<>();

    send(locLibraryStorageUrl("/" + id.toString()), HttpMethod.PUT,
      updateRequest.toString(), SUPPORTED_CONTENT_TYPE_JSON_DEF,
      ResponseHandler.any(updated));
    Response updateResponse = updated.get(5, TimeUnit.SECONDS);
    assertThat(updateResponse, statusCodeIs(HttpURLConnection.HTTP_NO_CONTENT));
    assertThat(updateResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    Response getResponse = getLibById(id);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    JsonObject item = getResponse.getJson();
    assertThat(item.getString("id"), is(id.toString()));
    assertThat(item.getString("name"), is("The Other Library"));
  }

  @Test
  public void canDeleteALib()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID instId = UUID.randomUUID();
    createInst(instId, "Institute of MetaPhysics", "MPI");
    UUID campId = UUID.randomUUID();
    createCamp(campId, "Riverside Campus", "RS", instId);

    UUID id = UUID.randomUUID();

    createLib(id, "Main Library", "RS", campId);

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();

    send(locLibraryStorageUrl("/" + id.toString()), HttpMethod.DELETE, null,
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(deleteCompleted));

    Response deleteResponse = deleteCompleted.get(5, TimeUnit.SECONDS);
    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
  }
}
