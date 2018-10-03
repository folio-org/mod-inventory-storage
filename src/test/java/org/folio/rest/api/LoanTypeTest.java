package org.folio.rest.api;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import org.folio.rest.support.AdditionalHttpStatusCodes;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.client.MaterialTypesClient;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.net.HttpURLConnection.*;
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
import org.junit.BeforeClass;

public class LoanTypeTest extends TestBaseWithInventoryUtil {

  private static final String       SUPPORTED_CONTENT_TYPE_JSON_DEF = "application/json";

  private static String materialTypeID;

  private static String postRequestCirculate = "{\"name\": \"Can circulate\"}";
  private static String postRequestCourse    = "{\"name\": \"Course reserve\"}";
  private static String putRequest  = "{\"name\": \"Reading room\"}";
  private static UUID mainLibraryLocationId;

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
    StorageTestSuite.deleteAll(materialTypesStorageUrl(""));
    StorageTestSuite.deleteAll(loanTypesStorageUrl(""));

    materialTypeID = new MaterialTypesClient(
      new org.folio.rest.support.HttpClient(StorageTestSuite.getVertx()),
      materialTypesStorageUrl("")).create("Journal");
  }

  @Test
  public void canCreateALoanType()
    throws MalformedURLException {

    JsonObject response = send(loanTypesStorageUrl(""), HttpMethod.POST,
      postRequestCirculate, HTTP_CREATED);

    assertThat(response.getString("id"), notNullValue());
    assertThat(response.getString("name"), is("Can circulate"));
  }

  @Test
  public void cannotCreateALoanTypeWithAdditionalProperties()
    throws MalformedURLException {

    JsonObject requestWithAdditionalProperties = new JsonObject()
      .put("name", "Can Circulate")
      .put("additional", "foo");

    send(loanTypesStorageUrl(""), HttpMethod.POST,
      requestWithAdditionalProperties.toString(), AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY);
  }

  @Test
  public void cannotCreateALoanTypeWithSameName()
    throws MalformedURLException {

    send(loanTypesStorageUrl(""), HttpMethod.POST, postRequestCirculate, HTTP_CREATED);

    send(loanTypesStorageUrl(""), HttpMethod.POST, postRequestCirculate, HTTP_BAD_REQUEST);
  }

  @Test
  public void cannotCreateALoanTypeWithSameId()
    throws MalformedURLException {

    JsonObject createResponse = send(loanTypesStorageUrl(""), HttpMethod.POST,
      postRequestCirculate, HTTP_CREATED);

    String loanTypeID = createResponse.getString("id");

    send(loanTypesStorageUrl(""), HttpMethod.POST,
      createLoanType("over night", loanTypeID), HTTP_BAD_REQUEST);
  }

  @Test
  public void canGetALoanTypeById()
    throws MalformedURLException {

    JsonObject createResponse = send(loanTypesStorageUrl(""), HttpMethod.POST,
      postRequestCirculate, HTTP_CREATED);

    //fix to read from location header
    String loanTypeID = createResponse.getString("id");

    JsonObject getResponse = send(loanTypesStorageUrl("/" + loanTypeID), HttpMethod.GET,
      null, HTTP_OK);

    assertThat(getResponse.getString("id"), is(loanTypeID));
    assertThat(getResponse.getString("name"), is("Can circulate"));
  }

  @Test
  public void cannotGetALoanTypeThatDoesNotExist()
    throws MalformedURLException {

    send(loanTypesStorageUrl("/" + UUID.randomUUID()), HttpMethod.GET, null, HTTP_NOT_FOUND);
  }

  @Test
  public void canGetAllLoanTypes()
    throws MalformedURLException {

    send(loanTypesStorageUrl(""), HttpMethod.POST, postRequestCirculate, HTTP_CREATED);
    send(loanTypesStorageUrl(""), HttpMethod.POST, postRequestCourse, HTTP_CREATED);

    JsonObject response = send(loanTypesStorageUrl(""), HttpMethod.GET, null, HTTP_OK);

    assertThat(response.getInteger("totalRecords"), is(2));
  }

  @Test
  public void canDeleteAnUnusedLoanType()
    throws MalformedURLException {

    JsonObject createResponse = send(loanTypesStorageUrl(""), HttpMethod.POST,
      postRequestCirculate, HTTP_CREATED);

    //fix to read from location header
    String loanTypeID = createResponse.getString("id");

    send(loanTypesStorageUrl("/" + loanTypeID), HttpMethod.DELETE, null, HTTP_NO_CONTENT);
  }

  @Test
  public void cannotDeleteALoanTypePermanentlyAssociatedToAnItem()
    throws MalformedURLException, ExecutionException, InterruptedException, TimeoutException {

    JsonObject createResponse = send(loanTypesStorageUrl(""), HttpMethod.POST,
      postRequestCirculate, HTTP_CREATED);

    String loanTypeID = createResponse.getString("id");

    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    send(itemsStorageUrl(""), HttpMethod.POST, createItem(holdingsRecordId, loanTypeID, null), HTTP_CREATED);

    send(loanTypesStorageUrl("/" + loanTypeID), HttpMethod.DELETE, null, HTTP_BAD_REQUEST);
  }

  @Test
  public void cannotDeleteALoanTypeTemporarilyAssociatedToAnItem()
    throws MalformedURLException, ExecutionException, InterruptedException,  TimeoutException {

    JsonObject circulateCreateResponse = send(loanTypesStorageUrl(""), HttpMethod.POST,
      postRequestCirculate, HTTP_CREATED);

    String circulateLoanTypeId = circulateCreateResponse.getString("id");

    JsonObject reserveCreateResponse = send(loanTypesStorageUrl(""), HttpMethod.POST,
      postRequestCourse, HTTP_CREATED);

    String reserveLoanTypeId = reserveCreateResponse.getString("id");

    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    send(itemsStorageUrl(""), HttpMethod.POST,
      createItem(holdingsRecordId, circulateLoanTypeId, reserveLoanTypeId), HTTP_CREATED);

    send(loanTypesStorageUrl("/" + reserveLoanTypeId), HttpMethod.DELETE, null,
      HTTP_BAD_REQUEST);
  }

  @Test
  public void cannotDeleteLoanTypeThatDoesNotExist()
    throws MalformedURLException {

    send(loanTypesStorageUrl("/" + UUID.randomUUID()), HttpMethod.DELETE, null, HTTP_NOT_FOUND);
  }

  @Test
  public void canUpdateALoanType() throws MalformedURLException {

    JsonObject createResponse = send(loanTypesStorageUrl(""), HttpMethod.POST,
      postRequestCirculate, HTTP_CREATED);

    //fix to read from location header
    String loanTypeID = createResponse.getString("id");

    send(loanTypesStorageUrl("/" + loanTypeID), HttpMethod.PUT, putRequest, HTTP_NO_CONTENT);
  }

  @Test
  public void cannotUpdateLoanTypeThatDoesNotExist()
    throws MalformedURLException {

    String id = UUID.randomUUID().toString();

    send(loanTypesStorageUrl("/" + id), HttpMethod.PUT, putRequest, HTTP_NOT_FOUND);
  }

  @Test
  public void cannotCreateItemWithPermanentLoanTypeThatDoesNotExist()
    throws MalformedURLException, ExecutionException, InterruptedException, TimeoutException {

    String nonexistentLoanId = UUID.randomUUID().toString();

    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    send(itemsStorageUrl(""), HttpMethod.POST, createItem(holdingsRecordId, nonexistentLoanId, null),
      HTTP_BAD_REQUEST);
  }

  @Test
  public void cannotCreateItemWithTemporaryLoanTypeThatDoesNotExist()
    throws MalformedURLException, ExecutionException, InterruptedException, TimeoutException {

    JsonObject circulateCreateResponse = send(loanTypesStorageUrl(""), HttpMethod.POST,
      postRequestCirculate, HTTP_CREATED);

    String circulateLoanTypeId = circulateCreateResponse.getString("id");

    String nonexistentLoanId = UUID.randomUUID().toString();
    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);
    send(itemsStorageUrl(""), HttpMethod.POST,
      createItem(holdingsRecordId, circulateLoanTypeId, nonexistentLoanId), HTTP_BAD_REQUEST);
  }

  @Test
  public void updateItemWithNonexistingPermanentLoanTypeId()
    throws MalformedURLException, ExecutionException, InterruptedException, TimeoutException {

    updateItemWithNonexistingId("permanentLoanTypeId");
  }

  @Test
  public void updateItemWithNonexistingTemporaryLoanTypeId()
    throws MalformedURLException, ExecutionException, InterruptedException, TimeoutException {

    updateItemWithNonexistingId("temporaryLoanTypeId");
  }

  private JsonObject send(URL url, HttpMethod method, String content,
                          int expectedStatusCode) {

    CompletableFuture<Response> future = new CompletableFuture<>();
    Handler<HttpClientResponse> handler = ResponseHandler.any(future);
    send(url, method, content, handler);
    Response response;

    try {
      response = future.get(5, TimeUnit.SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new IllegalStateException(e);
    }

    assertThat(url + " - " + method + " - " + content + ":" + response.getBody(),
        response.getStatusCode(), is(expectedStatusCode));

    try {
      return response.getJson();
    }
    catch (DecodeException e) {
      // No body at all or not in JSON format.
      return null;
    }
  }

  private void send(URL url, HttpMethod method, String content,
      Handler<HttpClientResponse> handler) {
    HttpClient client = StorageTestSuite.getVertx().createHttpClient();
    HttpClientRequest request;
    if (content == null) {
      content = "";
    }
    Buffer buffer = Buffer.buffer(content);

    switch (method) {
    case POST:
      request = client.postAbs(url.toString());
      break;
    case DELETE:
      request = client.deleteAbs(url.toString());
      break;
    case GET:
      request = client.getAbs(url.toString());
      break;
    default:
      request = client.putAbs(url.toString());
    }
    request.exceptionHandler(error -> {
      Assert.fail(error.getLocalizedMessage());
    })
    .handler(handler);
    request.putHeader("Authorization", "test_tenant");
    request.putHeader("x-okapi-tenant", "test_tenant");
    request.putHeader("Accept", "application/json,text/plain");
    request.putHeader("Content-type", SUPPORTED_CONTENT_TYPE_JSON_DEF);
    request.end(buffer);
  }

  /** Create a JSON String of an item; set permanentLoanTypeId and temporaryLoanTypeId
   * if the passed variable is not null */
  private static String createItem(UUID holdingsRecordId, String permanentLoanTypeId, String temporaryLoanTypeId) {
    JsonObject item = new JsonObject();

    item.put("holdingsRecordId", holdingsRecordId.toString());
    item.put("barcode", "12345");
    item.put("materialTypeId", materialTypeID);

    if (permanentLoanTypeId != null) {
      item.put("permanentLoanTypeId", permanentLoanTypeId);
    }

    if (temporaryLoanTypeId != null) {
      item.put("temporaryLoanTypeId", temporaryLoanTypeId);
    }

    return item.encode();
  }

  /** Create a JSON String with name and id element; does not include id if it is null */
  private static String createLoanType(String name, String id) {
    JsonObject item = new JsonObject();

    item.put("name", name);
    if (id != null) {
      item.put("id", id);
    }

    return item.encode();
  }

  /**
   * Changing the field to an non existing UUID must fail.
   * @param field - the field to change
   */
  private void updateItemWithNonexistingId(String field)
    throws MalformedURLException, ExecutionException, InterruptedException, TimeoutException {

    UUID holdingsRecordId = createInstanceAndHolding(mainLibraryLocationId);

    JsonObject response = send(itemsStorageUrl(""), HttpMethod.POST,
      createItem(holdingsRecordId, newLoanType(), newLoanType()), HTTP_CREATED);

    String itemId = response.getString("id");

    String nonExistentLoanId = UUID.randomUUID().toString();

    JsonObject putRequest = response.copy().put(field, nonExistentLoanId);

    send(itemsStorageUrl("/" + itemId), HttpMethod.PUT, putRequest.toString(),
      HTTP_BAD_REQUEST);
  }

  /**
   * Create a new loan type with random name.
   * @return the new loan type's id
   */
  private String newLoanType()
    throws MalformedURLException {

    String randomName = "My name is " + UUID.randomUUID().toString();
    String content = "{\"name\": \"" + randomName + "\"}";
    JsonObject response = send(loanTypesStorageUrl(""), HttpMethod.POST, content, HTTP_CREATED);
    // FIXME: read from location header
    return response.getString("id");
  }
}
