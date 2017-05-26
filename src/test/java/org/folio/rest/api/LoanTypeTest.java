package org.folio.rest.api;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.ResponseHandler;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.net.HttpURLConnection.*;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

public class LoanTypeTest {

  private static final String       SUPPORTED_CONTENT_TYPE_JSON_DEF = "application/json";

  private static final String       ITEM_URL = "/item-storage/items/";
  private static final String       LOAN_TYPE_URL = "/loan-types/";

  private static String postRequestCirculate = "{\"name\": \"Can circulate\"}";
  private static String postRequestCourse    = "{\"name\": \"Course reserve\"}";
  private static String putRequest  = "{\"name\": \"Reading room\"}";

  @Before
  public void beforeEach()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    StorageTestSuite.deleteAll(StorageTestSuite.storageUrl(ITEM_URL));
    StorageTestSuite.deleteAll(StorageTestSuite.storageUrl(LOAN_TYPE_URL));
  }

  @Test
  public void canCreateALoanType() {
    JsonObject response = send(LOAN_TYPE_URL, HttpMethod.POST,
      postRequestCirculate, HTTP_CREATED);

    assertThat(response.getString("id"), notNullValue());
    assertThat(response.getString("name"), is("Can circulate"));
  }

  @Test
  public void cannotCreateALoanTypeWithSameName() {
    send(LOAN_TYPE_URL, HttpMethod.POST, postRequestCirculate, HTTP_CREATED);

    send(LOAN_TYPE_URL, HttpMethod.POST, postRequestCirculate, HTTP_BAD_REQUEST);
  }

  @Test
  public void cannotCreateALoanTypeWithSameId() {
    JsonObject createResponse = send(LOAN_TYPE_URL, HttpMethod.POST,
      postRequestCirculate, HTTP_CREATED);

    String loanTypeID = createResponse.getString("id");

    send(LOAN_TYPE_URL, HttpMethod.POST,
      createLoanType("over night", loanTypeID), HTTP_BAD_REQUEST);
  }

  @Test
  public void canGetALoanTypeById() {
    JsonObject createResponse = send(LOAN_TYPE_URL, HttpMethod.POST,
      postRequestCirculate, HTTP_CREATED);

    //fix to read from location header
    String loanTypeID = createResponse.getString("id");

    JsonObject getResponse = send(LOAN_TYPE_URL + loanTypeID, HttpMethod.GET,
      null, HTTP_OK);

    assertThat(getResponse.getString("id"), is(loanTypeID));
    assertThat(getResponse.getString("name"), is("Can circulate"));
  }

  @Test
  public void cannotGetALoanTypeThatDoesNotExist() {
    send(LOAN_TYPE_URL + UUID.randomUUID(), HttpMethod.GET, null, HTTP_NOT_FOUND);
  }

  @Test
  public void canGetAllLoanTypes() {
    send(LOAN_TYPE_URL, HttpMethod.POST, postRequestCirculate, HTTP_CREATED);
    send(LOAN_TYPE_URL, HttpMethod.POST, postRequestCourse, HTTP_CREATED);

    JsonObject response = send(LOAN_TYPE_URL, HttpMethod.GET, null, HTTP_OK);

    assertThat(response.getInteger("totalRecords"), is(2));
  }

  @Test
  public void canDeleteAnUnusedLoanType() {
    JsonObject createResponse = send(LOAN_TYPE_URL, HttpMethod.POST,
      postRequestCirculate, HTTP_CREATED);

    //fix to read from location header
    String loanTypeID = createResponse.getString("id");

    send(LOAN_TYPE_URL + loanTypeID, HttpMethod.DELETE, null, HTTP_NO_CONTENT);
  }

  @Test
  public void cannotDeleteALoanTypePermanentlyAssociatedToAnItem() {
    JsonObject createResponse = send(LOAN_TYPE_URL, HttpMethod.POST,
      postRequestCirculate, HTTP_CREATED);

    String loanTypeID = createResponse.getString("id");

    send(ITEM_URL, HttpMethod.POST, createItem(loanTypeID, null), HTTP_CREATED);

    send(LOAN_TYPE_URL + loanTypeID, HttpMethod.DELETE, null, HTTP_BAD_REQUEST);
  }

  @Test
  public void cannotDeleteALoanTypeTemporarilyAssociatedToAnItem() {
    JsonObject circulateCreateResponse = send(LOAN_TYPE_URL, HttpMethod.POST,
      postRequestCirculate, HTTP_CREATED);

    String circulateLoanTypeId = circulateCreateResponse.getString("id");

    JsonObject reserveCreateResponse = send(LOAN_TYPE_URL, HttpMethod.POST,
      postRequestCourse, HTTP_CREATED);

    String reserveLoanTypeId = reserveCreateResponse.getString("id");

    send(ITEM_URL, HttpMethod.POST,
      createItem(circulateLoanTypeId, reserveLoanTypeId), HTTP_CREATED);

    send(LOAN_TYPE_URL + reserveLoanTypeId, HttpMethod.DELETE, null,
      HTTP_BAD_REQUEST);
  }

  @Test
  public void cannotDeleteLoanTypeThatDoesNotExist() {
    send(LOAN_TYPE_URL + UUID.randomUUID(), HttpMethod.DELETE, null, HTTP_NOT_FOUND);
  }

  @Test
  public void canUpdateALoanType() {
    JsonObject createResponse = send(LOAN_TYPE_URL, HttpMethod.POST,
      postRequestCirculate, HTTP_CREATED);

    //fix to read from location header
    String loanTypeID = createResponse.getString("id");

    send(LOAN_TYPE_URL+loanTypeID, HttpMethod.PUT, putRequest, HTTP_NO_CONTENT);
  }

  @Test
  public void cannotUpdateLoanTypeThatDoesNotExist() {
    String id = UUID.randomUUID().toString();

    send(LOAN_TYPE_URL+id, HttpMethod.PUT, putRequest, HTTP_NOT_FOUND);
  }

  @Test
  public void cannotCreateItemWithPermanentLoanTypeThatDoesNotExist() {
    String nonexistentLoanId = UUID.randomUUID().toString();

    send(ITEM_URL, HttpMethod.POST, createItem(nonexistentLoanId, null), HTTP_BAD_REQUEST);
  }

  @Test
  public void cannotCreateItemWithTemporaryLoanTypeThatDoesNotExist() {
    String nonexistentLoanId = UUID.randomUUID().toString();

    send(ITEM_URL, HttpMethod.POST, createItem(null, nonexistentLoanId), HTTP_BAD_REQUEST);
  }

  @Test
  public void updateItemWithNonexistingPermanentLoanTypeId() {
    updateItemWithNonexistingId("permanentLoanTypeId");
  }

  @Test
  public void updateItemWithNonexistingTemporaryLoanTypeId() {
    updateItemWithNonexistingId("temporaryLoanTypeId");
  }

  private JsonObject send(String urlPath, HttpMethod method, String content,
      int expectedStatusCode) {
    String url;

    try {
      if (urlPath.endsWith("/")) {
        urlPath = urlPath.substring(0, urlPath.length()-1);
      }
      url = StorageTestSuite.storageUrl(urlPath).toString();
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException(e);
    }

    CompletableFuture<JsonResponse> future = new CompletableFuture<>();
    Handler<HttpClientResponse> handler = ResponseHandler.json(future);
    send(url, method, content, handler);
    JsonResponse response;

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

  private void send(String url, HttpMethod method, String content,
      Handler<HttpClientResponse> handler) {
    HttpClient client = StorageTestSuite.getVertx().createHttpClient();
    HttpClientRequest request;
    if (content == null) {
      content = "";
    }
    Buffer buffer = Buffer.buffer(content);

    switch (method) {
    case POST:
      request = client.postAbs(url);
      break;
    case DELETE:
      request = client.deleteAbs(url);
      break;
    case GET:
      request = client.getAbs(url);
      break;
    default:
      request = client.putAbs(url);
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
  private static String createItem(String permanentLoanTypeId, String temporaryLoanTypeId) {
    JsonObject item = new JsonObject();

    item.put("instanceId", "" + UUID.randomUUID());
    item.put("title", "Book of all even numbers");
    item.put("barcode", "12345");
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
  private void updateItemWithNonexistingId(String field) {
    JsonObject response =
      send(ITEM_URL, HttpMethod.POST, createItem(newLoanType(), newLoanType()), HTTP_CREATED);
    String itemId = response.getString("id");

    String nonExistentLoanId = UUID.randomUUID().toString();

    JsonObject putRequest = response.copy().put(field, nonExistentLoanId);

    send(ITEM_URL+itemId, HttpMethod.PUT, putRequest.toString(), HTTP_BAD_REQUEST);
  }

  /**
   * Create a new loan type with random name.
   * @return the new loan type's id
   */
  private String newLoanType() {
    String randomName = "My name is " + UUID.randomUUID().toString();
    String content = "{\"name\": \"" + randomName + "\"}";
    JsonObject response = send(LOAN_TYPE_URL, HttpMethod.POST, content, HTTP_CREATED);
    // FIXME: read from location header
    return response.getString("id");
  }
}
