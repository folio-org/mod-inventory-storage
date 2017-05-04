package org.folio.rest;

import static java.net.HttpURLConnection.*;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.ResponseHandler;
import org.junit.Assert;
import org.junit.Test;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;

public class LoanTypeTest {

  private static final String       SUPPORTED_CONTENT_TYPE_JSON_DEF = "application/json";

  private static final String       ITEM_URL = "/item-storage/items";
  private static final String       LOAN_TYPE_URL = "/loan-types/";

  private static String postRequestCirculate = "{\"name\": \"Can circulate\"}";
  private static String postRequestCourse    = "{\"name\": \"Course reserve\"}";
  private static String putRequest  = "{\"name\": \"Reading room\"}";

  @Test
  public void kickoff() throws Exception {
    /** add a loan type */
    JsonObject response =
        send(LOAN_TYPE_URL, HttpMethod.POST, postRequestCirculate, 201, HTTP_CREATED);
    //fix to read from location header
    String loanTypeID = response.getString("id");

    /** get loan type by id will return 200 */
    response = send(LOAN_TYPE_URL + loanTypeID, HttpMethod.GET, null, 200, HTTP_OK);
    assertThat(response.getString("name"), is("Can circulate"));

    /** add a duplicate loan type name */
    send(LOAN_TYPE_URL, HttpMethod.POST, postRequestCirculate, 422, 422);

    /** add a duplicate loan type id */
    send(LOAN_TYPE_URL, HttpMethod.POST, createLoanType("over night", loanTypeID), 422, 422);

    /** update the loan type */
    send(LOAN_TYPE_URL+loanTypeID, HttpMethod.PUT, putRequest, 204, HTTP_NO_CONTENT);

    /** get loan type by id */
    response = send(LOAN_TYPE_URL + loanTypeID, HttpMethod.GET, null, 200, HTTP_OK);
    assertThat(response.getString("name"), is("Reading room"));

    /** get bad loan id will return 404 */
    String badId = "12345678-1234-1234-1234-1234567890ab";
    send(LOAN_TYPE_URL + badId, HttpMethod.GET, null, 404, HTTP_NOT_FOUND);

    /** add a course reserve loan type */
    response = send(LOAN_TYPE_URL, HttpMethod.POST, postRequestCourse, 201, HTTP_CREATED);
    //fix to read from location header
    String courseLoanTypeID = response.getString("id");

    /** add an item with permanent loan type */
    response = send(ITEM_URL, HttpMethod.POST, createItem(loanTypeID, null), 201, HTTP_CREATED);
    String itemID = response.getString("id");

    /** add an item with permanent and temporary loan type */
    response = send(ITEM_URL, HttpMethod.POST, createItem(loanTypeID, courseLoanTypeID), 201, HTTP_CREATED);
    String itemID2 = response.getString("id");

    /** get all loan types */
    response = send(LOAN_TYPE_URL, HttpMethod.GET, null, 200, HTTP_OK);
    assertThat(response.getInteger("totalRecords"), is(2));

    /** delete loan type in use - should fail as there is an item associated with the loan type */
    send(LOAN_TYPE_URL+      loanTypeID, HttpMethod.DELETE, null, 204, HTTP_BAD_REQUEST);
    send(LOAN_TYPE_URL+courseLoanTypeID, HttpMethod.DELETE, null, 204, HTTP_BAD_REQUEST);

    /** delete item with the course reserve loan type */
    send(ITEM_URL+"/"+itemID2, HttpMethod.DELETE, null, 204, HTTP_NO_CONTENT);

    /** delete the course reserve loan type, no items use it, should succeed */
    send(LOAN_TYPE_URL+courseLoanTypeID, HttpMethod.DELETE, null, 204, HTTP_NO_CONTENT);

    /** delete the first loan type that is still in use, should fail */
    send(LOAN_TYPE_URL+      loanTypeID, HttpMethod.DELETE, null, 204, HTTP_BAD_REQUEST);

    /** delete item with the first loan type */
    send(ITEM_URL+"/"+itemID, HttpMethod.DELETE, null, 204, HTTP_NO_CONTENT);

    /** delete the first loan type with no items attached */
    send(LOAN_TYPE_URL+loanTypeID, HttpMethod.DELETE, null, 204, HTTP_NO_CONTENT);

    /** delete non existant loan type */
    send(LOAN_TYPE_URL+loanTypeID, HttpMethod.DELETE, null, 404, HTTP_NOT_FOUND);

    /** update non existant loan type */
    send(LOAN_TYPE_URL+loanTypeID, HttpMethod.PUT, putRequest, 404, HTTP_NOT_FOUND);
  }

  private JsonObject send(String urlPath, HttpMethod method, String content,
      int errorCode, int expectedStatusCode) throws Exception {
    String url = StorageTestSuite.storageUrl(urlPath).toString();
    CompletableFuture<JsonResponse> future = new CompletableFuture<>();
    Handler<HttpClientResponse> handler = ResponseHandler.json(future);
    send(url, method, content, errorCode, handler);
    JsonResponse response = future.get(5, TimeUnit.SECONDS);
    assertThat(url + " - " + method + " - " + content,
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
      int errorCode, Handler<HttpClientResponse> handler) {
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

    item.put("instanceId", ""+UUID.randomUUID());
    item.put("title", "abcd");
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

}
