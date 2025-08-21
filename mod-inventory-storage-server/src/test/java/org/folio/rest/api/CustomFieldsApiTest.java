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
  private static final String CUSTOM_FIELDS_OPTIONS_STATS_ENDPOINT = "/custom-fields/{id}/options/{optId}/stats";

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
   * Create simple items with custom fields and check the statistics count
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
    assertEquals(3, getCustomFieldStatisticCount(customFields.get(1).getString(ID)));
  }

  @Test
  public void putCustomFields() {

  }

  @Test
  public void postCustomFields() {

  }

  @Test
  public void getCustomFieldById() {

  }

  @Test
  public void putCustomFieldById() {

  }

  @Test
  public void deleteCustomFieldById() {

  }

  @Test
  public void getCustomFieldStatsById() {

  }

  @Test
  public void getCustomFieldOptionStats() {

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

  private JsonObject createCustomFieldValues() {
    return new JsonObject()
        .put("textbox", "text1")
        .put("singleselect", "opt_0")
        .put("multiselect", new JsonArray().add("opt_1").add("opt_2"));
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
    return simpleItems
        .get(itemToCreateIndex)
        .copy()
        .put(HOLDING_RECORD_ID, holdingId.toString())
        .put(CUSTOM_FIELDS, createCustomFieldValues());
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

  /**
   * Asserts the item got created by checking the status code.
   *
   * @param itemToCreate The JsonObject representing the item to create
   * @return The IndividualResource of the json response of the creation request
   */
  private IndividualResource saveItem(JsonObject itemToCreate) {
    return itemsClient.create(itemToCreate);
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
        .pathParams("id", customFieldId)
        .get(customFieldsUrl(CUSTOM_FIELDS_STATS_ENDPOINT))
        .then()
        .statusCode(200)
        .extract()
        .as(CustomFieldStatistic.class)
        .getCount();
  }
}
