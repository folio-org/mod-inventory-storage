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
import org.folio.rest.support.AdditionalHttpStatusCodes;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
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
  private static final Logger logger = LoggerFactory.getLogger(LocationUnitTest.class);

  @Before
  public void beforeEach()
    throws MalformedURLException {

    StorageTestSuite.deleteAll(itemsStorageUrl(""));
    StorageTestSuite.deleteAll(locationsStorageUrl(""));
    StorageTestSuite.deleteAll(locLibraryStorageUrl(""));
    StorageTestSuite.deleteAll(locCampusStorageUrl(""));
    StorageTestSuite.deleteAll(locInstitutionStorageUrl(""));
    StorageTestSuite.deleteAll(loanTypesStorageUrl(""));
    StorageTestSuite.deleteAll(materialTypesStorageUrl(""));
  }

  ////////////////// General helpers
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

    request.putHeader("X-Okapi-User-Id", "test_user");
    request.putHeader("X-Okapi-Tenant", "test_tenant");
    request.putHeader("Accept", "application/json,text/plain");
    request.putHeader("Content-type", contentType);
    request.end(buffer);
  }

  ///////////////////////////////////////////////////////  Inst test helpers
  // May also be used from other tests
  public static Response createInst(UUID id, String name, String code)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<Response> createLocationUnit = new CompletableFuture<>();

    JsonObject request = new JsonObject()
      .put("name", name)
      .put("code", code);
    if (id != null) {
      request.put("id", id.toString());
    }

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

    Response response = createInst(null, "Institute of MetaPhysics", "MPI");

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
    assertThat(response.getJson().getString("id"), notNullValue());
    assertThat(response.getJson().getString("name"), is("Institute of MetaPhysics"));
    assertThat(response.getJson().getString("code"), is("MPI"));
  }

  @Test
  public void cannotCreateInstWithSameName()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    createInst(null, "Institute of MetaPhysics", "MPI");
    Response response = createInst(null, "Institute of MetaPhysics", "MPI");
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
    assertThat(item.getJsonObject("metadata").getString("createdByUserId"),
      is("test_user"));  // The userId header triggers creation of metadata
  }

  @Test
  public void cannotGetAnInstByWrongId()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {
    createInst(null, "Institute of MetaPhysics", "MPI");
    UUID id = UUID.randomUUID();
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    send(locInstitutionStorageUrl("/" + id.toString()), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(getCompleted));
    Response getResponse;
    getResponse = getCompleted.get(5, TimeUnit.SECONDS);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  @Test
  public void canListInsts()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    createInst(null, "Institute of MetaPhysics", "MPI");
    createInst(null, "The Other Institute", "OI");

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    send(locInstitutionStorageUrl("/"), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(getCompleted));

    Response getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    JsonObject item = getResponse.getJson();
    assertThat(item.getInteger("totalRecords"), is(2));
  }

  @Test
  public void canQueryInsts()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    createInst(null, "Institute of MetaPhysics", "MPI");
    createInst(null, "The Other Institute", "OI");
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    send(locInstitutionStorageUrl("/?query=name=Other"), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(getCompleted));
    Response getResponse = getCompleted.get(5, TimeUnit.SECONDS);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    JsonObject item = getResponse.getJson();
    assertThat(item.getInteger("totalRecords"), is(1));
  }

  @Test
  public void badQueryInsts()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    createInst(null, "Institute of MetaPhysics", "MPI");
    createInst(null, "The Other Institute", "OI");
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    send(locInstitutionStorageUrl("/?query=invalidCQL"), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(getCompleted));
    Response getResponse;
    getResponse = getCompleted.get(5, TimeUnit.SECONDS);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
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
  public void cannotUpdateAnInstId()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();
    createInst(id, "Institute of MetaPhysics", "MPI");
    JsonObject updateRequest = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("name", "The Other Institute");
    CompletableFuture<Response> updated = new CompletableFuture<>();
    send(locInstitutionStorageUrl("/" + id.toString()), HttpMethod.PUT,
      updateRequest.toString(), SUPPORTED_CONTENT_TYPE_JSON_DEF,
      ResponseHandler.any(updated));
    Response updateResponse = updated.get(5, TimeUnit.SECONDS);
    assertThat(updateResponse, statusCodeIs(HttpURLConnection.HTTP_BAD_REQUEST));
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

////////////////////////////////////// Campus test helpers
  public static Response createCamp(UUID id, String name, String code, UUID instId)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<Response> createLocationUnit = new CompletableFuture<>();

    JsonObject request = new JsonObject()
      .put("name", name)
      .put("code", code);
    if (instId != null) { // should not be, except when testing it
      request.put("institutionId", instId.toString());
    }
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
    assertThat(response.getJson().getString("code"), is("RS"));
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
  public void cannotCreateCampWithoutInst()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    Response response = createCamp(null, "Campus on the other Side of the River", "OS", null);
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
    assertThat(item.getJsonObject("metadata").getString("createdByUserId"),
      is("test_user"));  // The userId header triggers creation of metadata
  }

  @Test
  public void cannotGetACampWrongId()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {
    UUID instId = UUID.randomUUID();
    createInst(instId, "Institute of MetaPhysics", "MPI");

    createCamp(null, "Riverside Campus", "RS", instId);

    UUID id = UUID.randomUUID();
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    send(locCampusStorageUrl("/" + id.toString()), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(getCompleted));
    Response getResponse;
    getResponse = getCompleted.get(5, TimeUnit.SECONDS);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  @Test
  public void canListCamps()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID instId = UUID.randomUUID();
    createInst(instId, "Institute of MetaPhysics", "MPI");
    createCamp(null, "Riverside Campus", "RSC", instId);
    createCamp(null, "Other Side Campus", "OSC", instId);
    createCamp(null, "Underwater Location", "OSC", instId);

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    send(locCampusStorageUrl("/?query=name=Campus"), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(getCompleted));

    Response getResponse = getCompleted.get(5, TimeUnit.SECONDS);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    JsonObject item = getResponse.getJson();
    assertThat(item.getInteger("totalRecords"), is(2));
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
  public void cannotUpdateACampId()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID instId = UUID.randomUUID();
    createInst(instId, "Institute of MetaPhysics", "MPI");

    UUID id = UUID.randomUUID();
    createCamp(id, "Riverside Campus", "MPI", instId);
    JsonObject updateRequest = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("name", "The Other Campus")
      .put("institutionId", instId.toString());

    CompletableFuture<Response> updated = new CompletableFuture<>();
    send(locCampusStorageUrl("/" + id.toString()), HttpMethod.PUT,
      updateRequest.toString(), SUPPORTED_CONTENT_TYPE_JSON_DEF,
      ResponseHandler.any(updated));
    Response updateResponse = updated.get(5, TimeUnit.SECONDS);
    assertThat(updateResponse, statusCodeIs(HttpURLConnection.HTTP_BAD_REQUEST));
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

  @Test
  public void cannotDeleteInstUsedByCamp()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID instId = UUID.randomUUID();
    createInst(instId, "Institute of MetaPhysics", "MPI");
    createCamp(null, "Riverside Campus", "RS", instId);
    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();
    send(locInstitutionStorageUrl("/" + instId.toString()), HttpMethod.DELETE, null,
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(deleteCompleted));
    Response deleteResponse = deleteCompleted.get(5, TimeUnit.SECONDS);
    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
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
      .put("code", code)
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
    assertThat(response.getJson().getString("code"), is("RS"));
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
    createLib(id, "Main Library", "ML", campId);
    Response getResponse = getLibById(id);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    JsonObject item = getResponse.getJson();
    assertThat(item.getString("id"), is(id.toString()));
    assertThat(item.getString("name"), is("Main Library"));
    assertThat(item.getString("campusId"), is(campId.toString()));
    assertThat(item.getJsonObject("metadata").getString("createdByUserId"),
      is("test_user"));  // The userId header triggers creation of metadata
  }

  public void cannotGetALibWrongId()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID instId = UUID.randomUUID();
    createInst(instId, "Institute of MetaPhysics", "MPI");
    UUID campId = UUID.randomUUID();
    createCamp(campId, "Riverside Campus", "RS", instId);
    createLib(null, "Main Library", "ML", campId);
    UUID id = UUID.randomUUID();
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    send(locLibraryStorageUrl("/" + id.toString()), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(getCompleted));
    Response getResponse;
    getResponse = getCompleted.get(5, TimeUnit.SECONDS);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  @Test
  public void canListLibs()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID instId = UUID.randomUUID();
    createInst(instId, "Institute of MetaPhysics", "MPI");
    UUID campId = UUID.randomUUID();
    createCamp(campId, "Riverside Campus", "RS", instId);
    createLib(null, "Main Library", "ML", campId);
    createLib(null, "Side Library", "SL", campId);
    createLib(null, "The Book Store", "BS", campId);

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    send(locLibraryStorageUrl("/?query=name=LiBRaRy"), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(getCompleted));

    Response getResponse = getCompleted.get(5, TimeUnit.SECONDS);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    JsonObject item = getResponse.getJson();
    assertThat(item.getInteger("totalRecords"), is(2));
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

  public void cannotUpdateALibId()
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
      .put("id", UUID.randomUUID().toString());

    CompletableFuture<Response> updated = new CompletableFuture<>();
    send(locInstitutionStorageUrl("/" + id.toString()), HttpMethod.PUT,
      updateRequest.toString(), SUPPORTED_CONTENT_TYPE_JSON_DEF,
      ResponseHandler.any(updated));
    Response updateResponse = updated.get(5, TimeUnit.SECONDS);
    assertThat(updateResponse, statusCodeIs(HttpURLConnection.HTTP_BAD_REQUEST));
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

  @Test
  public void cannotDeleteCampUsedByLib()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID instId = UUID.randomUUID();
    createInst(instId, "Institute of MetaPhysics", "MPI");
    UUID campId = UUID.randomUUID();
    createCamp(campId, "Riverside Campus", "RS", instId);
    createLib(null, "Main Library", "RS", campId);
    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();
    send(locCampusStorageUrl("/" + campId.toString()), HttpMethod.DELETE, null,
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(deleteCompleted));
    Response deleteResponse = deleteCompleted.get(5, TimeUnit.SECONDS);
    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
  }

}
