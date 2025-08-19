package org.folio.rest.api;

import static org.folio.rest.support.http.InterfaceUrls.itemsStorageUrl;
import static org.folio.utility.ModuleUtility.getClient;
import static org.folio.utility.ModuleUtility.vertxUrl;
import static org.folio.utility.RestUtility.TENANT_ID;

import io.vertx.core.json.JsonObject;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import junitparams.JUnitParamsRunner;

import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.CustomField;
import org.folio.rest.jaxrs.model.CustomField.Type;
import org.folio.rest.jaxrs.model.SelectField;
import org.folio.rest.jaxrs.model.SelectFieldOption;
import org.folio.rest.jaxrs.model.SelectFieldOptions;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.github.tomakehurst.wiremock.client.WireMock;

@RunWith(JUnitParamsRunner.class)
public class CustomFieldsApiTest extends TestBaseWithInventoryUtil {

  private final UUID userId = UUID.randomUUID();
  private final JsonObject meta = new JsonObject()
      .put("creation_date", "2016-11-05T07:23")
      .put("last_login_date", "");

  private final JsonObject personal = new JsonObject()
      .put("lastName", "Handey")
      .put("firstName", "Jack")
      .put("preferredFirstName", "Jackie")
      .put("email", "jhandey@biglibrary.org")
      .put("phone", "2125551212");

  private final JsonObject user = new JsonObject()
      .put("username", "jhandey")
      .put("id", userId)
      .put("active", true)
      .put("type", "patron")
      .put("patronGroup", "4bb563d9-3f9d-4e1e-8d1d-04e75666d68f")
      .put("meta", meta)
      .put("personal", personal);

  private final List<JsonObject> simpleItems = List
      .of(simpleItem(), simpleItem(), simpleItem());

  private final List<CustomField> customFields = createCustomFields("item");

  private List<CustomField> createCustomFields(String entityType) {
    CustomField textbox = new CustomField()
        .withId(UUID.randomUUID().toString())
        .withName("textbox")
        .withType(Type.TEXTBOX_SHORT)
        .withEntityType(entityType);
    CustomField singleselect = new CustomField()
        .withId(UUID.randomUUID().toString())
        .withName("singleselect")
        .withType(Type.SINGLE_SELECT_DROPDOWN)
        .withSelectField(
            new SelectField()
                .withMultiSelect(false)
                .withOptions(
                    new SelectFieldOptions()
                        .withValues(
                            Arrays.asList(
                                new SelectFieldOption().withId("opt_0").withValue("opt0"),
                                new SelectFieldOption().withId("opt_1").withValue("opt1"),
                                new SelectFieldOption().withId("opt_2").withValue("opt2")))))
        .withEntityType(entityType);
    CustomField multiselect = new CustomField()
        .withId(UUID.randomUUID().toString())
        .withName("multiselect")
        .withType(Type.MULTI_SELECT_DROPDOWN)
        .withSelectField(
            new SelectField()
                .withMultiSelect(true)
                .withOptions(
                    new SelectFieldOptions()
                        .withValues(
                            Arrays.asList(
                                new SelectFieldOption().withId("opt_0").withValue("opt0"),
                                new SelectFieldOption().withId("opt_1").withValue("opt1"),
                                new SelectFieldOption().withId("opt_2").withValue("opt2"),
                                new SelectFieldOption().withId("opt_3").withValue("opt3")))))
        .withEntityType(entityType);

    return List.of(textbox, singleselect, multiselect);
  }

  private JsonObject simpleItem() {
    return new JsonObject()
        .put("id", UUID.randomUUID().toString())
        .put("status", new JsonObject().put("name", "Available"))
        .put("holdingsRecordId", UUID.randomUUID().toString())
        .put("materialTypeId", UUID.randomUUID().toString())
        .put("permanentLoanTypeId", UUID.randomUUID().toString());
  }

  private JsonObject itemAddCustomFields(JsonObject itemToCreate, JsonObject customFields) {
    return itemToCreate.copy().put("customFields", customFields);
  }

  public static URL customFieldsUrl(String subPath) {
    return vertxUrl("/custom-fields" + subPath);
  }

  private Response saveCustomFieldAndExpectText(CustomField customFieldToCreate) {
    var createCompleted = new CompletableFuture<Response>();
    String usersUrl = "http://localhost:" + mockServer.port();
    Map<String, String> headers = Map
        .of(XOkapiHeaders.USER_ID, userId.toString(), XOkapiHeaders.URL, usersUrl,
            XOkapiHeaders.URL_TO, usersUrl);
    getClient().post(
        customFieldsUrl(""),
        JsonObject.mapFrom(customFieldToCreate),
        headers,
        TENANT_ID,
        ResponseHandler.any(createCompleted));
    return get(createCompleted);
  }

  // private Response getCustomFieldAndExpectText() {
  // var createCompleted = new CompletableFuture<Response>();
  // getClient().get(customFieldsUrl(""), TENANT_ID,
  // ResponseHandler.any(createCompleted));
  // return get(createCompleted);
  // }

  private Response saveItemAndExpectText(JsonObject itemToCreate) {
    var createCompleted = new CompletableFuture<Response>();
    getClient().post(
        itemsStorageUrl(""),
        itemToCreate,
        TENANT_ID,
        ResponseHandler.any(createCompleted));
    return get(createCompleted);
  }

  @Before
  public void beforeEach() {
    StorageTestSuite.deleteAll(itemsStorageUrl(""));
    removeAllEvents();
    // folio-custom-field depends on UserService information for saving custom
    // fields
    WireMock.stubFor(WireMock.get("/users/" + userId)
        .willReturn(WireMock.okJson(user.encode())));
  }

  @After
  public void afterEach() {
    StorageTestSuite.deleteAll(itemsStorageUrl(""));
    removeAllEvents();
  }

  @Test
  public void testGetCustomFields() {
    // given
    // Response response2 = saveItemAndExpectText(simpleItems.get(0));
    Response response = saveCustomFieldAndExpectText(customFields.get(0));
    // Response response = getCustomFieldAndExpectText();
    // Response response2 = saveItemAndExpectText(simpleItems.get(0));
    saveCustomFieldAndExpectText(customFields.get(1));
    saveCustomFieldAndExpectText(customFields.get(2));
    simpleItems.get(0);
    // when
    // then
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
}
