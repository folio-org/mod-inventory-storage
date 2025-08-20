package org.folio.rest.api;

import static org.folio.rest.support.http.InterfaceUrls.itemsStorageUrl;
import static org.folio.utility.ModuleUtility.getClient;
import static org.folio.utility.ModuleUtility.vertxUrl;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import junitparams.JUnitParamsRunner;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.CustomField.Type;
import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.Test;

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
   * Create simple items with custom fields.
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
    // the items should be created.
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

  private JsonObject createSelectField(int numberOfValues) {
    JsonArray values = new JsonArray();
    for (int i = 0; i < numberOfValues; i++) {
      values = values.add(new JsonObject().put(ID, "opt_" + i).put(VALUE, "opt" + i));
    }

    return new JsonObject()
        .put(MULTI_SELECT, false)
        .put(OPTIONS, new JsonObject()
            .put(VALUES, values));
  }

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
        .put(SELECT_FIELD, createSelectField(3));

    JsonObject multiselect = new JsonObject()
        .put(ID, UUID.randomUUID().toString())
        .put(NAME, "multiselect")
        .put(TYPE, Type.MULTI_SELECT_DROPDOWN)
        .put(ENTITY_TYPE, entityType)
        .put(SELECT_FIELD, createSelectField(4));
    return List.of(textbox, singleselect, multiselect);
  }

  private JsonObject simpleItem() {
    return new JsonObject()
        .put(ID, UUID.randomUUID().toString())
        .put(STATUS, new JsonObject().put(NAME, "Available"))
        .put(MATERIAL_TYPE_ID, journalMaterialTypeID)
        .put(PERMANENT_LOAN_TYPE_ID, canCirculateLoanTypeId);
  }

  private JsonObject itemAddCustomFields(JsonObject itemToCreate, JsonObject customFields) {
    return itemToCreate.copy().put(CUSTOM_FIELDS, customFields);
  }

  private static URL customFieldsUrl(String subPath) {
    return vertxUrl(CUSTOM_FIELDS_URL + subPath);
  }

  private Response saveCustomFieldAndExpectText(JsonObject customFieldToCreate) {
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

  private Response getCustomFieldAndExpectText() {
    var createCompleted = new CompletableFuture<Response>();
    getClient().get(customFieldsUrl(""), TENANT_ID,
        ResponseHandler.any(createCompleted));
    return get(createCompleted);
  }

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
}
