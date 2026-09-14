package org.folio.it.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.HttpStatus.SC_BAD_REQUEST;
import static org.folio.HttpStatus.SC_NO_CONTENT;
import static org.folio.HttpStatus.SC_OK;
import static org.folio.HttpStatus.SC_UNPROCESSABLE_CONTENT;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;
import static org.folio.it.HoldingsStorageFixtures.createHoldingsRecordsSource;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.UUID;
import org.folio.support.ResourcePaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HoldingsStorageCrudIT extends HoldingsStorageTestBase {

  @Test
  @DisplayName("should create a holding with all fields populated")
  void shouldCreateHolding_withAllFieldsPopulated() {
    var instanceId = createInstanceRecord();
    var holdingId = UUID.randomUUID();
    var adminNote = "An admin note";
    var request = holdingRequest(instanceId).withId(holdingId)
      .withTags(tags(TAG_VALUE)).create().put(ADMINISTRATIVE_NOTES_KEY, new JsonArray().add(adminNote));

    var holding = createHolding(request);

    assertThat(holding.getString("id")).isEqualTo(holdingId.toString());
    assertThat(holding.getString("instanceId")).isEqualTo(instanceId);
    assertThat(holding.getString(PERMANENT_LOCATION_ID_KEY)).isEqualTo(mainLibraryLocationId);
    assertThat(holding.getString("hrid")).isEqualTo("ho00000000001");
    assertThat(holding.getString(EFFECTIVE_LOCATION_ID_KEY)).isEqualTo(mainLibraryLocationId);
    assertThat(holding.getJsonArray(ADMINISTRATIVE_NOTES_KEY)).contains(adminNote);

    var holdingFromGet = getHoldingById(holdingId).jsonBody();
    assertThat(holdingFromGet.getString("id")).isEqualTo(holdingId.toString());
    assertThat(getTags(holdingFromGet)).containsExactly(TAG_VALUE);
  }

  @Test
  @DisplayName("should create a holding without providing an id")
  void shouldCreateHolding_withoutProvidingId() {
    var instanceId = createInstanceRecord();
    var request = holdingRequest(instanceId).withTags(tags(TAG_VALUE)).create();

    var holding = createHolding(request);

    assertThat(holding.getString("id")).isNotNull();
    assertThat(holding.getString("instanceId")).isEqualTo(instanceId);
    assertThat(holding.getString(PERMANENT_LOCATION_ID_KEY)).isEqualTo(mainLibraryLocationId);
    assertThat(getTags(holding)).containsExactly(TAG_VALUE);
  }

  @Test
  @DisplayName("should create a holding at a specific location")
  void shouldCreateHolding_atSpecificLocation() {
    var instanceId = createInstanceRecord();
    var holdingId = UUID.randomUUID();

    var holding = createHolding(holdingRequest(instanceId).withId(holdingId).withTags(tags(TAG_VALUE)));

    assertThat(holding.getString("instanceId")).isEqualTo(instanceId);
    assertThat(holding.getString(PERMANENT_LOCATION_ID_KEY)).isEqualTo(mainLibraryLocationId);
    assertThat(holding.getString("hrid")).isEqualTo("ho00000000001");
    assertThat(getTags(holding)).containsExactly(TAG_VALUE);
  }

  @Test
  @DisplayName("should replace a holding at a specific location")
  void shouldReplaceHolding_atSpecificLocation() {
    var instanceId = createInstanceRecord();
    var adminNote = "an admin note";
    var newSourceId = createHoldingsRecordsSource(client);
    var holding = createHolding(holdingRequest(instanceId).withTags(tags(TAG_VALUE)));
    var holdingId = holding.getString("id");

    var replacement = holding.copy()
      .put(PERMANENT_LOCATION_ID_KEY, annexLibraryLocationId)
      .put("sourceId", newSourceId)
      .put("tags", new JsonObject().put("tagList", new JsonArray().add(NEW_TAG_VALUE)))
      .put(ADMINISTRATIVE_NOTES_KEY, new JsonArray().add(adminNote));

    assertThat(updateHolding(replacement).status()).isEqualTo(SC_NO_CONTENT);

    var updated = getHoldingById(holdingId).jsonBody();
    assertThat(updated.getString("sourceId")).isEqualTo(newSourceId);
    assertThat(updated.getString(PERMANENT_LOCATION_ID_KEY)).isEqualTo(annexLibraryLocationId);
    assertThat(updated.getJsonArray(ADMINISTRATIVE_NOTES_KEY)).contains(adminNote);
    assertThat(getTags(updated)).containsExactly(NEW_TAG_VALUE);
  }

  @Test
  @DisplayName("should move a holding to a new instance")
  void shouldMoveHolding_toNewInstance() {
    var instanceId = createInstanceRecord();
    var newInstanceId = createInstanceRecord();
    var holding = createHolding(holdingRequest(instanceId));
    var holdingId = holding.getString("id");

    var replacement = holding.copy().put("instanceId", newInstanceId);
    assertThat(updateHolding(replacement).status()).isEqualTo(SC_NO_CONTENT);

    var updated = getHoldingById(holdingId).jsonBody();
    assertThat(updated.getString("instanceId")).isEqualTo(newInstanceId);
  }

  @Test
  @DisplayName("should return 422 when the instance id does not exist")
  void shouldReturn422_whenInstanceIdDoesNotExist() {
    var instanceId = UUID.randomUUID().toString();
    var request = holdingRequest(instanceId).create();

    var response = await(doPost(client, ResourcePaths.HOLDINGS, request));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
    assertThat(response.body().toString()).contains(
      "Cannot set holdings_record.instanceid = " + instanceId + " because it does not exist in instance.id.");
  }

  @Test
  @DisplayName("should delete a holding")
  void shouldDeleteHolding() {
    var instanceId = createInstanceRecord();
    var holding = createHolding(holdingRequest(instanceId));
    var holdingId = holding.getString("id");

    assertThat(await(doDelete(client, ResourcePaths.HOLDINGS + "/" + holdingId)).status()).isEqualTo(SC_NO_CONTENT);

    assertGetNotFound(ResourcePaths.HOLDINGS + "/" + holdingId);
  }

  @Test
  @DisplayName("should get all holdings")
  void shouldGetAllHoldings() {
    createThreeHoldings();

    var response = await(doGet(client, ResourcePaths.HOLDINGS)).jsonBody();

    assertThat(response.getJsonArray(HOLDINGS_RECORDS_KEY)).hasSize(3);
    assertThat(response.getInteger(TOTAL_RECORDS_KEY)).isEqualTo(3);
  }

  @Test
  @DisplayName("should retrieve all holdings via the retrieve endpoint")
  void shouldRetrieveAllHoldings() {
    createThreeHoldings();

    var response = await(doPost(client, ResourcePaths.HOLDINGS_RETRIEVE, new JsonObject())).jsonBody();

    assertThat(response.getJsonArray(HOLDINGS_RECORDS_KEY)).hasSize(3);
    assertThat(response.getInteger(TOTAL_RECORDS_KEY)).isEqualTo(3);
  }

  @Test
  @DisplayName("should return 400 when the limit is negative")
  void shouldReturn400_whenLimitIsNegative() {
    createHolding(holdingRequest(createInstanceRecord()));

    var response = await(doGet(client, ResourcePaths.HOLDINGS + "?limit=-3"));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString().trim())
      .isEqualTo("'limit' parameter is incorrect. parameter value {-3} is not valid: "
                 + "must be greater than or equal to 0");
  }

  @Test
  @DisplayName("should return 400 when the offset is negative")
  void shouldReturn400_whenOffsetIsNegative() {
    createHolding(holdingRequest(createInstanceRecord()));

    var response = await(doGet(client, ResourcePaths.HOLDINGS + "?offset=-3"));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString().trim())
      .isEqualTo("'offset' parameter is incorrect. parameter value {-3} is not valid: "
                 + "must be greater than or equal to 0");
  }

  @Test
  @DisplayName("should page through all holdings")
  void shouldPageThroughAllHoldings() {
    var instanceId = createInstanceRecord();
    for (int i = 0; i < 5; i++) {
      createHolding(holdingRequest(instanceId));
    }

    var firstPage = await(doGet(client, ResourcePaths.HOLDINGS + "?limit=3")).jsonBody();
    var secondPage = await(doGet(client, ResourcePaths.HOLDINGS + "?limit=3&offset=3")).jsonBody();

    assertThat(firstPage.getJsonArray(HOLDINGS_RECORDS_KEY)).hasSize(3);
    assertThat(firstPage.getInteger(TOTAL_RECORDS_KEY)).isEqualTo(5);
    assertThat(secondPage.getJsonArray(HOLDINGS_RECORDS_KEY)).hasSize(2);
    assertThat(secondPage.getInteger(TOTAL_RECORDS_KEY)).isEqualTo(5);
  }

  @Test
  @DisplayName("should delete all holdings")
  void shouldDeleteAllHoldings() {
    createThreeHoldings();

    assertThat(await(doDelete(client, ResourcePaths.HOLDINGS + "?query=cql.allRecords=1")).status())
      .isEqualTo(SC_NO_CONTENT);

    var response = await(doGet(client, ResourcePaths.HOLDINGS)).jsonBody();
    assertThat(response.getJsonArray(HOLDINGS_RECORDS_KEY)).isEmpty();
    assertThat(response.getInteger(TOTAL_RECORDS_KEY)).isZero();
  }

  @Test
  @DisplayName("should delete holdings matching a CQL query")
  void shouldDeleteHoldings_matchingCqlQuery() {
    var instanceId = createInstanceRecord();
    final var keep1 = createHolding(withHrid(holdingRequest(instanceId), "2123"));
    var delete1 = createHolding(withHrid(holdingRequest(instanceId), "1234"));
    final var keep2 = createHolding(withHrid(holdingRequest(instanceId), "345 12"));
    var delete2 = createHolding(withHrid(holdingRequest(instanceId), "123"));

    var response = await(doDelete(client, ResourcePaths.HOLDINGS + "?query=hrid==12*"));

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
    assertGetNotFound(ResourcePaths.HOLDINGS + "/" + delete1.getString("id"));
    assertGetNotFound(ResourcePaths.HOLDINGS + "/" + delete2.getString("id"));
    assertThat(getHoldingById(keep1.getString("id")).status()).isEqualTo(SC_OK);
    assertThat(getHoldingById(keep2.getString("id")).status()).isEqualTo(SC_OK);
  }

  @Test
  @DisplayName("should return 400 when deleting holdings with an empty CQL query")
  void shouldReturn400_whenDeletingHoldingsWithEmptyCql() {
    var response = await(doDelete(client, ResourcePaths.HOLDINGS + "?query="));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString()).contains("empty");
  }

  @Test
  @DisplayName("should return 400 when the tenant is missing when creating a holding")
  void shouldReturn400_whenTenantMissingForCreatingHolding() {
    var request = holdingRequest(createInstanceRecord()).create();

    var response = await(doPost(client, ResourcePaths.HOLDINGS, null, request));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body()).hasToString("Unable to process request Tenant must be set");
  }

  @Test
  @DisplayName("should return 400 when the tenant is missing when getting a holding")
  void shouldReturn400_whenTenantMissingForGettingHolding() {
    var response = await(doGet(client, ResourcePaths.HOLDINGS + "/" + UUID.randomUUID(), null));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body()).hasToString("Unable to process request Tenant must be set");
  }

  @Test
  @DisplayName("should return 400 when the tenant is missing when getting all holdings")
  void shouldReturn400_whenTenantMissingForGettingAllHoldings() {
    var response = await(doGet(client, ResourcePaths.HOLDINGS, null));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body()).hasToString("Unable to process request Tenant must be set");
  }

  @ValueSource(strings = {"holdingsStatements", "holdingsStatementsForIndexes", "holdingsStatementsForSupplements"})
  @ParameterizedTest(name = "should set {0} with notes")
  void shouldSetHoldingStatement_withNotes(String holdingsStatementsField) {
    var statement = new JsonObject()
      .put("statement", "Test statement")
      .put("note", "Test note")
      .put("staffNote", "Test staff note");

    var holding = createHolding(holdingRequest(createInstanceRecord())
      .create().put(holdingsStatementsField, new JsonArray().add(statement)));

    var holdingStatement = holding.getJsonArray(holdingsStatementsField).getJsonObject(0);
    assertThat(holdingStatement.getString("statement")).isEqualTo("Test statement");
    assertThat(holdingStatement.getString("note")).isEqualTo("Test note");
    assertThat(holdingStatement.getString("staffNote")).isEqualTo("Test staff note");
  }

  @Test
  @DisplayName("should return 400 when deleting a holdings source still attached to a holding")
  void shouldReturn400_whenDeletingHoldingsSourceStillAttached() {
    var instanceId = createInstanceRecord();
    var sourceId = createHoldingsRecordsSource(client);
    createHolding(holdingRequest(instanceId).withSource(UUID.fromString(sourceId)));

    var response = await(doDelete(client, ResourcePaths.HOLDINGS_SOURCES + "/" + sourceId));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
  }

  @Test
  @DisplayName("should patch a holdings record")
  void shouldPatchHoldingsRecord() {
    var holding = createHolding(withHrid(holdingRequest(createInstanceRecord()), "hrid"));
    var holdingId = holding.getString("id");

    var patch = new JsonObject().put("id", holdingId).put("_version", 1)
      .put(ADMINISTRATIVE_NOTES_KEY, new JsonArray().add("new note"))
      .put(PERMANENT_LOCATION_ID_KEY, annexLibraryLocationId);

    assertThat(patchHolding(holdingId, patch).status()).isEqualTo(SC_NO_CONTENT);

    var updated = getHoldingById(holdingId).jsonBody();
    assertThat(updated.getJsonArray(ADMINISTRATIVE_NOTES_KEY)).containsExactly("new note");
    assertThat(updated.getString(PERMANENT_LOCATION_ID_KEY)).isEqualTo(annexLibraryLocationId);
  }

  @Test
  @DisplayName("should return 422 when patching a holdings record with a required field set to null")
  void shouldReturn422_whenPatchingWithRequiredFieldNull() {
    var holding = createHolding(withHrid(holdingRequest(createInstanceRecord()), "hrid"));
    var holdingId = holding.getString("id");

    var patch = new JsonObject().put("id", holdingId).put("_version", 1).putNull("sourceId");

    assertThat(patchHolding(holdingId, patch).status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
  }
}
