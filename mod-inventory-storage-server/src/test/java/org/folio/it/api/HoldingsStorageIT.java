package org.folio.it.api;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_CONFLICT;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_REQUEST_TOO_LONG;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;
import static org.folio.it.HoldingsStorageFixtures.createCallNumberType;
import static org.folio.it.HoldingsStorageFixtures.createHoldingsRecordsSource;
import static org.folio.it.InstanceStorageFixtures.createInstance;
import static org.folio.it.InstanceStorageFixtures.createInstanceType;
import static org.folio.it.LocationStorageFixtures.createLocation;
import static org.folio.validator.NotesValidators.MAX_NOTE_LENGTH;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.folio.it.BaseIntegrationTest;
import org.folio.rest.jaxrs.model.EffectiveCallNumberComponents;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.HoldingsNote;
import org.folio.rest.tools.utils.OptimisticLockingUtil;
import org.folio.support.ResourcePaths;
import org.folio.support.builders.HoldingRequestBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HoldingsStorageIT extends BaseIntegrationTest {

  private static final String TAG_VALUE = "test-tag";
  private static final String NEW_TAG_VALUE = "new test tag";
  private static final String STATISTICAL_CODE_IDS_KEY = "statisticalCodeIds";
  private static final String ADMINISTRATIVE_NOTES_KEY = "administrativeNotes";
  private static final String PERMANENT_LOCATION_ID_KEY = "permanentLocationId";
  private static final String EFFECTIVE_LOCATION_ID_KEY = "effectiveLocationId";
  private static final String HOLDINGS_RECORDS_KEY = "holdingsRecords";
  private static final String TOTAL_RECORDS_KEY = "totalRecords";
  private static final String INVALID_VALUE = "invalid value";
  private static final String INVALID_TYPE_ERROR_MESSAGE =
    "invalid input syntax for type uuid: \"" + INVALID_VALUE + "\"";

  // The exact reference-data id CallNumberUtils keys its LC shelf-key parsing off; cannot be
  // a fresh/random id (see HoldingsStorageFixtures.createCallNumberType).
  private static final String LC_CALL_NUMBER_TYPE_ID = "95467209-6d7b-468b-94df-0f5d7ad2747d";

  private static String instanceTypeId;
  private static String mainLibraryLocationId;
  private static String annexLibraryLocationId;
  private static String lcCallNumberTypeId;

  @BeforeAll
  static void seedReferenceData() {
    instanceTypeId = createInstanceType(client);
    mainLibraryLocationId = createLocation(client);
    annexLibraryLocationId = createLocation(client);
    lcCallNumberTypeId = createCallNumberType(client, LC_CALL_NUMBER_TYPE_ID, "Library of Congress classification");
  }

  @BeforeEach
  void clearHoldings() {
    runQuery("TRUNCATE TABLE instance, holdings_record, item CASCADE");
  }

  @AfterEach
  void resetHridSequenceAndOptimisticLockingOverride() {
    setHoldingsSequence(1);
    // this is process-wide, static RMB config (shared across every *IT class in the JVM) - reset
    // it after every test so a test enabling it doesn't leak into unrelated later tests
    OptimisticLockingUtil.configureAllowSuppressOptimisticLocking(Map.of());
  }

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
  @DisplayName("should return 422 when the holding id is not a UUID")
  void shouldReturn422_whenHoldingIdIsNotUuid() {
    var instanceId = createInstanceRecord();
    var request = holdingRequest(instanceId).create().put("id", "6556456");

    var response = await(doPost(client, ResourcePaths.HOLDINGS, request));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    var error = response.jsonBody().mapTo(Errors.class).getErrors().getFirst();
    assertThat(error.getMessage()).contains("must match");
    assertThat(error.getParameters().getFirst().getKey()).isEqualTo("id");
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
  @DisplayName("should return 400 when a statistical code id is invalid on create")
  void shouldReturn400_whenStatisticalCodeIdIsInvalidOnCreate() {
    var instanceId = createInstanceRecord();
    var request = holdingRequest(instanceId).create().put(STATISTICAL_CODE_IDS_KEY, Set.of(INVALID_VALUE));

    var response = await(doPost(client, ResourcePaths.HOLDINGS, request));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString()).contains(INVALID_TYPE_ERROR_MESSAGE);
  }

  @Test
  @DisplayName("should return 400 when a statistical code id is invalid on update")
  void shouldReturn400_whenStatisticalCodeIdIsInvalidOnUpdate() {
    var instanceId = createInstanceRecord();
    var holding = createHolding(holdingRequest(instanceId));
    holding.put(STATISTICAL_CODE_IDS_KEY, Set.of(INVALID_VALUE));

    var response = updateHolding(holding);

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString()).contains(INVALID_TYPE_ERROR_MESSAGE);
  }

  @Test
  @DisplayName("should return 422 when the instance id does not exist")
  void shouldReturn422_whenInstanceIdDoesNotExist() {
    var instanceId = UUID.randomUUID().toString();
    var request = holdingRequest(instanceId).create();

    var response = await(doPost(client, ResourcePaths.HOLDINGS, request));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
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

  @Test
  @DisplayName("should enforce optimistic locking on holding version")
  void shouldEnforceOptimisticLocking_onHoldingVersion() {
    var holding = createHolding(holdingRequest(createInstanceRecord()));
    holding.put(PERMANENT_LOCATION_ID_KEY, annexLibraryLocationId);
    // updating with current _version 1 succeeds and increments _version to 2
    assertThat(updateHolding(holding).status()).isEqualTo(SC_NO_CONTENT);
    holding.put(PERMANENT_LOCATION_ID_KEY, mainLibraryLocationId);
    // updating with outdated _version 1 fails, current _version is 2
    assertThat(updateHolding(holding).status()).isEqualTo(SC_CONFLICT);
    // updating with _version -1 should fail, single holding PUT never allows suppressing optimistic locking
    holding.put("_version", -1);
    assertThat(updateHolding(holding).status()).isEqualTo(SC_CONFLICT);
    // this allow should not apply to single holding PUT, only to batch unsafe
    OptimisticLockingUtil.configureAllowSuppressOptimisticLocking(
      Map.of(OptimisticLockingUtil.DB_ALLOW_SUPPRESS_OPTIMISTIC_LOCKING, "9999-12-31T23:59:59Z"));
    assertThat(updateHolding(holding).status()).isEqualTo(SC_CONFLICT);
  }

  @Test
  @DisplayName("should not update a holding when there are no changes and optimize-updates is enabled")
  void shouldNotUpdateHolding_whenNoChangesAndOptimizeUpdatesEnabled() {
    assertThat(updateOptimizeUpdatesSetting(true).status()).isEqualTo(SC_NO_CONTENT);
    var holding = createHolding(holdingRequest(createInstanceRecord()));

    assertThat(updateHolding(holding).status()).isEqualTo(SC_NO_CONTENT);

    var updated = getHoldingById(holding.getString("id")).jsonBody();
    assertThat(updated.getString("_version")).isEqualTo("1");
  }

  @Test
  @DisplayName("should update a holding when there are no changes and optimize-updates is disabled")
  void shouldUpdateHolding_whenNoChangesAndOptimizeUpdatesDisabled() {
    assertThat(updateOptimizeUpdatesSetting(false).status()).isEqualTo(SC_NO_CONTENT);
    var holding = createHolding(holdingRequest(createInstanceRecord()));

    assertThat(updateHolding(holding).status()).isEqualTo(SC_NO_CONTENT);

    var updated = getHoldingById(holding.getString("id")).jsonBody();
    assertThat(updated.getString("_version")).isEqualTo("2");
  }

  @Test
  @DisplayName("should update a holding when a new source id is provided")
  void shouldUpdateHolding_whenSourceIdProvided() {
    var instanceId = createInstanceRecord();
    var holding = createHolding(holdingRequest(instanceId).withCallNumber("testCallNumber"));
    var newSourceId = createHoldingsRecordsSource(client);

    holding.put("sourceId", newSourceId).put("callNumber", "updatedTestCallNumber");
    assertThat(updateHolding(holding).status()).isEqualTo(SC_NO_CONTENT);

    var updated = getHoldingById(holding.getString("id")).jsonBody();
    assertThat(updated.getString("callNumber")).isEqualTo("updatedTestCallNumber");
  }

  @Test
  @DisplayName("should return 422 when the source id is removed")
  void shouldReturn422_whenSourceIdRemoved() {
    var instanceId = createInstanceRecord();
    var holding = createHolding(holdingRequest(instanceId).withCallNumber("testCallNumber"));

    holding.putNull("sourceId").put("callNumber", "updatedTestCallNumber");
    var response = updateHolding(holding);

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    var updated = getHoldingById(holding.getString("id")).jsonBody();
    assertThat(updated.getString("callNumber")).isEqualTo("testCallNumber");
  }

  @Test
  @DisplayName("should return 422 when creating a holding whose administrative note exceeds the maximum length")
  void shouldReturn422_whenCreatingHoldingWithLongAdministrativeNote() {
    var request = holdingRequest(createInstanceRecord()).create()
      .put(ADMINISTRATIVE_NOTES_KEY, new JsonArray().add("x".repeat(MAX_NOTE_LENGTH + 1)));

    var response = await(doPost(client, ResourcePaths.HOLDINGS, request));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  @DisplayName("should return 422 when creating a holding whose note exceeds the maximum length")
  void shouldReturn422_whenCreatingHoldingWithLongNote() {
    var request = holdingRequest(createInstanceRecord()).create()
      .put("notes", new JsonArray().add(pojo2JsonObject(new HoldingsNote().withNote("x".repeat(MAX_NOTE_LENGTH + 1)))));

    var response = await(doPost(client, ResourcePaths.HOLDINGS, request));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  @DisplayName("should return 422 when updating a holding's administrative note to exceed the maximum length")
  void shouldReturn422_whenUpdatingHoldingWithLongAdministrativeNote() {
    var holding = createHolding(holdingRequest(createInstanceRecord()));
    holding.put(ADMINISTRATIVE_NOTES_KEY, new JsonArray().add("x".repeat(MAX_NOTE_LENGTH + 1)));

    assertThat(updateHolding(holding).status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  @DisplayName("should return 422 when updating a holding's note to exceed the maximum length")
  void shouldReturn422_whenUpdatingHoldingWithLongNote() {
    var holding = createHolding(holdingRequest(createInstanceRecord()));
    holding.put("notes", new JsonArray()
      .add(pojo2JsonObject(new HoldingsNote().withNote("x".repeat(MAX_NOTE_LENGTH + 1)))));

    assertThat(updateHolding(holding).status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  @DisplayName("should return 422 when creating a holding without a permanent location")
  void shouldReturn422_whenCreatingWithoutPermanentLocation() {
    var request = holdingRequest(createInstanceRecord()).create();
    request.remove(PERMANENT_LOCATION_ID_KEY);

    var response = await(doPost(client, ResourcePaths.HOLDINGS, request));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    var error = response.jsonBody().mapTo(Errors.class).getErrors().getFirst();
    assertThat(error.getMessage()).containsAnyOf("may not be null", "must not be null");
    assertThat(error.getParameters().getFirst().getKey()).isEqualTo(PERMANENT_LOCATION_ID_KEY);
  }

  @Test
  @DisplayName("should create a holding when a hrid is supplied")
  void shouldCreateHolding_whenHridIsSupplied() {
    var holding = createHolding(withHrid(holdingRequest(createInstanceRecord()), "TEST1001"));

    assertThat(holding.getString("hrid")).isEqualTo("TEST1001");
    assertThat(getHoldingById(holding.getString("id")).jsonBody().getString("hrid")).isEqualTo("TEST1001");
  }

  @Test
  @DisplayName("should return 422 when creating a holding with a duplicate hrid")
  void shouldReturn422_whenCreatingHoldingWithDuplicateHrid() {
    var instanceId = createInstanceRecord();
    var holding = createHolding(withHrid(holdingRequest(instanceId), "ho00000000001"));
    assertThat(holding.getString("hrid")).isEqualTo("ho00000000001");

    var duplicate = withHrid(holdingRequest(instanceId), "ho00000000001").create();
    var response = await(doPost(client, ResourcePaths.HOLDINGS, duplicate));

    assertHridError(response, "ho00000000001");
  }

  @Test
  @DisplayName("should return 500 when hrid generation fails because the sequence is exhausted")
  void shouldReturn500_whenHridGenerationFails() {
    var instanceId = createInstanceRecord();
    setHoldingsSequence(99_999_999_999L);
    var holding = createHolding(holdingRequest(instanceId));
    assertThat(holding.getString("hrid")).isEqualTo("ho99999999999");

    var response = await(doPost(client, ResourcePaths.HOLDINGS, holdingRequest(instanceId).create()));

    assertThat(response.status()).isEqualTo(SC_INTERNAL_SERVER_ERROR);
    assertThat(response.body().toString()).contains("hrid_holdings_seq");
  }

  @Test
  @DisplayName("should return 422 when the hrid is already allocated")
  void shouldReturn422_whenHridAlreadyAllocated() {
    var instanceId = createInstanceRecord();
    setHoldingsSequence(1000L);
    var request = holdingRequest(instanceId).create();
    var first = createHolding(request);
    assertThat(first.getString("hrid")).isEqualTo("ho00000001000");

    setHoldingsSequence(1000L);
    var response = await(doPost(client, ResourcePaths.HOLDINGS, holdingRequest(instanceId).create()));

    assertHridError(response, "ho00000001000");
  }

  @Test
  @DisplayName("should return 400 when changing the hrid after creation")
  void shouldReturn400_whenChangingHridAfterCreation() {
    var holding = createHolding(holdingRequest(createInstanceRecord()));
    assertThat(holding.getString("hrid")).isEqualTo("ho00000000001");
    holding.put("hrid", "ABC123");

    var response = updateHolding(holding);

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body()).hasToString("The hrid field cannot be changed: new=ABC123, old=ho00000000001");
  }

  @Test
  @DisplayName("should return 400 when removing the hrid after creation")
  void shouldReturn400_whenRemovingHridAfterCreation() {
    var holding = createHolding(holdingRequest(createInstanceRecord()));
    assertThat(holding.getString("hrid")).isEqualTo("ho00000000001");
    holding.remove("hrid");

    var response = updateHolding(holding);

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body()).hasToString("The hrid field cannot be changed: new=null, old=ho00000000001");
  }

  @Test
  @DisplayName("should create a holding via PUT when a hrid is supplied")
  void shouldCreateHolding_viaPutWhenHridSupplied() {
    var holdingId = UUID.randomUUID();
    var request = withHrid(holdingRequest(createInstanceRecord()), "TEST1001").withId(holdingId).create();

    assertThat(updateHolding(request).status()).isEqualTo(SC_NO_CONTENT);

    var holding = getHoldingById(holdingId).jsonBody();
    assertThat(holding.getString("hrid")).isEqualTo("TEST1001");
  }

  @Test
  @DisplayName("should generate hrids for a synchronous batch")
  void shouldGenerateHrids_forSynchronousBatch() {
    setHoldingsSequence(1);
    var holdingsArray = threeHoldingsRequest();

    assertThat(syncBatch(holdingsArray).status()).isEqualTo(SC_CREATED);

    holdingsArray.stream().map(JsonObject.class::cast).forEach(request -> {
      var holding = getHoldingById(request.getString("id")).jsonBody();
      assertThat(holding.getString("hrid")).isBetween("ho00000000001", "ho00000000003");
      assertThat(holding.getString(EFFECTIVE_LOCATION_ID_KEY)).isEqualTo(holding.getString(PERMANENT_LOCATION_ID_KEY));
    });
  }

  @Test
  @DisplayName("should generate hrids for the holdings missing one in a synchronous batch")
  void shouldGenerateHrids_forHoldingsMissingOneInBatch() {
    setHoldingsSequence(1);
    var hrid = "ABC123";
    var holdingsArray = threeHoldingsRequest();
    holdingsArray.getJsonObject(1).put("hrid", hrid);

    assertThat(syncBatch(holdingsArray).status()).isEqualTo(SC_CREATED);

    var first = getHoldingById(holdingsArray.getJsonObject(0).getString("id")).jsonBody();
    assertThat(first.getString("hrid")).isBetween("ho00000000001", "ho00000000002");
    var second = getHoldingById(holdingsArray.getJsonObject(1).getString("id")).jsonBody();
    assertThat(second.getString("hrid")).isEqualTo(hrid);
    var third = getHoldingById(holdingsArray.getJsonObject(2).getString("id")).jsonBody();
    assertThat(third.getString("hrid")).isBetween("ho00000000001", "ho00000000002");
  }

  @Test
  @DisplayName("should return 422 when a synchronous batch has duplicate hrids")
  void shouldReturn422_whenBatchHasDuplicateHrids() {
    setHoldingsSequence(1);
    var duplicateHrid = "ho00000000001";
    var holdingsArray = threeHoldingsRequest();
    holdingsArray.getJsonObject(1).put("hrid", duplicateHrid);

    var response = syncBatch(holdingsArray);

    assertHridError(response, duplicateHrid);
    holdingsArray.forEach(holding ->
      assertGetNotFound(ResourcePaths.HOLDINGS + "/" + ((JsonObject) holding).getString("id")));
  }

  @Test
  @DisplayName("should return 500 when hrid generation fails for a synchronous batch")
  void shouldReturn500_whenBatchHridGenerationFails() {
    setHoldingsSequence(99_999_999_999L);
    var holdingsArray = threeHoldingsRequest();

    var response = syncBatch(holdingsArray);

    assertThat(response.status()).isEqualTo(SC_INTERNAL_SERVER_ERROR);
    assertThat(response.body().toString()).contains("hrid_holdings_seq");
    holdingsArray.forEach(holding ->
      assertGetNotFound(ResourcePaths.HOLDINGS + "/" + ((JsonObject) holding).getString("id")));
  }

  @Test
  @DisplayName("should return 413 when an unsafe synchronous batch is not allowed")
  void shouldReturn413_whenUnsafeBatchNotAllowed() {
    // not allowed because DB_ALLOW_SUPPRESS_OPTIMISTIC_LOCKING is not configured
    var response = syncBatchUnsafe(threeHoldingsRequest());

    assertThat(response.status()).isEqualTo(SC_REQUEST_TOO_LONG);
  }

  @Test
  @DisplayName("should allow an unsafe synchronous batch update when configured")
  void shouldAllowUnsafeBatchUpdate_whenConfigured() {
    OptimisticLockingUtil.configureAllowSuppressOptimisticLocking(
      Map.of(OptimisticLockingUtil.DB_ALLOW_SUPPRESS_OPTIMISTIC_LOCKING, "9999-12-31T23:59:59Z"));
    var holdingsArray = threeHoldingsRequest();
    assertThat(syncBatchUnsafe(holdingsArray).status()).isEqualTo(SC_CREATED);

    holdingsArray.getJsonObject(1).put("copyNumber", "456");
    assertThat(syncBatchUnsafe(holdingsArray).status()).isEqualTo(SC_CREATED);

    // safe update, env var should not influence the regular API
    holdingsArray.getJsonObject(1).put("copyNumber", "789");
    assertThat(syncBatch("?upsert=true", holdingsArray).status()).isEqualTo(SC_CONFLICT);
  }

  @Test
  @DisplayName("should return 400 when an unsafe synchronous batch has an invalid statistical code id")
  void shouldReturn400_whenUnsafeBatchHasInvalidStatisticalCodeId() {
    OptimisticLockingUtil.configureAllowSuppressOptimisticLocking(
      Map.of(OptimisticLockingUtil.DB_ALLOW_SUPPRESS_OPTIMISTIC_LOCKING, "9999-12-31T23:59:59Z"));
    var holdingsArray = threeHoldingsRequest();
    holdingsArray.getJsonObject(1).put(STATISTICAL_CODE_IDS_KEY, Set.of(INVALID_VALUE));

    var response = syncBatchUnsafe(holdingsArray);

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString()).contains(INVALID_TYPE_ERROR_MESSAGE);
  }

  @Test
  @DisplayName("should return 400 when a synchronous batch has an invalid statistical code id")
  void shouldReturn400_whenBatchHasInvalidStatisticalCodeId() {
    var holdingsArray = threeHoldingsRequest();
    holdingsArray.getJsonObject(1).put(STATISTICAL_CODE_IDS_KEY, Set.of(INVALID_VALUE));

    var response = syncBatch(holdingsArray);

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString()).contains(INVALID_TYPE_ERROR_MESSAGE);
  }

  @Test
  @DisplayName("should create holdings via a synchronous batch")
  void shouldCreateHoldings_viaSynchronousBatch() {
    var holdingsArray = threeHoldingsRequest();

    assertThat(syncBatch(holdingsArray).status()).isEqualTo(SC_CREATED);

    holdingsArray.forEach(holding -> assertExists((JsonObject) holding));
  }

  @Test
  @DisplayName("should create a holding without an id via a synchronous batch with upsert=true")
  void shouldCreateHolding_viaSynchronousBatchWithoutIdAndUpsertTrue() {
    var instanceId = createInstanceRecord();
    var request = new JsonObject()
      .put("instanceId", instanceId)
      .put("sourceId", createHoldingsRecordsSource(client))
      .put("_version", 1)
      .put("callNumber", "test-call-number")
      .put(PERMANENT_LOCATION_ID_KEY, mainLibraryLocationId);

    assertThat(syncBatch("?upsert=true", new JsonArray().add(request)).status()).isEqualTo(SC_CREATED);

    var found = await(doGet(client, ResourcePaths.HOLDINGS + "?query=callNumber=test-call-number")).jsonBody()
      .getJsonArray(HOLDINGS_RECORDS_KEY).getJsonObject(0);
    assertThat(found.getString("id")).isNotNull();
    assertThat(found.getString("callNumber")).isEqualTo("test-call-number");
  }

  @Test
  @DisplayName("should return 422 when a synchronous batch references a non-existing instance at a "
               + "non-consortium tenant")
  void shouldReturn422_whenBatchInstanceDoesNotExist() {
    var holdingsArray = threeHoldingsRequestWithoutInstance();

    var response = syncBatch(holdingsArray);

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    var error = response.jsonBody().mapTo(Errors.class).getErrors().getFirst();
    assertThat(error.getMessage()).matches("Cannot set holdings_record.instanceid = \\S+ "
                                           + "because it does not exist in instance.id.");
    assertThat(error.getParameters().getFirst().getKey()).isEqualTo("holdings_record.instanceid");
    holdingsArray.forEach(holding ->
      assertGetNotFound(ResourcePaths.HOLDINGS + "/" + ((JsonObject) holding).getString("id")));
  }

  @Test
  @DisplayName("should return 422 when a synchronous batch has a duplicate id")
  void shouldReturn422_whenBatchHasDuplicateId() {
    var holdingsArray = threeHoldingsRequest();
    var duplicateId = holdingsArray.getJsonObject(0).getString("id");
    holdingsArray.getJsonObject(1).put("id", duplicateId);

    var response = syncBatch(holdingsArray);

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    holdingsArray.forEach(holding ->
      assertGetNotFound(ResourcePaths.HOLDINGS + "/" + ((JsonObject) holding).getString("id")));
  }

  @Test
  @DisplayName("should return 422 when a synchronous batch reuses an existing id without upsert")
  void shouldReturn422_whenBatchReusesExistingId_withoutUpsert() {
    assertReturns422ForExistingId("");
  }

  @Test
  @DisplayName("should return 422 when a synchronous batch reuses an existing id with upsert=false")
  void shouldReturn422_whenBatchReusesExistingId_withUpsertFalse() {
    assertReturns422ForExistingId("?upsert=false");
  }

  @Test
  @DisplayName("should search by the discoverySuppress property")
  void shouldSearchByDiscoverySuppressProperty() {
    var instanceId = createInstanceRecord();
    var suppressed = createHolding(holdingRequest(instanceId).withDiscoverySuppress(true));
    var notSuppressed = createHolding(holdingRequest(instanceId).withDiscoverySuppress(false));
    var notSuppressedDefault = createHolding(holdingRequest(instanceId));

    var suppressedResults = searchForHoldings("discoverySuppress==true");
    var notSuppressedResults = searchForHoldings("cql.allRecords=1 not discoverySuppress==true");

    assertThat(idsOf(suppressedResults)).containsExactly(suppressed.getString("id"));
    assertThat(idsOf(notSuppressedResults))
      .containsExactlyInAnyOrder(notSuppressed.getString("id"), notSuppressedDefault.getString("id"));
  }

  @Test
  @DisplayName("should filter by the full call number")
  void shouldFilterByFullCallNumber() {
    var instanceId = createInstanceRecord();
    var wholeCallNumber = createHolding(holdingRequest(instanceId)
      .withCallNumberPrefix("prefix").withCallNumber("callNumber").withCallNumberSuffix("suffix"));
    createHolding(holdingRequest(instanceId).withCallNumberPrefix("prefix").withCallNumber("callNumber"));
    createHolding(holdingRequest(instanceId)
      .withCallNumberPrefix("prefix").withCallNumber("differentCallNumber").withCallNumberSuffix("suffix"));

    var found = searchForHoldings("fullCallNumber==\"prefix callNumber suffix\"");

    assertThat(idsOf(found)).containsExactly(wholeCallNumber.getString("id"));
  }

  @Test
  @DisplayName("should filter by the call number and suffix")
  void shouldFilterByCallNumberAndSuffix() {
    var instanceId = createInstanceRecord();
    var wholeCallNumber = createHolding(holdingRequest(instanceId)
      .withCallNumberPrefix("prefix").withCallNumber("callNumber").withCallNumberSuffix("suffix"));
    createHolding(holdingRequest(instanceId).withCallNumberPrefix("prefix").withCallNumber("callNumber"));
    var noPrefix = createHolding(holdingRequest(instanceId)
      .withCallNumber("callNumber").withCallNumberSuffix("suffix"));

    var found = searchForHoldings("callNumberAndSuffix==\"callNumber suffix\"");

    assertThat(idsOf(found)).containsExactlyInAnyOrder(wholeCallNumber.getString("id"), noPrefix.getString("id"));
  }

  @Test
  @DisplayName("should find a holding by call number when there is a suffix")
  void shouldFindHolding_byCallNumberWithSuffix() {
    var instanceId = createInstanceRecord();
    var first = createHolding(holdingRequest(instanceId).withCallNumber("GE77 .F73 2014"));
    var second = createHolding(holdingRequest(instanceId)
      .withCallNumber("GE77 .F73 2014").withCallNumberSuffix("Curriculum Materials Collection"));
    createHolding(holdingRequest(instanceId)
      .withCallNumber("GE77 .F73 ").withCallNumberSuffix("2014 Curriculum Materials Collection"));

    var found = searchByCallNumberEyeReadable("GE77 .F73 2014");

    assertThat(found).containsExactlyInAnyOrder(first.getString("id"), second.getString("id"));
  }

  @Test
  @DisplayName("should apply explicit right truncation")
  void shouldApplyExplicitRightTruncation() {
    var instanceId = createInstanceRecord();
    var first = createHolding(holdingRequest(instanceId).withCallNumber("GE77 .F73 2014"));
    var second = createHolding(holdingRequest(instanceId)
      .withCallNumber("GE77 .F73 2014").withCallNumberSuffix("Curriculum Materials Collection"));
    var third = createHolding(holdingRequest(instanceId)
      .withCallNumber("GE77 .F73 ").withCallNumberSuffix("2014 Curriculum Materials Collection"));
    createHolding(holdingRequest(instanceId)
      .withCallNumber("GE77 .F74 ").withCallNumberSuffix("2014 Curriculum Materials Collection"));

    var found = searchByCallNumberEyeReadable("GE77 .F73*");

    assertThat(found).containsExactlyInAnyOrder(
      first.getString("id"), second.getString("id"), third.getString("id"));
  }

  @Test
  @DisplayName("should filter by an instance property")
  void shouldFilterByInstanceProperty() {
    var planetInstanceId = createInstance(client, "the long way to a small angry planet", instanceTypeId);
    var uprootedInstanceId = createInstance(client, "uprooted", instanceTypeId);
    var planetHolding = createHolding(holdingRequest(planetInstanceId));
    var uprootedHolding = createHolding(holdingRequest(uprootedInstanceId));

    var foundPlanet = searchForHoldings("instance.title = planet");
    assertThat(idsOf(foundPlanet)).containsExactly(planetHolding.getString("id"));

    var foundUprooted = searchForHoldings("instance.title = uprooted");
    assertThat(idsOf(foundUprooted)).containsExactly(uprootedHolding.getString("id"));
  }

  @Test
  @DisplayName("should set holding statements with notes")
  void shouldSetHoldingStatement_withNotes() {
    var statement = holdingStatement();

    var holding = createHolding(holdingRequest(createInstanceRecord())
      .create().put("holdingsStatements", new JsonArray().add(statement)));

    assertHoldingStatement(holding.getJsonArray("holdingsStatements").getJsonObject(0));
  }

  @Test
  @DisplayName("should set holding statements for indexes with notes")
  void shouldSetHoldingStatementForIndexes_withNotes() {
    var statement = holdingStatement();

    var holding = createHolding(holdingRequest(createInstanceRecord())
      .create().put("holdingsStatementsForIndexes", new JsonArray().add(statement)));

    assertHoldingStatement(holding.getJsonArray("holdingsStatementsForIndexes").getJsonObject(0));
  }

  @Test
  @DisplayName("should set holding statements for supplements with notes")
  void shouldSetHoldingStatementForSupplements_withNotes() {
    var statement = holdingStatement();

    var holding = createHolding(holdingRequest(createInstanceRecord())
      .create().put("holdingsStatementsForSupplements", new JsonArray().add(statement)));

    assertHoldingStatement(holding.getJsonArray("holdingsStatementsForSupplements").getJsonObject(0));
  }

  @Test
  @DisplayName("should return 422 when an additional call number is missing its call number")
  void shouldReturn422_whenAdditionalCallNumberMissingCallNumber() {
    var request = holdingRequest(createInstanceRecord()).create()
      .put("additionalCallNumbers", new JsonArray().add(pojo2JsonObject(new EffectiveCallNumberComponents())));

    var response = await(doPost(client, ResourcePaths.HOLDINGS, request));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  @DisplayName("should create a holding with minimal additional call numbers")
  void shouldCreateHolding_withMinimalAdditionalCallNumbers() {
    var additionalCallNumber = new EffectiveCallNumberComponents().withCallNumber("123456789");
    var request = holdingRequest(createInstanceRecord()).create()
      .put("additionalCallNumbers", new JsonArray().add(pojo2JsonObject(additionalCallNumber)));

    var response = await(doPost(client, ResourcePaths.HOLDINGS, request));

    assertThat(response.status()).isEqualTo(SC_CREATED);
  }

  @Test
  @DisplayName("should create a holding with additional call numbers")
  void shouldCreateHolding_withAdditionalCallNumbers() {
    var additionalCallNumber = new EffectiveCallNumberComponents()
      .withCallNumber("123456789").withPrefix("A").withSuffix("Z").withTypeId(lcCallNumberTypeId);
    var request = holdingRequest(createInstanceRecord()).create()
      .put("additionalCallNumbers", new JsonArray().add(pojo2JsonObject(additionalCallNumber)));

    var holding = createHolding(request);

    var stored = holding.getJsonArray("additionalCallNumbers").getJsonObject(0);
    assertThat(stored.getString("callNumber")).isEqualTo("123456789");
    assertThat(stored.getString("prefix")).isEqualTo("A");
    assertThat(stored.getString("suffix")).isEqualTo("Z");
    assertThat(stored.getString("typeId")).isEqualTo(lcCallNumberTypeId);
  }

  @Test
  @DisplayName("should create a holding with empty additional call numbers")
  void shouldCreateHolding_withEmptyAdditionalCallNumbers() {
    var request = holdingRequest(createInstanceRecord()).create().put("additionalCallNumbers", new JsonArray());

    var response = await(doPost(client, ResourcePaths.HOLDINGS, request));

    assertThat(response.status()).isEqualTo(SC_CREATED);
  }

  @Test
  @DisplayName("should delete an additional call number from a holding")
  void shouldDeleteAdditionalCallNumberFromHolding() {
    var additionalCallNumber = new EffectiveCallNumberComponents()
      .withCallNumber("123456789").withPrefix("A").withSuffix("Z").withTypeId(lcCallNumberTypeId);
    var request = holdingRequest(createInstanceRecord()).create()
      .put("additionalCallNumbers", new JsonArray().add(pojo2JsonObject(additionalCallNumber)));
    var holding = createHolding(request);

    holding.remove("additionalCallNumbers");
    assertThat(updateHolding(holding).status()).isEqualTo(SC_NO_CONTENT);
  }

  @Test
  @DisplayName("should update a holding's additional call numbers")
  void shouldUpdateHoldingsAdditionalCallNumbers() {
    var firstCallNumber = new EffectiveCallNumberComponents()
      .withCallNumber("123456789").withPrefix("A").withSuffix("Z").withTypeId(lcCallNumberTypeId);
    var request = holdingRequest(createInstanceRecord()).create()
      .put("additionalCallNumbers", new JsonArray().add(pojo2JsonObject(firstCallNumber)));
    var holding = createHolding(request);

    var secondCallNumber = new EffectiveCallNumberComponents()
      .withCallNumber("secondCallNumber").withPrefix("A").withSuffix("Z").withTypeId(lcCallNumberTypeId);
    holding.getJsonArray("additionalCallNumbers").add(pojo2JsonObject(secondCallNumber));

    assertThat(updateHolding(holding).status()).isEqualTo(SC_NO_CONTENT);

    var updated = getHoldingById(holding.getString("id")).jsonBody();
    assertThat(updated.getJsonArray("additionalCallNumbers")).hasSize(2);
    assertThat(updated.getJsonArray("additionalCallNumbers").getJsonObject(1).getString("callNumber"))
      .isEqualTo("secondCallNumber");
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

    assertThat(patchHolding(holdingId, patch).status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
  }

  private void assertReturns422ForExistingId(String queryParams) {
    var holdingsArray1 = threeHoldingsRequest();
    var holdingsArray2 = threeHoldingsRequest();
    var existingId = holdingsArray1.getJsonObject(1).getString("id");
    holdingsArray2.getJsonObject(1).put("id", existingId);

    assertThat(syncBatch(queryParams, holdingsArray1).status()).isEqualTo(SC_CREATED);
    assertThat(syncBatch(queryParams, holdingsArray2).status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
  }

  private static JsonObject holdingStatement() {
    return new JsonObject().put("statement", "Test statement").put("note", "Test note")
      .put("staffNote", "Test staff note");
  }

  private static void assertHoldingStatement(JsonObject statement) {
    assertThat(statement.getString("statement")).isEqualTo("Test statement");
    assertThat(statement.getString("note")).isEqualTo("Test note");
    assertThat(statement.getString("staffNote")).isEqualTo("Test staff note");
  }

  // -- shared helpers --

  private static String createInstanceRecord() {
    return createInstance(client, "an instance " + UUID.randomUUID(), instanceTypeId);
  }

  private static HoldingRequestBuilder holdingRequest(String instanceId) {
    return new HoldingRequestBuilder()
      .forInstance(UUID.fromString(instanceId))
      .withSource(UUID.fromString(createHoldingsRecordsSource(client)))
      .withPermanentLocation(UUID.fromString(mainLibraryLocationId));
  }

  private static HoldingRequestBuilder withHrid(HoldingRequestBuilder builder, String hrid) {
    return builder.withHrid(hrid);
  }

  private static JsonObject tags(String... tagValues) {
    return new JsonObject().put("tagList", new JsonArray(List.of(tagValues)));
  }

  private static JsonObject createHolding(HoldingRequestBuilder builder) {
    return createHolding(builder.create());
  }

  private static JsonObject createHolding(JsonObject request) {
    var response = await(doPost(client, ResourcePaths.HOLDINGS, request));
    assertThat(response.status()).isEqualTo(SC_CREATED);
    return response.jsonBody();
  }

  private static void createThreeHoldings() {
    var instanceId = createInstanceRecord();
    createHolding(holdingRequest(instanceId));
    createHolding(holdingRequest(instanceId));
    createHolding(holdingRequest(instanceId));
  }

  private static TestResponse updateHolding(JsonObject holding) {
    return await(doPut(client, ResourcePaths.HOLDINGS + "/" + holding.getString("id"), holding));
  }

  private static TestResponse getHoldingById(UUID id) {
    return getHoldingById(id.toString());
  }

  private static TestResponse getHoldingById(String id) {
    return await(doGet(client, ResourcePaths.HOLDINGS + "/" + id));
  }

  private static TestResponse patchHolding(String holdingId, JsonObject patch) {
    return await(doPatch(client, ResourcePaths.HOLDINGS + "/" + holdingId, patch));
  }

  private static void assertGetNotFound(String path) {
    assertThat(await(doGet(client, path)).status()).isEqualTo(SC_NOT_FOUND);
  }

  private static void assertExists(JsonObject expectedHolding) {
    var response = await(doGet(client, ResourcePaths.HOLDINGS + "/" + expectedHolding.getString("id")));
    assertThat(response.status()).isEqualTo(SC_OK);
    assertThat(response.body().toString()).contains(expectedHolding.getString("instanceId"));
  }

  private static List<String> getTags(JsonObject holding) {
    return holding.getJsonObject("tags").getJsonArray("tagList").stream().map(String.class::cast).toList();
  }

  private static void setHoldingsSequence(long sequenceNumber) {
    runQuery("select setval('hrid_holdings_seq'," + sequenceNumber + ",FALSE)");
  }

  private static TestResponse updateOptimizeUpdatesSetting(boolean value) {
    return await(doPatch(client, "/inventory-settings/inventory.optimize-updates.enabled",
      new JsonObject().put("value", value)));
  }

  private static TestResponse syncBatch(JsonArray holdingsArray) {
    return syncBatch("", holdingsArray);
  }

  private static TestResponse syncBatch(String queryParams, JsonArray holdingsArray) {
    return await(doPost(client, ResourcePaths.HOLDINGS_SYNC + queryParams,
      new JsonObject().put(HOLDINGS_RECORDS_KEY, holdingsArray)));
  }

  private static TestResponse syncBatchUnsafe(JsonArray holdingsArray) {
    return await(doPost(client, ResourcePaths.HOLDINGS_SYNC_UNSAFE,
      new JsonObject().put(HOLDINGS_RECORDS_KEY, holdingsArray)));
  }

  private static JsonArray threeHoldingsRequest() {
    var holdingsArray = new JsonArray();
    for (int i = 0; i < 3; i++) {
      var instanceId = createInstance(client, "an instance " + UUID.randomUUID(), instanceTypeId);
      holdingsArray.add(new JsonObject()
        .put("id", UUID.randomUUID().toString())
        .put("instanceId", instanceId)
        .put("sourceId", createHoldingsRecordsSource(client))
        .put("_version", 1)
        .put(PERMANENT_LOCATION_ID_KEY, mainLibraryLocationId));
    }
    return holdingsArray;
  }

  private static JsonArray threeHoldingsRequestWithoutInstance() {
    var holdingsArray = new JsonArray();
    for (int i = 0; i < 3; i++) {
      holdingsArray.add(new JsonObject()
        .put("id", UUID.randomUUID().toString())
        .put("instanceId", UUID.randomUUID().toString())
        .put("sourceId", createHoldingsRecordsSource(client))
        .put("_version", 1)
        .put(PERMANENT_LOCATION_ID_KEY, mainLibraryLocationId));
    }
    return holdingsArray;
  }

  private static void assertHridError(TestResponse response, String hrid) {
    assertThat(response.status()).isEqualTo(HttpStatus.SC_UNPROCESSABLE_ENTITY);
    var error = response.jsonBody().mapTo(Errors.class).getErrors().getFirst();
    assertThat(error.getMessage()).contains("HRID value already exists in table holdings_record: " + hrid);
    var parameter = error.getParameters().getFirst();
    assertThat(parameter.getKey()).isEqualTo("lower(f_unaccent(jsonb ->> 'hrid'::text))");
    assertThat(parameter.getValue()).isEqualTo(hrid);
  }

  private static JsonObject searchForHoldings(String cql) {
    return await(doGet(client, ResourcePaths.HOLDINGS + "?query=" + urlEncode(cql))).jsonBody();
  }

  private static List<String> idsOf(JsonObject searchResponse) {
    return searchResponse.getJsonArray(HOLDINGS_RECORDS_KEY).stream()
      .map(JsonObject.class::cast)
      .map(holding -> holding.getString("id"))
      .toList();
  }

  private static List<String> searchByCallNumberEyeReadable(String searchTerm) {
    var cql = "fullCallNumber==\"" + searchTerm + "\" OR callNumberAndSuffix==\"" + searchTerm
              + "\" OR callNumber==\"" + searchTerm + "\"";
    return idsOf(searchForHoldings(cql));
  }

  private static String urlEncode(String value) {
    return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
  }
}
