package org.folio.rest.api;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.folio.rest.support.http.InterfaceUrls.itemsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.loanTypesStorageUrl;
import static org.folio.utility.ModuleUtility.getClient;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.SneakyThrows;
import org.folio.rest.support.AdditionalHttpStatusCodes;
import org.folio.rest.support.HttpClient;
import org.folio.rest.support.Response;
import org.folio.rest.support.http.ResourceClient;
import org.folio.utility.RestUtility;
import org.junit.Before;
import org.junit.Test;

public class LoanTypeTest extends TestBaseWithInventoryUtil {

  private static final String SUPPORTED_CONTENT_TYPE_JSON_DEF = "application/json";

  private static String materialTypeID;

  private static final String POST_REQUEST_CIRCULATE = "{\"name\": \"Can circulate\"}";
  private static final String POST_REQUEST_COURSE = "{\"name\": \"Course reserve\"}";
  private static final String PUT_REQUEST = "{\"name\": \"Reading room\"}";

  /**
   * Create a JSON String of an item; set permanentLoanTypeId and temporaryLoanTypeId
   * if the passed variable is not null.
   */
  private static String createItem(UUID holdingsRecordId, String permanentLoanTypeId,
                                   String temporaryLoanTypeId) {

    JsonObject item = new JsonObject();

    item.put("status", new JsonObject().put("name", "Available"));
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

  /**
   * Create a JSON String with name and id element; does not include id if it is null.
   */
  private static String createLoanType(String name, String id) {
    JsonObject item = new JsonObject();

    item.put("name", name);
    if (id != null) {
      item.put("id", id);
    }

    return item.encode();
  }

  @SneakyThrows
  @Before
  public void beforeEach() {
    clearData();

    materialTypeID = ResourceClient.forMaterialTypes(getClient())
      .create(new JsonObject().put("name", "Journal"))
      .getId()
      .toString();

    setupLocations();
    removeAllEvents();
  }

  @Test
  public void canCreateLoanType()
    throws MalformedURLException {

    JsonObject response = send(loanTypesStorageUrl(""), HttpMethod.POST,
      POST_REQUEST_CIRCULATE, HTTP_CREATED);

    assertThat(response.getString("id"), notNullValue());
    assertThat(response.getString("name"), is("Can circulate"));
  }

  @Test
  public void cannotCreateLoanTypeWithAdditionalProperties()
    throws MalformedURLException {

    JsonObject requestWithAdditionalProperties = new JsonObject()
      .put("name", "Can Circulate")
      .put("additional", "foo");

    send(loanTypesStorageUrl(""), HttpMethod.POST,
      requestWithAdditionalProperties.toString(), AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY);
  }

  @Test
  public void cannotCreateLoanTypeWithSameName()
    throws MalformedURLException {

    send(loanTypesStorageUrl(""), HttpMethod.POST, POST_REQUEST_CIRCULATE, HTTP_CREATED);

    send(loanTypesStorageUrl(""), HttpMethod.POST, POST_REQUEST_CIRCULATE, HTTP_BAD_REQUEST);
  }

  @Test
  public void cannotCreateLoanTypeWithSameId()
    throws MalformedURLException {

    JsonObject createResponse = send(loanTypesStorageUrl(""), HttpMethod.POST,
      POST_REQUEST_CIRCULATE, HTTP_CREATED);

    String loanTypeId = createResponse.getString("id");

    send(loanTypesStorageUrl(""), HttpMethod.POST,
      createLoanType("over night", loanTypeId), HTTP_BAD_REQUEST);
  }

  @Test
  public void canGetLoanTypeById()
    throws MalformedURLException {

    JsonObject createResponse = send(loanTypesStorageUrl(""), HttpMethod.POST,
      POST_REQUEST_CIRCULATE, HTTP_CREATED);

    //fix to read from location header
    String loanTypeId = createResponse.getString("id");

    JsonObject getResponse = send(loanTypesStorageUrl("/" + loanTypeId), HttpMethod.GET,
      null, HTTP_OK);

    assertThat(getResponse.getString("id"), is(loanTypeId));
    assertThat(getResponse.getString("name"), is("Can circulate"));
  }

  @Test
  public void cannotGetLoanTypeThatDoesNotExist()
    throws MalformedURLException {

    send(loanTypesStorageUrl("/" + UUID.randomUUID()), HttpMethod.GET, null, HTTP_NOT_FOUND);
  }

  @Test
  public void canGetAllLoanTypes()
    throws MalformedURLException {

    send(loanTypesStorageUrl(""), HttpMethod.POST, POST_REQUEST_CIRCULATE, HTTP_CREATED);
    send(loanTypesStorageUrl(""), HttpMethod.POST, POST_REQUEST_COURSE, HTTP_CREATED);

    JsonObject response = send(loanTypesStorageUrl(""), HttpMethod.GET, null, HTTP_OK);

    assertThat(response.getInteger("totalRecords"), is(2));
  }

  @Test
  public void canDeleteAnUnusedLoanType()
    throws MalformedURLException {

    JsonObject createResponse = send(loanTypesStorageUrl(""), HttpMethod.POST,
      POST_REQUEST_CIRCULATE, HTTP_CREATED);

    //fix to read from location header
    String loanTypeId = createResponse.getString("id");

    send(loanTypesStorageUrl("/" + loanTypeId), HttpMethod.DELETE, null, HTTP_NO_CONTENT);
  }

  @Test
  public void cannotDeleteLoanTypePermanentlyAssociatedToAnItem()
    throws MalformedURLException, ExecutionException, InterruptedException, TimeoutException {

    JsonObject createResponse = send(loanTypesStorageUrl(""), HttpMethod.POST,
      POST_REQUEST_CIRCULATE, HTTP_CREATED);

    String loanTypeId = createResponse.getString("id");

    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

    send(itemsStorageUrl(""), HttpMethod.POST, createItem(holdingsRecordId, loanTypeId, null), HTTP_CREATED);

    send(loanTypesStorageUrl("/" + loanTypeId), HttpMethod.DELETE, null, HTTP_BAD_REQUEST);
  }

  @Test
  public void cannotDeleteLoanTypeTemporarilyAssociatedToAnItem()
    throws MalformedURLException, ExecutionException, InterruptedException, TimeoutException {

    JsonObject circulateCreateResponse = send(loanTypesStorageUrl(""), HttpMethod.POST,
      POST_REQUEST_CIRCULATE, HTTP_CREATED);

    String circulateLoanTypeId = circulateCreateResponse.getString("id");

    JsonObject reserveCreateResponse = send(loanTypesStorageUrl(""), HttpMethod.POST,
      POST_REQUEST_COURSE, HTTP_CREATED);

    String reserveLoanTypeId = reserveCreateResponse.getString("id");

    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

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
  public void canUpdateLoanType() throws MalformedURLException {

    JsonObject createResponse = send(loanTypesStorageUrl(""), HttpMethod.POST,
      POST_REQUEST_CIRCULATE, HTTP_CREATED);

    //fix to read from location header
    String loanTypeId = createResponse.getString("id");

    send(loanTypesStorageUrl("/" + loanTypeId), HttpMethod.PUT, PUT_REQUEST, HTTP_NO_CONTENT);
  }

  @Test
  public void cannotUpdateLoanTypeThatDoesNotExist()
    throws MalformedURLException {

    String id = UUID.randomUUID().toString();

    send(loanTypesStorageUrl("/" + id), HttpMethod.PUT, PUT_REQUEST, HTTP_NOT_FOUND);
  }

  @Test
  public void cannotCreateItemWithPermanentLoanTypeThatDoesNotExist()
    throws MalformedURLException,
    ExecutionException,
    InterruptedException,
    TimeoutException {

    String nonexistentLoanId = UUID.randomUUID().toString();

    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

    send(itemsStorageUrl(""), HttpMethod.POST, createItem(holdingsRecordId, nonexistentLoanId, null),
      AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY);
  }

  @Test
  public void cannotCreateItemWithTemporaryLoanTypeThatDoesNotExist()
    throws MalformedURLException,
    ExecutionException,
    InterruptedException,
    TimeoutException {

    JsonObject circulateCreateResponse = send(loanTypesStorageUrl(""), HttpMethod.POST,
      POST_REQUEST_CIRCULATE, HTTP_CREATED);

    String circulateLoanTypeId = circulateCreateResponse.getString("id");

    String nonexistentLoanId = UUID.randomUUID().toString();
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
    send(itemsStorageUrl(""), HttpMethod.POST,
      createItem(holdingsRecordId, circulateLoanTypeId, nonexistentLoanId),
      AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY);
  }

  @Test
  public void updateItemWithNonexistingPermanentLoanTypeId()
    throws MalformedURLException,
    ExecutionException,
    InterruptedException,
    TimeoutException {

    updateItemWithNonexistingId("permanentLoanTypeId");
  }

  @Test
  public void updateItemWithNonexistingTemporaryLoanTypeId()
    throws MalformedURLException,
    ExecutionException,
    InterruptedException,
    TimeoutException {

    updateItemWithNonexistingId("temporaryLoanTypeId");
  }

  private JsonObject send(URL url, HttpMethod method, String content,
                          int expectedStatusCode) {

    CompletableFuture<Response> future =
      HttpClient.asResponse(RestUtility.send(url, method, content, SUPPORTED_CONTENT_TYPE_JSON_DEF));

    Response response;

    try {
      response = future.get(10, TimeUnit.SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new IllegalStateException(e);
    }

    assertThat(url + " - " + method + " - " + content + ":" + response.getBody(),
      response.getStatusCode(), is(expectedStatusCode));

    try {
      return response.getJson();
    } catch (DecodeException e) {
      // No body at all or not in JSON format.
      return null;
    }
  }

  /**
   * Changing the field to an non existing UUID must fail.
   *
   * @param field - the field to change
   */
  private void updateItemWithNonexistingId(String field)
    throws MalformedURLException,
    ExecutionException,
    InterruptedException,
    TimeoutException {

    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

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
   *
   * @return the new loan type's id
   */
  private String newLoanType()
    throws MalformedURLException {

    String randomName = "My name is " + UUID.randomUUID();
    String content = "{\"name\": \"" + randomName + "\"}";
    JsonObject response = send(loanTypesStorageUrl(""), HttpMethod.POST, content, HTTP_CREATED);
    // FIXME: read from location header
    return response.getString("id");
  }
}
