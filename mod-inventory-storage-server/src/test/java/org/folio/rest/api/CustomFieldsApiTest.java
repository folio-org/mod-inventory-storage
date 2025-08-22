package org.folio.rest.api;

import static io.restassured.RestAssured.given;
import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.folio.rest.support.http.InterfaceUrls.itemsStorageUrl;
import static org.folio.utility.ModuleUtility.getClient;
import static org.folio.utility.ModuleUtility.vertxUrl;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.restassured.http.Header;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import junitparams.JUnitParamsRunner;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.CustomField.Type;
import org.folio.rest.jaxrs.model.CustomFieldOptionStatistic;
import org.folio.rest.jaxrs.model.CustomFieldStatistic;
import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class CustomFieldsApiTest extends TestBaseWithInventoryUtil {

  private static final String ACTIVE = "active";
  private static final String CREATION_DATE = "creation_date";
  private static final String CUSTOM_FIELDS = "customFields";
  private static final String CUSTOM_FIELDS_URL = "/custom-fields";
  private static final String EMAIL = "email";
  private static final String ENTITY_TYPE = "entityType";
  private static final String FIRST_NAME = "firstName";
  private static final String HOLDING_RECORD_ID = "holdingsRecordId";
  private static final String HTTP_LOCALHOST = "http://localhost:";
  private static final String ID = "id";
  private static final String ITEM = "item";
  private static final String LAST_LOGIN_DATE = "last_login_date";
  private static final String LAST_NAME = "lastName";
  private static final String MATERIAL_TYPE_ID = "materialTypeId";
  private static final String META = "meta";
  private static final String MULTI_SELECT = "multiSelect";
  private static final String NAME = "name";
  private static final String OPTIONS = "options";
  private static final String PATRON_GROUP = "patronGroup";
  private static final String PERMANENT_LOAN_TYPE_ID = "permanentLoanTypeId";
  private static final String PERSONAL = "personal";
  private static final String PHONE = "phone";
  private static final String PREFERRED_FIRST_NAME = "preferredFirstName";
  private static final String SELECT_FIELD = "selectField";
  private static final String STATUS = "status";
  private static final String TYPE = "type";
  private static final String USERNAME = "username";
  private static final String USERS_URL = "/users/";
  private static final String VALUE = "value";
  private static final String VALUES = "values";
  private static final String CUSTOM_FIELDS_STATS_ENDPOINT = "/{id}/stats";
  private static final String CUSTOM_FIELDS_OPTIONS_STATS_ENDPOINT = "/{id}/options/{optId}/stats";

  private final UUID userId = UUID.randomUUID();
  private final JsonObject user = createSimpleUser(userId);
  private UUID holdingId;
  private final List<JsonObject> simpleItems = List
      .of(simpleItem(), simpleItem(), simpleItem());
  private final List<JsonObject> customFields = createCustomFields(ITEM);

  @Before
  public void beforeEach() {
    StorageTestSuite.deleteAll(itemsStorageUrl(""));
    removeAllEvents();
    // folio-custom-field depends on UserService information for saving custom
    // fields
    WireMock.stubFor(WireMock.get(USERS_URL + userId)
        .willReturn(WireMock.okJson(user.encode())));
    holdingId = createInstanceAndHolding(MAIN_LIBRARY_LOCATION_ID);
  }

  @After
  public void afterEach() {
    StorageTestSuite.deleteAll(itemsStorageUrl(""));
    removeAllEvents();
  }

  /*
   * Create simple items with custom fields and check the statistics count.
   * POST /custom-fields
   * GET /custom-fields/{id}/stats
   * GET /custom-fields/{id}/options/{optId}/stats
   */
  @Test
  public void testSaveItemsWithCustomFields() {
    // given
    // 3 simple items and default custom fields as JsonObjects
    // (textbox, singleselect, multiselect)
    // when
    // saving the custom fields in the database
    saveCustomField(customFields.get(0));
    saveCustomField(customFields.get(1));
    saveCustomField(customFields.get(2));
    // adding references to the 3 custom fields and
    // corresponding values for each of the items
    JsonObject itemWithCustomField0 = itemAddCustomFields(0);
    JsonObject itemWithCustomField1 = itemAddCustomFields(1);
    JsonObject itemWithCustomField2 = itemAddCustomFields(2);
    // and saving the items with custom field values in the database
    saveItem(itemWithCustomField0);
    saveItem(itemWithCustomField1);
    saveItem(itemWithCustomField2);
    // then
    // the items should be created
    // and statistics should work.
    assertEquals(3, getCustomFieldStatisticCount(customFieldsId(1)));
    assertEquals(3, getCustomFieldOptionStatisticCount(customFieldsId(1), "opt_0"));
  }

  /**
   *
   * POST /custom-fields
   * GET /custom-fields
   * GET /custom-fields/{id}/stats
   */
  @Test
  public void testDeleteCustomField() {
    // given
    // simple item and default custom field as JsonObject
    // (textbox)
    // when
    // saving the custom field in the database
    assertEquals(0, getCustomFieldsCount());
    saveCustomField(customFields.get(0));
    assertEquals(1, getCustomFieldsCount());
    assertEquals(0, getCustomFieldStatisticCount(customFieldsId(0)));
    // adding references to the custom field and
    // corresponding value
    // and saving the items with custom field values in the database
    IndividualResource item = saveItem(itemAddCustomFields(0, List.of(0)));
    assertEquals(true, itemContainsCustomField(item, "textbox"));
    assertEquals(1, getCustomFieldStatisticCount(customFieldsId(0)));
    deleteCustomField(customFieldsId(0));
    // then
    // the items should be created
    assertEquals(0, getCustomFieldsCount());
    // check that item has no custom field anymore
    IndividualResource updatedItem = getItem(item.getId());
    assertEquals(false, itemContainsCustomField(updatedItem, "textbox"));
  }

  /**
   * remove single option from custom field
   * customFieldsPO.get(1).getSelectField().getOptions().getValues().removeFirst();
   * putCustomField(customFieldsPO.get(1));
   * 
   * assertEquals(2,
   * purchaseOrder1.getCustomFields().getAdditionalProperties().size());
   * assertNull(purchaseOrder1.getCustomFields().getAdditionalProperties().get("singleselect"));
   * assertThat(purchaseOrder2, samePropertyValuesAs(purchaseOrders.get(1)));
   * 
   * POST /custom-fields
   * PUT /custom-fields/{id}
   */
  @Test
  public void testCustomFieldPut() {
    // given
    // simple item and default custom field as JsonObject
    // (singleselect)
    // when
    // saving the custom field in the database
    saveCustomField(customFields.get(1));
    // adding references to the custom field and
    // corresponding value
    // and saving the items with custom field values in the database
    IndividualResource item = saveItem(itemAddCustomFields(0, List.of(1)));

  }

  @Test
  void testPutPutCustomFieldCollection() {
    // // populate with sample data
    // populateSampleData();

    // // remove opt from custom field using PutCustomFieldsCollection
    // customFieldsPO.get(1).getSelectField().getOptions().getValues().removeFirst();
    // putCustomFieldCollection(
    // new PutCustomFieldCollection()
    // .withCustomFields(customFieldsPO)
    // .withEntityType("purchase_order"));

    // // POs should be updated
    // Map<String, PurchaseOrder> allPurchaseOrders = getAllPurchaseOrders();
    // PurchaseOrder purchaseOrder1 =
    // allPurchaseOrders.get(purchaseOrders.get(0).getId());
    // PurchaseOrder purchaseOrder2 =
    // allPurchaseOrders.get(purchaseOrders.get(1).getId());
    // assertEquals(2,
    // purchaseOrder1.getCustomFields().getAdditionalProperties().size());
    // assertNull(purchaseOrder1.getCustomFields().getAdditionalProperties().get("singleselect"));
    // assertEquals(3,
    // purchaseOrder2.getCustomFields().getAdditionalProperties().size());
    // assertThat(purchaseOrder2, samePropertyValuesAs(purchaseOrders.get(1)));
  }

  private boolean itemContainsCustomField(IndividualResource item, String name) {
    return ((JsonObject) item.getJson().getValue(CUSTOM_FIELDS)).containsKey(name);
  }

  private String customFieldsId(int index) {
    return customFields.get(index).getString(ID);
  }

  /**
   * Creates a select field or multiselect.
   * Adds numberOfValues options to the select.
   *
   * @param numberOfValues number of options added
   * @param multiselect    flag deciding if result is a select field or a
   *                       multiselect
   * @return a select field or multiselect as JsonObject
   */
  private JsonObject createSelectField(int numberOfValues, boolean multiselect) {
    JsonArray values = new JsonArray();
    for (int i = 0; i < numberOfValues; i++) {
      values.add(new JsonObject()
          .put(ID, "opt_" + i)
          .put(VALUE, "opt" + i));
    }

    return new JsonObject()
        .put(MULTI_SELECT, multiselect)
        .put(OPTIONS, new JsonObject()
            .put(VALUES, values));
  }

  /**
   * Create a list of custom fields as json objects for the entityType
   * The list consists of a textbox, a single select and a multi select.
   *
   * @param entityType The entity type the custom field is added to
   * @return list of custom fields as json objects
   */
  private List<JsonObject> createCustomFields(String entityType) {
    JsonObject textbox = new JsonObject()
        .put(ID, UUID.randomUUID().toString())
        .put(NAME, "textbox")
        .put(TYPE, Type.TEXTBOX_SHORT)
        .put(ENTITY_TYPE, entityType);

    JsonObject singleselect = new JsonObject()
        .put(ID, UUID.randomUUID().toString())
        .put(NAME, "singleselect")
        .put(TYPE, Type.SINGLE_SELECT_DROPDOWN)
        .put(ENTITY_TYPE, entityType)
        .put(SELECT_FIELD, createSelectField(3, false));

    JsonObject multiselect = new JsonObject()
        .put(ID, UUID.randomUUID().toString())
        .put(NAME, "multiselect")
        .put(TYPE, Type.MULTI_SELECT_DROPDOWN)
        .put(ENTITY_TYPE, entityType)
        .put(SELECT_FIELD, createSelectField(4, true));
    return List.of(textbox, singleselect, multiselect);
  }

  private JsonObject createCustomFieldValues(List<Integer> customFieldIndices) {
    JsonObject customFieldValues = new JsonObject();
    if (customFieldIndices.contains(0))
      customFieldValues.put("textbox", "text1");
    if (customFieldIndices.contains(1))
      customFieldValues.put("singleselect", "opt_0");
    if (customFieldIndices.contains(2))
      customFieldValues.put("multiselect", new JsonArray().add("opt_1").add("opt_2"));
    return customFieldValues;
  }

  private JsonObject simpleItem() {
    return new JsonObject()
        .put(ID, UUID.randomUUID().toString())
        .put(STATUS, new JsonObject().put(NAME, "Available"))
        .put(MATERIAL_TYPE_ID, journalMaterialTypeID)
        .put(PERMANENT_LOAN_TYPE_ID, canCirculateLoanTypeID);
  }

  /**
   * Add custom field references by name and values to the specified item
   * by the choosen index in simpleItems.
   *
   * @param itemToCreateIndex index of choosen item from simpleItems
   * @return a complete json object with custom fields
   */
  private JsonObject itemAddCustomFields(int itemToCreateIndex) {
    return itemAddCustomFields(itemToCreateIndex, List.of(0, 1, 2));
  }

  private JsonObject itemAddCustomFields(int itemToCreateIndex, List<Integer> customFieldIndices) {
    return simpleItems
        .get(itemToCreateIndex)
        .copy()
        .put(HOLDING_RECORD_ID, holdingId.toString())
        .put(CUSTOM_FIELDS, createCustomFieldValues(customFieldIndices));
  }

  private static URL customFieldsUrl(String subPath) {
    return vertxUrl(CUSTOM_FIELDS_URL + subPath);
  }

  /**
   * Save the customfield with the API.
   * Assert the customfields got created by checking the status code.
   * Here we also need to add the mock to a user interface because custom fields
   * still have dependencies on a user
   *
   * @param customFieldToCreate The JsonObject representing the custom field
   * @return Response of the creation request
   */
  private Response saveCustomField(JsonObject customFieldToCreate) {
    var createCompleted = new CompletableFuture<Response>();
    String usersUrl = HTTP_LOCALHOST + mockServer.port();
    Map<String, String> headers = Map
        .of(XOkapiHeaders.USER_ID, userId.toString(), XOkapiHeaders.URL, usersUrl,
            XOkapiHeaders.URL_TO, usersUrl);
    getClient().post(
        customFieldsUrl(""),
        customFieldToCreate,
        headers,
        TENANT_ID,
        ResponseHandler.any(createCompleted));
    Response response = get(createCompleted);
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
    return response;
  }

  private Response deleteCustomField(String id) {
    var createCompleted = new CompletableFuture<Response>();
    String usersUrl = HTTP_LOCALHOST + mockServer.port();
    Map<String, String> headers = Map
        .of(XOkapiHeaders.USER_ID, userId.toString(), XOkapiHeaders.URL, usersUrl,
            XOkapiHeaders.URL_TO, usersUrl);
    getClient().delete(
        customFieldsUrl("/" + id),
        headers,
        TENANT_ID,
        ResponseHandler.any(createCompleted));
    Response response = get(createCompleted);
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
    return response;
  }

  /**
   * Asserts the item got created by checking the status code.
   *
   * @param itemToCreate The JsonObject representing the item to create
   * @return The IndividualResource of the json response of the creation request
   */
  private IndividualResource saveItem(JsonObject itemToCreate) {
    return itemsClient.create(itemToCreate);
  }

  private IndividualResource getItem(UUID id) {
    Response response = itemsClient.getById(id);
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    return new IndividualResource(response);
  }

  private JsonObject createSimpleUser(UUID newUserId) {
    JsonObject meta = new JsonObject()
        .put(CREATION_DATE, "2016-11-05T07:23")
        .put(LAST_LOGIN_DATE, "");

    JsonObject personal = new JsonObject()
        .put(LAST_NAME, "Handey")
        .put(FIRST_NAME, "Jack")
        .put(PREFERRED_FIRST_NAME, "Jackie")
        .put(EMAIL, "jhandey@biglibrary.org")
        .put(PHONE, "2125551212");

    return new JsonObject()
        .put(USERNAME, "jhandey")
        .put(ID, newUserId)
        .put(ACTIVE, true)
        .put(TYPE, "patron")
        .put(PATRON_GROUP, "4bb563d9-3f9d-4e1e-8d1d-04e75666d68f")
        .put(META, meta)
        .put(PERSONAL, personal);
  }

  private int getCustomFieldStatisticCount(String customFieldId) {
    return given()
        .header(new Header(TENANT, TENANT_ID))
        .pathParams(ID, customFieldId)
        .get(customFieldsUrl(CUSTOM_FIELDS_STATS_ENDPOINT))
        .then()
        .statusCode(200)
        .extract()
        .as(CustomFieldStatistic.class)
        .getCount();
  }

  private int getCustomFieldOptionStatisticCount(String customFieldId, String optionId) {
    return given()
        .header(new Header(TENANT, TENANT_ID))
        .pathParams(ID, customFieldId, "optId", optionId)
        .get(customFieldsUrl(CUSTOM_FIELDS_OPTIONS_STATS_ENDPOINT))
        .then()
        .statusCode(200)
        .extract()
        .as(CustomFieldOptionStatistic.class)
        .getCount();
  }

  private int getCustomFieldsCount() {
    var createCompleted = new CompletableFuture<Response>();
    getClient().get(
        customFieldsUrl("/"),
        TENANT_ID,
        ResponseHandler.any(createCompleted));
    Response response = get(createCompleted);
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    return new JsonObject(response.getBody()).getInteger("totalRecords");
  }
}
