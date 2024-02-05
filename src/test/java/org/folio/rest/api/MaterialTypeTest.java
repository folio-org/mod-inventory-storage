package org.folio.rest.api;

import static org.folio.rest.support.HttpResponseMatchers.statusCodeIs;
import static org.folio.rest.support.JsonObjectMatchers.hasSoleMessageContaining;
import static org.folio.rest.support.http.InterfaceUrls.itemsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.loanTypesStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.materialTypesStorageUrl;
import static org.folio.utility.ModuleUtility.getVertx;
import static org.folio.utility.RestUtility.send;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.SneakyThrows;
import org.folio.rest.support.AdditionalHttpStatusCodes;
import org.folio.rest.support.HttpClient;
import org.folio.rest.support.JsonErrorResponse;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.client.LoanTypesClient;
import org.junit.Before;
import org.junit.Test;

public class MaterialTypeTest extends TestBaseWithInventoryUtil {

  private static final String SUPPORTED_CONTENT_TYPE_JSON_DEF = "application/json";

  @SneakyThrows
  @Before
  public void beforeEach() {
    clearData();

    canCirculateLoanTypeID = new LoanTypesClient(
      new HttpClient(getVertx()),
      loanTypesStorageUrl("")).create("Can Circulate");

    setupLocations();
    removeAllEvents();
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

    JsonErrorResponse response = createMaterialType.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
    assertThat(response.getErrors(), hasSoleMessageContaining("Unrecognized field"));
  }

  @Test
  public void canGetMaterialTypeById()
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
  public void canUpdateMaterialType()
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

    send(materialTypesStorageUrl("/" + id).toString(), HttpMethod.PUT,
      updateRequest.toString(), SUPPORTED_CONTENT_TYPE_JSON_DEF,
      ResponseHandler.any(updated));

    Response updateResponse = updated.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(updateResponse, statusCodeIs(HttpURLConnection.HTTP_NO_CONTENT));

    assertThat(updateResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    Response getResponse = getById(id);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject item = getResponse.getJson();

    assertThat(item.getString("id"), is(id.toString()));
    assertThat(item.getString("name"), is("Book"));
  }

  @Test
  public void cannotUpdateMaterialTypeThatDoesNotExist()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    JsonObject updateRequest = new JsonObject()
      .put("id", id.toString())
      .put("name", "Book");

    CompletableFuture<Response> updated = new CompletableFuture<>();

    send(materialTypesStorageUrl("/" + id).toString(), HttpMethod.PUT,
      updateRequest.toString(), SUPPORTED_CONTENT_TYPE_JSON_DEF,
      ResponseHandler.any(updated));

    Response updateResponse = updated.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(updateResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  @Test
  public void cannotGetMaterialTypeThatDoesNotExist()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    send(materialTypesStorageUrl("/" + UUID.randomUUID()).toString(), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(getCompleted));

    Response getResponse = getCompleted.get(TIMEOUT, TimeUnit.SECONDS);

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

    Response getResponse = getCompleted.get(TIMEOUT, TimeUnit.SECONDS);

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

    send(materialTypesStorageUrl("/" + id).toString(), HttpMethod.DELETE, null,
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(deleteCompleted));

    Response deleteResponse = deleteCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
  }

  @Test
  public void cannotDeleteMaterialTypeAssociatedToAnItem()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID materialTypeId = UUID.randomUUID();

    createMaterialType(materialTypeId, "Book");

    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

    JsonObject item = createItemRequest(holdingsRecordId.toString(), materialTypeId.toString());

    CompletableFuture<Response> createItemCompleted = new CompletableFuture<>();

    send(itemsStorageUrl("").toString(), HttpMethod.POST, item.toString(),
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(createItemCompleted));

    Response createItemResponse = createItemCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(createItemResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();

    send(materialTypesStorageUrl("/" + materialTypeId).toString(),
      HttpMethod.DELETE, null, SUPPORTED_CONTENT_TYPE_JSON_DEF,
      ResponseHandler.text(deleteCompleted));

    Response deleteResponse = deleteCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
  }

  @Test
  public void cannotDeleteMaterialTypeThatCannotBeFound()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();

    send(materialTypesStorageUrl("/" + UUID.randomUUID()).toString(),
      HttpMethod.DELETE, null, SUPPORTED_CONTENT_TYPE_JSON_DEF,
      ResponseHandler.text(deleteCompleted));

    Response deleteResponse = deleteCompleted.get(TIMEOUT, TimeUnit.SECONDS);

    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  private JsonObject createItemRequest(String holdingsRecordId, String materialTypeId) {

    JsonObject item = new JsonObject();

    item.put("barcode", "12345");
    item.put("status", new JsonObject().put("name", "Available"));
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
    String createMtUrl = materialTypesStorageUrl("").toString();

    send(createMtUrl, HttpMethod.POST, new JsonObject().put("name", name).toString(),
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(createMaterialType));

    return createMaterialType.get(TIMEOUT, TimeUnit.SECONDS);
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

    return createMaterialType.get(TIMEOUT, TimeUnit.SECONDS);
  }

  private Response getById(UUID id)
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    send(materialTypesStorageUrl("/" + id.toString()).toString(), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(getCompleted));

    return getCompleted.get(TIMEOUT, TimeUnit.SECONDS);
  }
}
