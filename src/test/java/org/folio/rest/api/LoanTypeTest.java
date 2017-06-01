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
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.net.HttpURLConnection.*;
import static org.folio.rest.api.StorageTestSuite.itemsUrl;
import static org.folio.rest.api.StorageTestSuite.loanTypesUrl;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

public class LoanTypeTest {

  private static final String       SUPPORTED_CONTENT_TYPE_JSON_DEF = "application/json";

  private static String postRequestCirculate = "{\"name\": \"Can circulate\"}";
  private static String postRequestCourse    = "{\"name\": \"Course reserve\"}";
  private static String putRequest  = "{\"name\": \"Reading room\"}";

  @Before
  public void beforeEach()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    StorageTestSuite.deleteAll(itemsUrl());
    StorageTestSuite.deleteAll(loanTypesUrl());
  }

  @Test
  public void canCreateALoanType()
    throws MalformedURLException {

    JsonObject response = send(loanTypesUrl(), HttpMethod.POST,
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

    send(loanTypesUrl(), HttpMethod.POST,
      requestWithAdditionalProperties.toString(), HTTP_BAD_REQUEST);
  }

  @Test
  public void cannotCreateALoanTypeWithSameName()
    throws MalformedURLException {

    send(loanTypesUrl(), HttpMethod.POST, postRequestCirculate, HTTP_CREATED);

    send(loanTypesUrl(), HttpMethod.POST, postRequestCirculate, HTTP_BAD_REQUEST);
  }

  @Test
  public void cannotCreateALoanTypeWithSameId()
    throws MalformedURLException {

    JsonObject createResponse = send(loanTypesUrl(), HttpMethod.POST,
      postRequestCirculate, HTTP_CREATED);

    String loanTypeID = createResponse.getString("id");

    send(loanTypesUrl(), HttpMethod.POST,
      createLoanType("over night", loanTypeID), HTTP_BAD_REQUEST);
  }

  @Test
  public void canGetALoanTypeById()
    throws MalformedURLException {

    JsonObject createResponse = send(loanTypesUrl(), HttpMethod.POST,
      postRequestCirculate, HTTP_CREATED);

    //fix to read from location header
    String loanTypeID = createResponse.getString("id");

    JsonObject getResponse = send(loanTypesUrl("/" + loanTypeID), HttpMethod.GET,
      null, HTTP_OK);

    assertThat(getResponse.getString("id"), is(loanTypeID));
    assertThat(getResponse.getString("name"), is("Can circulate"));
  }

  @Test
  public void cannotGetALoanTypeThatDoesNotExist()
    throws MalformedURLException {

    send(loanTypesUrl("/" + UUID.randomUUID()), HttpMethod.GET, null, HTTP_NOT_FOUND);
  }

  @Test
  public void canGetAllLoanTypes()
    throws MalformedURLException {

    send(loanTypesUrl(), HttpMethod.POST, postRequestCirculate, HTTP_CREATED);
    send(loanTypesUrl(), HttpMethod.POST, postRequestCourse, HTTP_CREATED);

    JsonObject response = send(loanTypesUrl(), HttpMethod.GET, null, HTTP_OK);

    assertThat(response.getInteger("totalRecords"), is(2));
  }

  @Test
  public void canDeleteAnUnusedLoanType()
    throws MalformedURLException {

    JsonObject createResponse = send(loanTypesUrl(), HttpMethod.POST,
      postRequestCirculate, HTTP_CREATED);

    //fix to read from location header
    String loanTypeID = createResponse.getString("id");

    send(loanTypesUrl("/" + loanTypeID), HttpMethod.DELETE, null, HTTP_NO_CONTENT);
  }

  @Test
  public void cannotDeleteALoanTypePermanentlyAssociatedToAnItem()
    throws MalformedURLException {

    JsonObject createResponse = send(loanTypesUrl(), HttpMethod.POST,
      postRequestCirculate, HTTP_CREATED);

    String loanTypeID = createResponse.getString("id");

    send(itemsUrl(), HttpMethod.POST, createItem(loanTypeID, null), HTTP_CREATED);

    send(loanTypesUrl("/" + loanTypeID), HttpMethod.DELETE, null, HTTP_BAD_REQUEST);
  }

  @Test
  public void cannotDeleteALoanTypeTemporarilyAssociatedToAnItem()
    throws MalformedURLException {

    JsonObject circulateCreateResponse = send(loanTypesUrl(), HttpMethod.POST,
      postRequestCirculate, HTTP_CREATED);

    String circulateLoanTypeId = circulateCreateResponse.getString("id");

    JsonObject reserveCreateResponse = send(loanTypesUrl(), HttpMethod.POST,
      postRequestCourse, HTTP_CREATED);

    String reserveLoanTypeId = reserveCreateResponse.getString("id");

    send(itemsUrl(), HttpMethod.POST,
      createItem(circulateLoanTypeId, reserveLoanTypeId), HTTP_CREATED);

    send(loanTypesUrl("/" + reserveLoanTypeId), HttpMethod.DELETE, null,
      HTTP_BAD_REQUEST);
  }

  @Test
  public void cannotDeleteLoanTypeThatDoesNotExist()
    throws MalformedURLException {

    send(loanTypesUrl("/" + UUID.randomUUID()), HttpMethod.DELETE, null, HTTP_NOT_FOUND);
  }

  @Test
  public void canUpdateALoanType() throws MalformedURLException {

    JsonObject createResponse = send(loanTypesUrl(), HttpMethod.POST,
      postRequestCirculate, HTTP_CREATED);

    //fix to read from location header
    String loanTypeID = createResponse.getString("id");

    send(loanTypesUrl("/" + loanTypeID), HttpMethod.PUT, putRequest, HTTP_NO_CONTENT);
  }

  @Test
  public void cannotUpdateLoanTypeThatDoesNotExist()
    throws MalformedURLException {

    String id = UUID.randomUUID().toString();

    send(loanTypesUrl("/" + id), HttpMethod.PUT, putRequest, HTTP_NOT_FOUND);
  }

  @Test
  public void cannotCreateItemWithPermanentLoanTypeThatDoesNotExist()
    throws MalformedURLException {

    String nonexistentLoanId = UUID.randomUUID().toString();

    send(itemsUrl(), HttpMethod.POST, createItem(nonexistentLoanId, null),
      HTTP_BAD_REQUEST);
  }

  @Test
  public void cannotCreateItemWithTemporaryLoanTypeThatDoesNotExist()
    throws MalformedURLException {

    String nonexistentLoanId = UUID.randomUUID().toString();

    send(itemsUrl(), HttpMethod.POST, createItem(null, nonexistentLoanId),
      HTTP_BAD_REQUEST);
  }

  @Test
  public void updateItemWithNonexistingPermanentLoanTypeId()
    throws MalformedURLException {

    updateItemWithNonexistingId("permanentLoanTypeId");
  }

  @Test
  public void updateItemWithNonexistingTemporaryLoanTypeId()
    throws MalformedURLException {

    updateItemWithNonexistingId("temporaryLoanTypeId");
  }

  private JsonObject send(URL url, HttpMethod method, String content,
                          int expectedStatusCode) {

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
  private void updateItemWithNonexistingId(String field)
    throws MalformedURLException {

    JsonObject response = send(itemsUrl(), HttpMethod.POST,
      createItem(newLoanType(), newLoanType()), HTTP_CREATED);

    String itemId = response.getString("id");

    String nonExistentLoanId = UUID.randomUUID().toString();

    JsonObject putRequest = response.copy().put(field, nonExistentLoanId);

    send(itemsUrl("/" + itemId), HttpMethod.PUT, putRequest.toString(),
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
    JsonObject response = send(loanTypesUrl(), HttpMethod.POST, content, HTTP_CREATED);
    // FIXME: read from location header
    return response.getString("id");
  }
}
