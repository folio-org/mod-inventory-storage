package org.folio.rest.api;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.folio.rest.support.http.InterfaceUrls.itemsStorageUrl;
import static org.folio.rest.support.http.InterfaceUrls.loanTypesStorageUrl;
import static org.folio.utility.ModuleUtility.getClient;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.SneakyThrows;
import org.folio.HttpStatus;
import org.folio.rest.support.HttpClient;
import org.folio.rest.support.Response;
import org.folio.rest.support.http.ResourceClient;
import org.folio.utility.RestUtility;
import org.junit.Before;
import org.junit.Test;

public class LoanTypeTest extends TestBaseWithInventoryUtil {

  private static final String SUPPORTED_CONTENT_TYPE_JSON_DEF = "application/json";
  private static final String POST_REQUEST_CIRCULATE = "{\"name\": \"Can circulate\"}";
  private static final String POST_REQUEST_COURSE = "{\"name\": \"Course reserve\"}";
  private static final String POST_READING_ROOM = "{\"name\": \"Reading room\", \"source\": \"System\"}";
  private static final String PUT_REQUEST = "{\"name\": \"Reading room\"}";
  private static String materialTypeID;

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
  public void canCreateLoanType() {
    JsonObject response = send(loanTypesStorageUrl(""), HttpMethod.POST,
      POST_REQUEST_CIRCULATE, HTTP_CREATED);

    assertThat(response).isNotNull();
    assertThat(response.getString("id")).isNotNull();
    assertThat(response.getString("name")).isEqualTo("Can circulate");
  }

  @Test
  public void canCreateLoanTypeWithSourceFieldPopulated() {
    // post new loan type with 'source' field populated
    JsonObject response = send(loanTypesStorageUrl(""), HttpMethod.POST,
      POST_READING_ROOM, HTTP_CREATED);

    // verify all fields have been saved
    assertThat(response).isNotNull();
    assertThat(response.getString("id")).isNotNull();
    assertThat(response.getString("name")).isEqualTo("Reading room");
    assertThat(response.getString("source")).isEqualTo("System");
  }

  @Test
  public void cannotCreateLoanTypeWithAdditionalProperties() {
    JsonObject requestWithAdditionalProperties = new JsonObject()
      .put("name", "Can Circulate")
      .put("additional", "foo");

    send(loanTypesStorageUrl(""), HttpMethod.POST,
      requestWithAdditionalProperties.toString(), HttpStatus.HTTP_UNPROCESSABLE_ENTITY.toInt());
  }

  @Test
  public void cannotCreateLoanTypeWithSameName() {
    send(loanTypesStorageUrl(""), HttpMethod.POST, POST_REQUEST_CIRCULATE, HTTP_CREATED);
    send(loanTypesStorageUrl(""), HttpMethod.POST, POST_REQUEST_CIRCULATE, HTTP_BAD_REQUEST);
  }

  @Test
  public void cannotCreateLoanTypeWithSameId() {
    JsonObject createResponse = send(loanTypesStorageUrl(""), HttpMethod.POST,
      POST_REQUEST_CIRCULATE, HTTP_CREATED);

    String loanTypeId = createResponse.getString("id");

    send(loanTypesStorageUrl(""), HttpMethod.POST,
      createLoanType("over night", loanTypeId), HTTP_BAD_REQUEST);
  }

  @Test
  public void canGetLoanTypeById() {
    JsonObject createResponse = send(loanTypesStorageUrl(""), HttpMethod.POST,
      POST_REQUEST_CIRCULATE, HTTP_CREATED);

    String loanTypeId = null;
    if (createResponse != null) {
      loanTypeId = createResponse.getString("id");
    } else {
      fail();
    }

    JsonObject getResponse = send(loanTypesStorageUrl("/" + loanTypeId), HttpMethod.GET,
      null, HTTP_OK);

    assertThat(getResponse).isNotNull();
    assertThat(getResponse.getString("id")).isEqualTo(loanTypeId);
    assertThat(getResponse.getString("name")).isEqualTo("Can circulate");
  }

  @Test
  public void canGetLoanTypeByIdWithSourceFieldPopulated() {
    // post new loan type with 'source' field populated
    JsonObject createResponse = send(loanTypesStorageUrl(""), HttpMethod.POST,
      POST_READING_ROOM, HTTP_CREATED);

    // get id of created loan type
    String loanTypeId = null;
    if (createResponse != null) {
      loanTypeId = createResponse.getString("id");
    } else {
      fail();
    }

    // get saved loan type by id and verify all fields have been populated
    JsonObject response = send(loanTypesStorageUrl("/" + loanTypeId), HttpMethod.GET, null, HTTP_OK);

    assertThat(response).isNotNull();
    assertThat(response.getString("id")).isEqualTo(loanTypeId);
    assertThat(response.getString("name")).isEqualTo("Reading room");
    assertThat(response.getString("source")).isEqualTo("System");
  }

  @Test
  public void cannotGetLoanTypeThatDoesNotExist() {
    send(loanTypesStorageUrl("/" + UUID.randomUUID()), HttpMethod.GET, null, HTTP_NOT_FOUND);
  }

  @Test
  public void canGetAllLoanTypes() {
    send(loanTypesStorageUrl(""), HttpMethod.POST, POST_REQUEST_CIRCULATE, HTTP_CREATED);
    send(loanTypesStorageUrl(""), HttpMethod.POST, POST_REQUEST_COURSE, HTTP_CREATED);

    JsonObject response = send(loanTypesStorageUrl(""), HttpMethod.GET, null, HTTP_OK);

    assertThat(response).isNotNull();
    assertThat(response.getInteger("totalRecords")).isEqualTo(2);
  }

  @Test
  public void canDeleteAnUnusedLoanType() {
    JsonObject createResponse = send(loanTypesStorageUrl(""), HttpMethod.POST,
      POST_REQUEST_CIRCULATE, HTTP_CREATED);

    //fix to read from location header
    String loanTypeId = null;
    if (createResponse != null) {
      loanTypeId = createResponse.getString("id");
    } else {
      fail();
    }

    send(loanTypesStorageUrl("/" + loanTypeId), HttpMethod.DELETE, null, HTTP_NO_CONTENT);
  }

  @Test
  public void cannotDeleteLoanTypePermanentlyAssociatedToAnItem() {
    JsonObject createResponse = send(loanTypesStorageUrl(""), HttpMethod.POST,
      POST_REQUEST_CIRCULATE, HTTP_CREATED);

    String loanTypeId = createResponse.getString("id");

    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

    send(itemsStorageUrl(""), HttpMethod.POST, createItem(holdingsRecordId, loanTypeId, null), HTTP_CREATED);

    send(loanTypesStorageUrl("/" + loanTypeId), HttpMethod.DELETE, null, HTTP_BAD_REQUEST);
  }

  @Test
  public void cannotDeleteLoanTypeTemporarilyAssociatedToAnItem() {
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
  public void cannotDeleteLoanTypeThatDoesNotExist() {
    send(loanTypesStorageUrl("/" + UUID.randomUUID()), HttpMethod.DELETE, null, HTTP_NOT_FOUND);
  }

  @Test
  public void canUpdateLoanType() {
    JsonObject createResponse = send(loanTypesStorageUrl(""), HttpMethod.POST,
      POST_REQUEST_CIRCULATE, HTTP_CREATED);

    //fix to read from location header
    String loanTypeId = createResponse.getString("id");

    send(loanTypesStorageUrl("/" + loanTypeId), HttpMethod.PUT, PUT_REQUEST, HTTP_NO_CONTENT);
  }

  @Test
  public void cannotUpdateLoanTypeThatDoesNotExist() {
    String id = UUID.randomUUID().toString();

    send(loanTypesStorageUrl("/" + id), HttpMethod.PUT, PUT_REQUEST, HTTP_NOT_FOUND);
  }

  @Test
  public void cannotCreateItemWithPermanentLoanTypeThatDoesNotExist() {
    String nonexistentLoanId = UUID.randomUUID().toString();

    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

    send(itemsStorageUrl(""), HttpMethod.POST, createItem(holdingsRecordId, nonexistentLoanId, null),
      HttpStatus.HTTP_UNPROCESSABLE_ENTITY.toInt());
  }

  @Test
  public void cannotCreateItemWithTemporaryLoanTypeThatDoesNotExist() {
    JsonObject circulateCreateResponse = send(loanTypesStorageUrl(""), HttpMethod.POST,
      POST_REQUEST_CIRCULATE, HTTP_CREATED);

    String circulateLoanTypeId = circulateCreateResponse.getString("id");

    String nonexistentLoanId = UUID.randomUUID().toString();
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
    send(itemsStorageUrl(""), HttpMethod.POST,
      createItem(holdingsRecordId, circulateLoanTypeId, nonexistentLoanId),
      HttpStatus.HTTP_UNPROCESSABLE_ENTITY.toInt());
  }

  @Test
  public void updateItemWithNonexistingPermanentLoanTypeId() {
    updateItemWithNonexistingId("permanentLoanTypeId");
  }

  @Test
  public void updateItemWithNonexistingTemporaryLoanTypeId() {
    updateItemWithNonexistingId("temporaryLoanTypeId");
  }

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

  private JsonObject send(URL url, HttpMethod method, String content,
                          int expectedStatusCode) {
    CompletableFuture<Response> future =
      HttpClient.asResponse(RestUtility.send(url, method, content, SUPPORTED_CONTENT_TYPE_JSON_DEF));

    Response response;

    try {
      response = future.get(TIMEOUT, TimeUnit.SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new IllegalStateException(e);
    }

    assertThat(response.getStatusCode())
      .as(url + " - " + method + " - " + content + ":" + response.getBody())
      .isEqualTo(expectedStatusCode);

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
  private void updateItemWithNonexistingId(String field) {
    UUID holdingsRecordId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);

    JsonObject response = send(itemsStorageUrl(""), HttpMethod.POST,
      createItem(holdingsRecordId, newLoanType(), newLoanType()), HTTP_CREATED);

    String itemId = null;
    if (response != null) {
      itemId = response.getString("id");
    } else {
      fail();
    }

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
  private String newLoanType() {
    String randomName = "My name is " + UUID.randomUUID();
    String content = "{\"name\": \"" + randomName + "\"}";
    JsonObject response = send(loanTypesStorageUrl(""), HttpMethod.POST, content, HTTP_CREATED);
    return response.getString("id");
  }
}
