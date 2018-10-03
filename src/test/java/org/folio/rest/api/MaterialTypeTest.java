package org.folio.rest.api;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import org.folio.rest.support.*;
import org.folio.rest.support.client.LoanTypesClient;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.folio.rest.support.HttpResponseMatchers.statusCodeIs;
import static org.folio.rest.support.JsonObjectMatchers.hasSoleMessgeContaining;
import static org.folio.rest.support.http.InterfaceUrls.*;
import static org.folio.rest.support.http.InterfaceUrls.instancesStorageUrl;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import org.junit.BeforeClass;

public class MaterialTypeTest extends TestBaseWithInventoryUtil {

  private static final String SUPPORTED_CONTENT_TYPE_JSON_DEF = "application/json";
  private static UUID mainLibraryLocationId;

  private String canCirculateLoanTypeID;
  
  @BeforeClass
  public static void beforeAny()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {
    StorageTestSuite.deleteAll(itemsStorageUrl(""));
    StorageTestSuite.deleteAll(holdingsStorageUrl(""));
    StorageTestSuite.deleteAll(instancesStorageUrl(""));

    StorageTestSuite.deleteAll(locationsStorageUrl(""));
    StorageTestSuite.deleteAll(locLibraryStorageUrl(""));
    StorageTestSuite.deleteAll(locCampusStorageUrl(""));
    StorageTestSuite.deleteAll(locInstitutionStorageUrl(""));

    LocationsTest.createLocUnits(true);
    mainLibraryLocationId = LocationsTest.createLocation(null, "Main Library (Loan)", "Lo/M");
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

    StorageTestSuite.deleteAll(materialTypesStorageUrl(""));
    StorageTestSuite.deleteAll(loanTypesStorageUrl(""));

    canCirculateLoanTypeID = new LoanTypesClient(
      new org.folio.rest.support.HttpClient(StorageTestSuite.getVertx()),
      loanTypesStorageUrl("")).create("Can Circulate");
  }

  @Test
  public void canCreateMaterialType()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    Response response = createMaterialType("Journal");

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    //fix to read from location header
    assertThat(response.getJson().getString("id"), notNullValue());
    assertThat(response.getJson().getString("name"), is("Journal"));
  }

  @Test
  public void cannotCreateMaterialTypeWithSameName()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    createMaterialType("Journal");

    Response response = createMaterialType("Journal");

    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void cannotCreateMaterialTypeWithSameId()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    createMaterialType(id, "Journal");

    Response response = createMaterialType(id, "Book");

    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void cannotProvideAdditionalPropertiesInMaterialType()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    CompletableFuture<JsonErrorResponse> createMaterialType = new CompletableFuture<>();

    JsonObject requestWithAdditionalProperty = new JsonObject()
      .put("name", "Journal")
      .put("somethingAdditional", "foo");

    send(materialTypesStorageUrl("").toString(), HttpMethod.POST,
      requestWithAdditionalProperty.toString(), SUPPORTED_CONTENT_TYPE_JSON_DEF,
      ResponseHandler.jsonErrors(createMaterialType));

    JsonErrorResponse response = createMaterialType.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
    assertThat(response.getErrors(), hasSoleMessgeContaining("Unrecognized field"));
  }

  @Test
  public void canGetAMaterialTypeById()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    createMaterialType(id, "Journal");

    Response getResponse = getById(id);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject item = getResponse.getJson();

    assertThat(item.getString("id"), is(id.toString()));
    assertThat(item.getString("name"), is("Journal"));
  }

  @Test
  public void canUpdateAMaterialType()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    createMaterialType(id, "Journal");

    JsonObject updateRequest = new JsonObject()
      .put("id", id.toString())
      .put("name", "Book");

    CompletableFuture<Response> updated = new CompletableFuture<>();

    send(materialTypesStorageUrl("/" + id.toString()).toString(), HttpMethod.PUT,
      updateRequest.toString(), SUPPORTED_CONTENT_TYPE_JSON_DEF,
      ResponseHandler.any(updated));

    Response updateResponse = updated.get(5, TimeUnit.SECONDS);

    assertThat(updateResponse, statusCodeIs(HttpURLConnection.HTTP_NO_CONTENT));

    assertThat(updateResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    Response getResponse = getById(id);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject item = getResponse.getJson();

    assertThat(item.getString("id"), is(id.toString()));
    assertThat(item.getString("name"), is("Book"));
  }

  @Test
  public void cannotUpdateAMaterialTypeThatDoesNotExist()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    JsonObject updateRequest = new JsonObject()
      .put("id", id.toString())
      .put("name", "Book");

    CompletableFuture<Response> updated = new CompletableFuture<>();

    send(materialTypesStorageUrl("/" + id.toString()).toString(), HttpMethod.PUT,
      updateRequest.toString(), SUPPORTED_CONTENT_TYPE_JSON_DEF,
      ResponseHandler.any(updated));

    Response updateResponse = updated.get(5, TimeUnit.SECONDS);

    assertThat(updateResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  @Test
  public void cannotGetAMaterialTypeThatDoesNotExist()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    send(materialTypesStorageUrl("/" + UUID.randomUUID().toString()).toString(), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(getCompleted));

    Response getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  @Test
  public void canGetAllMaterialTypes()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    createMaterialType(UUID.randomUUID(), "Journal");
    createMaterialType(UUID.randomUUID(), "Book");

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    send(materialTypesStorageUrl("").toString(), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(getCompleted));

    Response getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(getResponse.getJson().getInteger("totalRecords"), is(2));
  }

  @Test
  public void canDeleteAnUnusedMaterialType()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    createMaterialType(id, "Journal");

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();

    send(materialTypesStorageUrl("/" + id.toString()).toString(), HttpMethod.DELETE, null,
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(deleteCompleted));

    Response deleteResponse = deleteCompleted.get(5, TimeUnit.SECONDS);

    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
  }

  @Test
  public void cannotDeleteAMaterialTypeAssociatedToAnItem()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID materialTypeId = UUID.randomUUID();

    createMaterialType(materialTypeId, "Book");

    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    JsonObject item = createItemRequest(holdingsRecordId.toString(), materialTypeId.toString());

    CompletableFuture<Response> createItemCompleted = new CompletableFuture<>();

    send(itemsStorageUrl("").toString(), HttpMethod.POST, item.toString(),
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(createItemCompleted));

    Response createItemResponse = createItemCompleted.get(5, TimeUnit.SECONDS);

    assertThat(createItemResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();

    send(materialTypesStorageUrl("/" + materialTypeId.toString()).toString(),
      HttpMethod.DELETE, null, SUPPORTED_CONTENT_TYPE_JSON_DEF,
      ResponseHandler.text(deleteCompleted));

    Response deleteResponse = deleteCompleted.get(5, TimeUnit.SECONDS);

    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
  }

  @Test
  public void cannotDeleteAMaterialTypeThatCannotBeFound()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();

    send(materialTypesStorageUrl("/" + UUID.randomUUID().toString()).toString(),
      HttpMethod.DELETE, null, SUPPORTED_CONTENT_TYPE_JSON_DEF,
      ResponseHandler.text(deleteCompleted));

    Response deleteResponse = deleteCompleted.get(5, TimeUnit.SECONDS);

    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  private void send(String url, HttpMethod method, String content,
                    String contentType, Handler<HttpClientResponse> handler) {

    HttpClient client = StorageTestSuite.getVertx().createHttpClient();
    HttpClientRequest request;

    if(content == null){
      content = "";
    }
    Buffer buffer = Buffer.buffer(content);

    if (method == HttpMethod.POST) {
      request = client.postAbs(url);
    }
    else if (method == HttpMethod.DELETE) {
      request = client.deleteAbs(url);
    }
    else if (method == HttpMethod.GET) {
      request = client.getAbs(url);
    }
    else {
      request = client.putAbs(url);
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

  private JsonObject createItemRequest(String holdingsRecordId, String materialTypeId) {

    JsonObject item = new JsonObject();

    item.put("barcode", "12345");
    item.put("holdingsRecordId", holdingsRecordId);
    item.put("materialTypeId", materialTypeId);
    item.put("permanentLoanTypeId", canCirculateLoanTypeID);

    return item;
  }

  private Response createMaterialType(String name)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<Response> createMaterialType = new CompletableFuture<>();
    String createMTURL = materialTypesStorageUrl("").toString();

    send(createMTURL, HttpMethod.POST, new JsonObject().put("name", name).toString(),
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(createMaterialType));

    return createMaterialType.get(5, TimeUnit.SECONDS);
  }

  private Response createMaterialType(UUID id, String name)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<Response> createMaterialType = new CompletableFuture<>();

    JsonObject request = new JsonObject()
      .put("id", id.toString())
      .put("name", name);

    send(materialTypesStorageUrl("").toString(), HttpMethod.POST, request.toString(),
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(createMaterialType));

    return createMaterialType.get(5, TimeUnit.SECONDS);
  }

  private Response getById(UUID id)
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    send(materialTypesStorageUrl("/" + id.toString()).toString(), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(getCompleted));

    return getCompleted.get(5, TimeUnit.SECONDS);
  }
}
