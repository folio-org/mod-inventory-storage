package org.folio.rest.impl;

import static org.folio.HttpStatus.HTTP_BAD_REQUEST;
import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.HttpStatus.HTTP_NOT_FOUND;
import static org.folio.HttpStatus.HTTP_NO_CONTENT;
import static org.folio.HttpStatus.HTTP_OK;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.folio.rest.impl.InstanceStorageFixtures.createInstance;
import static org.folio.rest.impl.InstanceStorageFixtures.createInstanceType;
import static org.folio.rest.impl.ResourcePaths.PRECEDING_SUCCEEDING_TITLES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.UUID;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Identifier;
import org.folio.rest.jaxrs.model.InstancePrecedingSucceedingTitle;
import org.folio.rest.jaxrs.model.InstancePrecedingSucceedingTitles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PrecedingSucceedingTitlesIT extends BaseIntegrationTest {

  private static final String TITLE = "A web primer";
  private static final String HRID = "inst000000000022";
  private static final String IDENTIFIER_TYPE_ID = "8261054f-be78-422d-bd51-4ed9f33c3422";
  private static final String INVALID_UUID_ERROR_MESSAGE = "Invalid UUID format of id, should be "
    + "xxxxxxxx-xxxx-Mxxx-Nxxx-xxxxxxxxxxxx where M is 1-5 and N is 8, 9, a, b, A or B and x is 0-9, a-f or A-F.";

  private String instanceTypeId;

  @BeforeEach
  void createFixtures() {
    instanceTypeId = createInstanceType(client);
  }

  @DisplayName("should create a connected preceding/succeeding title when both instances exist")
  @Test
  void shouldCreateConnectedTitle_whenBothInstancesExist() {
    var precedingId = createInstance(client, "Title One", instanceTypeId);
    var succeedingId = createInstance(client, "Title Two", instanceTypeId);

    var created = createTitle(precedingId, succeedingId, null, null, List.of());

    assertTitle(getTitleById(created.getString("id")), created.getString("id"), precedingId, succeedingId,
      null, null, List.of());
  }

  @DisplayName("should create an unconnected preceding title")
  @Test
  void shouldCreateUnconnectedPrecedingTitle() {
    var instanceId = createInstance(client, "Title One", instanceTypeId);
    var identifiers = List.of(identifier("9781473619777"));

    var created = createTitle(null, instanceId, TITLE, HRID, identifiers);

    assertTitle(getTitleById(created.getString("id")), created.getString("id"), null, instanceId, TITLE, HRID,
      identifiers);
  }

  @DisplayName("should create an unconnected succeeding title")
  @Test
  void shouldCreateUnconnectedSucceedingTitle() {
    var instanceId = createInstance(client, "Title One", instanceTypeId);
    var identifiers = List.of(identifier("9781473619777"));

    var created = createTitle(instanceId, null, TITLE, HRID, identifiers);

    assertTitle(getTitleById(created.getString("id")), created.getString("id"), instanceId, null, TITLE, HRID,
      identifiers);
  }

  @DisplayName("should update a connected preceding/succeeding title")
  @Test
  void shouldUpdateConnectedTitle() {
    var instance1Id = createInstance(client, "Title One", instanceTypeId);
    var instance2Id = createInstance(client, "Title Two", instanceTypeId);

    var created = createTitle(instance1Id, instance2Id, null, null, List.of());
    var id = created.getString("id");

    replaceTitle(id, instance2Id, instance1Id, null, null, List.of());

    assertTitle(getTitleById(id), id, instance2Id, instance1Id, null, null, List.of());
  }

  @DisplayName("should update an unconnected preceding/succeeding title")
  @Test
  void shouldUpdateUnconnectedTitle() {
    var instance1Id = createInstance(client, "Title One", instanceTypeId);
    var identifiers = List.of(identifier("9781473619777"));

    var created = createTitle(instance1Id, null, TITLE, HRID, identifiers);
    var id = created.getString("id");

    var instance2Id = createInstance(client, "Title Two", instanceTypeId);
    var newTitle = "New";
    var newHrid = "inst000000000133";
    var newIdentifiers = List.of(identifier("1081473619777"));

    replaceTitle(id, instance2Id, instance1Id, newTitle, newHrid, newIdentifiers);

    assertTitle(getTitleById(id), id, instance2Id, instance1Id, newTitle, newHrid, newIdentifiers);
  }

  @DisplayName("should delete a preceding/succeeding title")
  @Test
  void shouldDeleteTitle() {
    var instanceId = createInstance(client, "Title One", instanceTypeId);
    var created = createTitle(null, instanceId, TITLE, HRID, List.of(identifier("9781473619777")));
    var id = created.getString("id");

    assertEquals(HTTP_NO_CONTENT.toInt(), get(doDelete(client, titleByIdPath(id))).status());
    assertEquals(HTTP_NOT_FOUND.toInt(), get(doGet(client, titleByIdPath(id))).status());
  }

  @DisplayName("should find a preceding/succeeding title by query")
  @Test
  void shouldFindTitleByQuery() {
    var precedingId = createInstance(client, "Title One", instanceTypeId);
    var succeedingId = createInstance(client, "Title Two", instanceTypeId);
    var identifiers = List.of(identifier("9781473619777"));

    var created = createTitle(precedingId, succeedingId, TITLE, HRID, identifiers);

    var response = get(doGet(client, PRECEDING_SUCCEEDING_TITLES + "?query=succeedingInstanceId=" + succeedingId));
    assertEquals(HTTP_OK.toInt(), response.status());

    var titles = response.jsonBody().getJsonArray("precedingSucceedingTitles");
    assertEquals(1, titles.size());
    assertTitle(titles.getJsonObject(0), created.getString("id"), precedingId, succeedingId, TITLE, HRID,
      identifiers);
  }

  @DisplayName("should return 422 when the preceding instance does not exist")
  @Test
  void shouldReturn422_whenPrecedingInstanceDoesNotExist() {
    var nonExistingInstanceId = UUID.randomUUID().toString();
    var instanceId = createInstance(client, "Title One", instanceTypeId);

    var response = postTitle(nonExistingInstanceId, instanceId, null, null, List.of());

    assertEquals(HTTP_UNPROCESSABLE_ENTITY.toInt(), response.status());
    assertErrorMessage(response, "Cannot set preceding_succeeding_title.precedinginstanceid = "
      + nonExistingInstanceId + " because it does not exist in instance.id.");
  }

  @DisplayName("should return 422 when the succeeding instance does not exist")
  @Test
  void shouldReturn422_whenSucceedingInstanceDoesNotExist() {
    var nonExistingInstanceId = UUID.randomUUID().toString();
    var instanceId = createInstance(client, "Title One", instanceTypeId);

    var response = postTitle(instanceId, nonExistingInstanceId, TITLE, HRID, List.of(identifier("9781473619777")));

    assertEquals(HTTP_UNPROCESSABLE_ENTITY.toInt(), response.status());
    assertErrorMessage(response, "Cannot set preceding_succeeding_title.succeedinginstanceid = "
      + nonExistingInstanceId + " because it does not exist in instance.id.");
  }

  @DisplayName("should return 422 when both preceding and succeeding instance ids are empty")
  @Test
  void shouldReturn422_whenBothInstanceIdsAreEmpty() {
    var response = postTitle(null, null, TITLE, HRID, List.of(identifier("9781473619777")));

    assertEquals(HTTP_UNPROCESSABLE_ENTITY.toInt(), response.status());
    assertErrorMessage(response, "The precedingInstanceId and succeedingInstanceId can't be empty at the same time");
  }

  @DisplayName("should return 422 when getting by an invalid id")
  @Test
  void shouldReturn422_whenGettingByInvalidId() {
    var response = get(doGet(client, titleByIdPath("abc")));

    assertEquals(HTTP_UNPROCESSABLE_ENTITY.toInt(), response.status());
    assertErrorMessage(response, INVALID_UUID_ERROR_MESSAGE);
  }

  @DisplayName("should return 422 when updating by an invalid id")
  @Test
  void shouldReturn422_whenUpdatingByInvalidId() {
    var instanceId = createInstance(client, "Title One", instanceTypeId);
    var title = new InstancePrecedingSucceedingTitle()
      .withPrecedingInstanceId(instanceId)
      .withTitle(TITLE)
      .withHrid(HRID);

    var response = get(doPut(client, titleByIdPath("abc"), pojo2JsonObject(title)));

    assertEquals(HTTP_UNPROCESSABLE_ENTITY.toInt(), response.status());
    assertErrorMessage(response, INVALID_UUID_ERROR_MESSAGE);
  }

  @DisplayName("should return 400 when deleting by an invalid id")
  @Test
  void shouldReturn400_whenDeletingByInvalidId() {
    var response = get(doDelete(client, titleByIdPath("abc")));

    assertEquals(HTTP_BAD_REQUEST.toInt(), response.status());
    assertEquals(INVALID_UUID_ERROR_MESSAGE, response.body().toString());
  }

  @DisplayName("should update the preceding/succeeding titles connected to an instance")
  @Test
  void shouldUpdateTitleCollection_forInstance() {
    var instanceId = createInstance(client, "Title One", instanceTypeId);

    var title1Id = createTitle(instanceId, null, null, null, List.of()).getString("id");
    var title2Id = createTitle(instanceId, null, null, null, List.of()).getString("id");

    var updated = new InstancePrecedingSucceedingTitles()
      .withPrecedingSucceedingTitles(List.of(
        new InstancePrecedingSucceedingTitle().withId(title1Id).withSucceedingInstanceId(instanceId),
        new InstancePrecedingSucceedingTitle().withId(title2Id).withSucceedingInstanceId(instanceId)))
      .withTotalRecords(2);

    var response = get(doPut(client, PRECEDING_SUCCEEDING_TITLES + "/instances/" + instanceId,
      pojo2JsonObject(updated)));
    assertEquals(HTTP_NO_CONTENT.toInt(), response.status());

    var found = get(doGet(client, PRECEDING_SUCCEEDING_TITLES
      + "?query=succeedingInstanceId==(" + instanceId + ")+or+precedingInstanceId==(" + instanceId + ")"))
      .jsonBody().getJsonArray("precedingSucceedingTitles");

    assertEquals(2, found.size());
    found.forEach(entry -> {
      var json = (JsonObject) entry;
      assertEquals(instanceId, json.getString("succeedingInstanceId"));
      assertNull(json.getString("precedingInstanceId"));
    });
  }

  @DisplayName("should return 404 when updating titles for an instance that does not exist")
  @Test
  void shouldReturn404_whenUpdatingTitlesForMissingInstance() {
    var missingInstanceId = UUID.randomUUID().toString();
    var updated = new InstancePrecedingSucceedingTitles()
      .withPrecedingSucceedingTitles(List.of(
        new InstancePrecedingSucceedingTitle().withPrecedingInstanceId(missingInstanceId),
        new InstancePrecedingSucceedingTitle().withSucceedingInstanceId(missingInstanceId)))
      .withTotalRecords(2);

    var response = get(doPut(client, PRECEDING_SUCCEEDING_TITLES + "/instances/" + missingInstanceId,
      pojo2JsonObject(updated)));

    assertEquals(HTTP_NOT_FOUND.toInt(), response.status());
    assertEquals("Instance not found", response.body().toString());
  }

  @DisplayName("should return 422 when a title in the collection is missing the instance id")
  @Test
  void shouldReturn422_whenTitleMissingInstanceId() {
    var instanceId = UUID.randomUUID().toString();
    var updated = new InstancePrecedingSucceedingTitles()
      .withPrecedingSucceedingTitles(List.of(new InstancePrecedingSucceedingTitle()))
      .withTotalRecords(1);

    var response = get(doPut(client, PRECEDING_SUCCEEDING_TITLES + "/instances/" + instanceId,
      pojo2JsonObject(updated)));

    assertEquals(HTTP_UNPROCESSABLE_ENTITY.toInt(), response.status());
    assertTrue(response.body().toString()
      .contains("The precedingInstanceId or succeedingInstanceId should contain instanceId"));
  }

  private JsonObject createTitle(String precedingId, String succeedingId, String title, String hrid,
                                  List<Identifier> identifiers) {
    var response = postTitle(precedingId, succeedingId, title, hrid, identifiers);
    assertEquals(HTTP_CREATED.toInt(), response.status());
    return response.jsonBody();
  }

  private TestResponse postTitle(String precedingId, String succeedingId, String title, String hrid,
                                  List<Identifier> identifiers) {
    var request = new InstancePrecedingSucceedingTitle()
      .withPrecedingInstanceId(precedingId)
      .withSucceedingInstanceId(succeedingId)
      .withTitle(title)
      .withHrid(hrid)
      .withIdentifiers(identifiers);
    return get(doPost(client, PRECEDING_SUCCEEDING_TITLES, pojo2JsonObject(request)));
  }

  private void replaceTitle(String id, String precedingId, String succeedingId, String title, String hrid,
                             List<Identifier> identifiers) {
    var request = new InstancePrecedingSucceedingTitle()
      .withPrecedingInstanceId(precedingId)
      .withSucceedingInstanceId(succeedingId)
      .withTitle(title)
      .withHrid(hrid)
      .withIdentifiers(identifiers);

    assertEquals(HTTP_NO_CONTENT.toInt(), get(doPut(client, titleByIdPath(id), pojo2JsonObject(request))).status());
  }

  private JsonObject getTitleById(String id) {
    return get(doGet(client, titleByIdPath(id))).jsonBody();
  }

  private String titleByIdPath(String id) {
    return PRECEDING_SUCCEEDING_TITLES + "/" + id;
  }

  private Identifier identifier(String value) {
    return new Identifier()
      .withIdentifierTypeId(IDENTIFIER_TYPE_ID)
      .withValue(value);
  }

  private void assertTitle(JsonObject actual, String id, String precedingId, String succeedingId, String title,
                            String hrid, List<Identifier> identifiers) {
    assertEquals(id, actual.getString("id"));
    assertEquals(precedingId, actual.getString("precedingInstanceId"));
    assertEquals(succeedingId, actual.getString("succeedingInstanceId"));
    assertEquals(title, actual.getString("title"));
    assertEquals(hrid, actual.getString("hrid"));

    var expectedIdentifiers = new JsonArray(identifiers.stream()
      .map(BaseIntegrationTest::pojo2JsonObject)
      .toList());
    assertEquals(expectedIdentifiers, actual.getJsonArray("identifiers"));
  }

  private void assertErrorMessage(TestResponse response, String message) {
    var errors = response.bodyAsClass(Errors.class);
    assertEquals(message, errors.getErrors().getFirst().getMessage());
  }
}
