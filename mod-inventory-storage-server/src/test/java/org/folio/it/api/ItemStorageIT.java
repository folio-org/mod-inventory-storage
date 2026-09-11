package org.folio.it.api;

import static java.nio.charset.StandardCharsets.UTF_8;
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
import static org.folio.it.HoldingsStorageFixtures.createLoanType;
import static org.folio.it.HoldingsStorageFixtures.createMaterialType;
import static org.folio.it.InstanceStorageFixtures.createInstance;
import static org.folio.it.InstanceStorageFixtures.createInstanceType;
import static org.folio.it.ItemStorageFixtures.createStatisticalCode;
import static org.folio.it.LocationStorageFixtures.createLocation;
import static org.folio.validator.NotesValidators.MAX_NOTE_LENGTH;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import org.folio.it.BaseIntegrationTest;
import org.folio.it.HoldingsStorageFixtures;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.ItemLastCheckIn;
import org.folio.rest.jaxrs.model.ItemNote;
import org.folio.rest.tools.utils.OptimisticLockingUtil;
import org.folio.support.ResourcePaths;
import org.folio.support.builders.HoldingRequestBuilder;
import org.folio.support.builders.ItemRequestBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Item CRUD, order calculation, HRID lifecycle, batch sync/unsafe, optimistic locking, notes
 * validation, status-date tracking, additional call numbers, and FK-protection tests. CQL search
 * tests live in {@link ItemSearchIT}; bulk PATCH tests live in {@link ItemPatchIT} - both split
 * out because each is a large, cohesive concern in its own right.
 */
class ItemStorageIT extends BaseIntegrationTest {

  private static final String TAG_VALUE = "test-tag";
  private static final String INVALID_VALUE = "invalid value";
  private static final String INVALID_TYPE_ERROR_MESSAGE =
    "invalid input syntax for type uuid: \"" + INVALID_VALUE + "\"";
  private static final String ORDER_FIELD = "order";
  private static final String STATISTICAL_CODE_IDS_KEY = "statisticalCodeIds";
  private static final String ITEMS_KEY = "items";
  private static final String STATUS_KEY = "status";
  // The additionalCallNumbers typeId CallNumberUtils/tests key off; cannot be a fresh/random id.
  private static final String LC_CALL_NUMBER_TYPE_ID = "95467209-6d7b-468b-94df-0f5d7ad2747d";

  private static String instanceTypeId;
  private static String materialTypeId;
  private static String loanTypeId;
  private static String secondLoanTypeId;
  private static String mainLibraryLocationId;
  private static String annexLibraryLocationId;
  private static String lcCallNumberTypeId;

  @BeforeAll
  static void seedReferenceData() {
    instanceTypeId = createInstanceType(client);
    materialTypeId = createMaterialType(client);
    loanTypeId = createLoanType(client);
    secondLoanTypeId = createLoanType(client);
    mainLibraryLocationId = createLocation(client);
    annexLibraryLocationId = createLocation(client);
    lcCallNumberTypeId = createCallNumberType(client, LC_CALL_NUMBER_TYPE_ID, "Library of Congress classification");
  }

  @BeforeEach
  void clearItems() {
    runQuery("TRUNCATE TABLE instance, holdings_record, item CASCADE");
  }

  @AfterEach
  void resetItemSequenceAndOptimisticLockingOverride() {
    setItemSequence(1);
    OptimisticLockingUtil.configureAllowSuppressOptimisticLocking(Map.of());
  }

  @CsvSource({
    "PN 12 A6,PN12 .A6,,PN2 .A6,,,,,",
    "PN 12 A6 V 13 NO 12 41999,PN2 .A6 v.3 no.2 1999,,PN2 .A6,v. 3,no. 2,1999,,",
    "PN 12 A6 41999,PN12 .A6 41999,,PN2 .A6 1999,,,,,",
    "PN 12 A6 41999 CD,PN12 .A6 41999 CD,,PN2 .A6 1999,,,,,CD",
    "PN 12 A6 41999 12,PN12 .A6 41999 C.12,,PN2 .A6 1999,,,,2,",
    "PN 12 A69 41922 12,PN12 .A69 41922 C.12,,PN2 .A69,,,1922,2,",
    "PN 12 A69 NO 12,PN12 .A69 NO.12,,PN2 .A69,,no. 2,,,",
    "PN 12 A69 NO 12 41922 11,PN12 .A69 NO.12 41922 C.11,,PN2 .A69,,no. 2,1922,1,",
    "PN 12 A69 NO 12 41922 12,PN12 .A69 NO.12 41922 C.12,Wordsworth,PN2 .A69,,no. 2,1922,2,",
    "PN 12 A69 V 11 NO 11,PN12 .A69 V.11 NO.11,,PN2 .A69,v.1,no. 1,,,",
    "PN 12 A69 V 11 NO 11 +,PN12 .A69 V.11 NO.11 +,Over,PN2 .A69,v.1,no. 1,,,+",
    "PN 12 A69 V 11 NO 11 41921,PN12 .A69 V.11 NO.11 41921,,PN2 .A69,v.1,no. 1,1921,,",
    "PR 49199.3 41920 L33 41475 A6,PR 49199.3 41920 .L33 41475 .A6,,PR9199.3 1920 .L33 1475 .A6,,,,,",
    "PQ 42678 K26 P54,PQ 42678 .K26 P54,,PQ2678.K26 P54,,,,,",
    "PQ 48550.21 R57 V5 41992,PQ 48550.21 .R57 V15 41992,,PQ8550.21.R57 V5 1992,,,,,",
    "PQ 48550.21 R57 V5 41992,PQ 48550.21 .R57 V15 41992,,PQ8550.21.R57 V5,,,1992,,",
    "PR 3919 L33 41990,PR 3919 .L33 41990,,PR919 .L33 1990,,,,,",
    "PR 49199 A39,PR 49199 .A39,,PR9199 .A39,,,,,",
    "PR 49199.48 B3,PR 49199.48 .B3,,PR9199.48 .B3,,,,,"
  })
  @ParameterizedTest
  @DisplayName("should compute the effective shelving order when an item is created")
  void shouldComputeEffectiveShelvingOrder_whenItemIsCreated(
    String desiredShelvingOrder, String initiallyDesiredShelvesOrder, String prefix, String callNumber,
    String volume, String enumeration, String chronology, String copy, String suffix) {
    var holdingId = createHoldingRecord();
    var itemToCreate = itemRequestWithCallNumberComponents(holdingId, prefix, callNumber, volume, enumeration,
      chronology, copy, suffix);

    var item = createItem(itemToCreate);

    assertThat(item.getString("effectiveShelvingOrder")).isEqualTo(desiredShelvingOrder);
  }

  @Test
  @DisplayName("should create an item via the collection resource with many properties populated")
  void shouldCreateItem_viaCollectionResourceWithManyPropertiesPopulated() {
    var holdingId = createHoldingRecord();
    var id = UUID.randomUUID();
    var inTransitServicePointId = UUID.randomUUID().toString();
    var adminNote = "an admin note";
    var displaySummary = "Important item";
    var statisticalCodeId = createStatisticalCode(client);
    var itemToCreate = detailedItemRequest(id, holdingId, adminNote, displaySummary, inTransitServicePointId,
      statisticalCodeId);

    var item = createItem(itemToCreate);
    assertDetailedItem(item, id, adminNote, holdingId, displaySummary, inTransitServicePointId, statisticalCodeId);

    var itemFromGet = getItemJsonById(id.toString());
    assertDetailedItem(itemFromGet, id, adminNote, holdingId, displaySummary, inTransitServicePointId,
      statisticalCodeId);
  }

  @Test
  @DisplayName("should create an item with minimal properties")
  void shouldCreateItem_withMinimalProperties() {
    var holdingId = createHoldingRecord();
    var id = UUID.randomUUID();
    var itemToCreate = minimalItemRequest(id, holdingId).put("tags", tags(TAG_VALUE));

    var item = createItem(itemToCreate);
    assertThat(item.getString("id")).isEqualTo(id.toString());

    var itemFromGet = getItemJsonById(id.toString());
    assertThat(itemFromGet.getString("id")).isEqualTo(id.toString());
    assertThat(itemFromGet.getJsonObject(STATUS_KEY).getString("name")).isEqualTo("Available");
    assertThat(itemFromGet.getInteger(ORDER_FIELD)).isEqualTo(1);
    assertThat(getTags(itemFromGet)).containsExactly(TAG_VALUE);
  }

  @Test
  @DisplayName("should calculate different order values for several items created concurrently")
  void shouldCalculateDifferentOrderValues_forItemsCreatedConcurrently() {
    var holdingId = createHoldingRecord();
    var id1 = UUID.randomUUID();
    var id2 = UUID.randomUUID();

    var responses = runConcurrentPosts(Map.of(id1, minimalItemRequest(id1, holdingId),
      id2, minimalItemRequest(id2, holdingId)));

    assertThat(responses).allSatisfy(response -> assertThat(response.status()).isEqualTo(SC_CREATED));
    var order1 = getItemJsonById(id1.toString()).getInteger(ORDER_FIELD);
    var order2 = getItemJsonById(id2.toString()).getInteger(ORDER_FIELD);
    assertThat(order1).isIn(1, 2);
    assertThat(order2).isIn(1, 2);
    assertThat(order1).isNotEqualTo(order2);
  }

  @Test
  @DisplayName("should calculate order for concurrently created items mixing manual and automatic order")
  void shouldCalculateOrder_forConcurrentItemsWithManualAndAutoOrder() {
    var holdingId = createHoldingRecord();
    var id1 = UUID.randomUUID();
    var id2 = UUID.randomUUID();
    var id3 = UUID.randomUUID();
    var item2 = minimalItemRequest(id2, holdingId).put(ORDER_FIELD, 6);

    var responses = runConcurrentPosts(Map.of(id1, minimalItemRequest(id1, holdingId), id2, item2,
      id3, minimalItemRequest(id3, holdingId)));

    assertThat(responses).allSatisfy(response -> assertThat(response.status()).isEqualTo(SC_CREATED));
    assertThat(getItemJsonById(id2.toString()).getInteger(ORDER_FIELD)).isEqualTo(6);
    var order1 = getItemJsonById(id1.toString()).getInteger(ORDER_FIELD);
    var order3 = getItemJsonById(id3.toString()).getInteger(ORDER_FIELD);
    assertThat(order1).isIn(1, 2, 7, 8);
    assertThat(order3).isIn(1, 2, 7, 8);
    assertThat(order1).isNotEqualTo(order3);

    var item4 = createItem(minimalItemRequest(UUID.randomUUID(), holdingId));

    assertThat(item4.getInteger(ORDER_FIELD)).isIn(7, 8, 9);
  }

  @Test
  @DisplayName("should calculate order for a new item when the previous request's order decreases")
  void shouldCalculateOrder_whenOrderDecreasesInNextRequest() {
    var holdingId = createHoldingRecord();
    var id1 = UUID.randomUUID();
    var id2 = UUID.randomUUID();

    var responses = runConcurrentPosts(Map.of(id1, minimalItemRequest(id1, holdingId).put(ORDER_FIELD, 1000),
      id2, minimalItemRequest(id2, holdingId).put(ORDER_FIELD, 10)));
    assertThat(responses).allSatisfy(response -> assertThat(response.status()).isEqualTo(SC_CREATED));

    var item3 = createItem(minimalItemRequest(UUID.randomUUID(), holdingId));

    assertThat(getItemJsonById(id1.toString()).getInteger(ORDER_FIELD)).isEqualTo(1000);
    assertThat(getItemJsonById(id2.toString()).getInteger(ORDER_FIELD)).isEqualTo(10);
    assertThat(item3.getInteger(ORDER_FIELD)).isEqualTo(1001);
  }

  @Test
  @DisplayName("should return 400 when a statistical code id is invalid")
  void shouldReturn400_whenStatisticalCodeIdIsInvalid() {
    var holdingId = createHoldingRecord();
    var itemToCreate = minimalItemRequest(UUID.randomUUID(), holdingId)
      .put("tags", tags(TAG_VALUE)).put(STATISTICAL_CODE_IDS_KEY, List.of(INVALID_VALUE));

    var response = await(doPost(client, ResourcePaths.ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString()).contains(INVALID_TYPE_ERROR_MESSAGE);
  }

  @Test
  @DisplayName("should return 422 when the item-level call number type id is not a UUID")
  void shouldReturn422_whenItemLevelCallNumberTypeIdIsNotUuid() {
    var holdingId = createHoldingRecord();
    var itemToCreate = minimalItemRequest(UUID.randomUUID(), holdingId)
      .put("itemLevelCallNumberTypeId", INVALID_VALUE);

    var response = await(doPost(client, ResourcePaths.ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    assertThat(response.body().toString()).contains("must match");
  }

  @Test
  @DisplayName("should return 422 when the effective call number components type id is not a UUID")
  void shouldReturn422_whenEffectiveCallNumberComponentsTypeIdIsNotUuid() {
    var holdingId = createHoldingRecord();
    var itemToCreate = minimalItemRequest(UUID.randomUUID(), holdingId)
      .put("effectiveCallNumberComponents", new JsonObject().put("typeId", INVALID_VALUE));

    var response = await(doPost(client, ResourcePaths.ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    assertThat(response.body().toString()).contains("must match");
  }

  @Test
  @DisplayName("should return 422 when the item-level call number type id does not exist")
  void shouldReturn422_whenItemLevelCallNumberTypeIdDoesNotExist() {
    var holdingId = createHoldingRecord();
    var nonExistingTypeId = UUID.randomUUID();
    var itemToCreate = minimalItemRequest(UUID.randomUUID(), holdingId)
      .put("itemLevelCallNumberTypeId", nonExistingTypeId);

    var response = await(doPost(client, ResourcePaths.ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    assertThat(response.body().toString()).contains(("Cannot set item.itemlevelcallnumbertypeid = %s "
                                                     + "because it does not exist in call_number_type.id.").formatted(
      nonExistingTypeId));
  }

  @Test
  @DisplayName("should create an item with circulation note ids populated")
  void shouldCreateItem_withCirculationNoteIdsPopulated() {
    var holdingId = createHoldingRecord();
    var itemId = UUID.randomUUID();
    var circulationNoteId = UUID.randomUUID();
    var itemToCreate = itemRequestWithCirculationNotes(holdingId, itemId, circulationNoteId);

    var item = createItem(itemToCreate);

    assertThat(item.getString("id")).isEqualTo(itemId.toString());
    assertCirculationNotesPopulated(getItemJsonById(itemId.toString()), circulationNoteId);
  }

  @Test
  @DisplayName("should replace an item with new properties")
  void shouldReplaceItem_withNewProperties() {
    var holdingId = createHoldingRecord();
    var id = UUID.randomUUID();
    createItem(smallAngryPlanet(id, holdingId));
    var createdItem = getItemJsonById(id.toString());
    assertThat(createdItem.getString("copyNumber")).isNull();

    var updatedItem = createdItem.copy().put("copyNumber", "copy1").put("displaySummary", "Important item")
      .put("administrativeNotes", new JsonArray().add("an admin note"));
    assertThat(updateItem(updatedItem).status()).isEqualTo(SC_NO_CONTENT);

    var itemFromGet = getItemJsonById(id.toString());
    assertThat(itemFromGet.getString("copyNumber")).isEqualTo("copy1");
    assertThat(itemFromGet.getJsonArray("administrativeNotes")).contains("an admin note");
    assertThat(itemFromGet.getString("displaySummary")).isEqualTo("Important item");
  }

  @Test
  @DisplayName("should move an item to a new holding")
  void shouldMoveItem_toNewHolding() {
    var oldHoldingId = createHoldingRecord();
    var newHoldingId = createHoldingRecord();
    var id = UUID.randomUUID();
    createItem(smallAngryPlanet(id, oldHoldingId));
    var createdItem = getItemJsonById(id.toString());

    var updatedItem = createdItem.copy().put("holdingsRecordId", newHoldingId);
    assertThat(updateItem(updatedItem).status()).isEqualTo(SC_NO_CONTENT);

    assertThat(getItemJsonById(id.toString()).getString("holdingsRecordId")).isEqualTo(newHoldingId);
  }

  @Test
  @DisplayName("should enforce optimistic locking on item version")
  void shouldEnforceOptimisticLocking_onItemVersion() {
    var holdingId = createHoldingRecord();
    var item = createItem(smallAngryPlanet(UUID.randomUUID(), holdingId));
    item.put("permanentLocationId", annexLibraryLocationId);
    // updating with current _version 1 succeeds and increments _version to 2
    assertThat(updateItem(item).status()).isEqualTo(SC_NO_CONTENT);
    item.put("permanentLocationId", mainLibraryLocationId);
    // updating with outdated _version 1 fails, current _version is 2
    assertThat(updateItem(item).status()).isEqualTo(SC_CONFLICT);
    // updating with _version -1 should fail, single item PUT never allows suppressing optimistic locking
    item.put("_version", -1);
    assertThat(updateItem(item).status()).isEqualTo(SC_CONFLICT);
    // this allow should not apply to single item PUT, only to batch unsafe
    OptimisticLockingUtil.configureAllowSuppressOptimisticLocking(
      Map.of(OptimisticLockingUtil.DB_ALLOW_SUPPRESS_OPTIMISTIC_LOCKING, "9999-12-31T23:59:59Z"));
    assertThat(updateItem(item).status()).isEqualTo(SC_CONFLICT);
  }

  @Test
  @DisplayName("should not update an item when there are no changes and optimize-updates is enabled")
  void shouldNotUpdateItem_whenNoChangesAndOptimizeUpdatesEnabled() {
    assertThat(updateOptimizeUpdatesSetting(true).status()).isEqualTo(SC_NO_CONTENT);
    var holdingId = createHoldingRecord();
    var item = createItem(smallAngryPlanet(UUID.randomUUID(), holdingId));

    assertThat(updateItem(item).status()).isEqualTo(SC_NO_CONTENT);

    assertThat(getItemJsonById(item.getString("id")).getString("_version")).isEqualTo("1");
  }

  @Test
  @DisplayName("should update an item when there are no changes and optimize-updates is disabled")
  void shouldUpdateItem_whenNoChangesAndOptimizeUpdatesDisabled() {
    assertThat(updateOptimizeUpdatesSetting(false).status()).isEqualTo(SC_NO_CONTENT);
    var holdingId = createHoldingRecord();
    var item = createItem(smallAngryPlanet(UUID.randomUUID(), holdingId));

    assertThat(updateItem(item).status()).isEqualTo(SC_NO_CONTENT);

    assertThat(getItemJsonById(item.getString("id")).getString("_version")).isEqualTo("2");
  }

  @Test
  @DisplayName("should create an item without providing an id")
  void shouldCreateItem_withoutProvidingId() {
    var holdingId = createHoldingRecord();
    var itemToCreate = smallAngryPlanet(null, holdingId).put("tags", tags(TAG_VALUE));

    var item = createItem(itemToCreate);
    var newId = item.getString("id");

    var itemFromGet = getItemJsonById(newId);
    assertThat(itemFromGet.getString("holdingsRecordId")).isEqualTo(holdingId);
    assertThat(itemFromGet.getString("barcode")).isEqualTo("036000291452");
    assertThat(itemFromGet.getJsonObject(STATUS_KEY).getString("name")).isEqualTo("Available");
    assertThat(getTags(itemFromGet)).containsExactly(TAG_VALUE);
  }

  @Test
  @DisplayName("should create an item when a hrid is supplied")
  void shouldCreateItem_whenHridIsSupplied() {
    var holdingId = createHoldingRecord();
    var itemToCreate = minimalItemRequest(UUID.randomUUID(), holdingId).put("hrid", "ITEM12345")
      .put("tags", tags(TAG_VALUE));

    var item = createItem(itemToCreate);

    assertThat(item.getString("hrid")).isEqualTo("ITEM12345");
    assertThat(getItemJsonById(item.getString("id")).getString("hrid")).isEqualTo("ITEM12345");
  }

  @Test
  @DisplayName("should update an item when the hrid has not changed")
  void shouldUpdateItem_whenHridHasNotChanged() {
    var holdingId = createHoldingRecord();
    var itemId = UUID.randomUUID();
    var itemToCreate = minimalItemRequest(itemId, holdingId);
    setItemSequence(1);
    createItem(itemToCreate);

    var createdItem = getItemJsonById(itemId.toString());
    assertThat(updateItem(createdItem.put("tags", tags("new tag"))).status()).isEqualTo(SC_NO_CONTENT);

    var updatedItem = getItemJsonById(itemId.toString());
    assertThat(updatedItem.getString("hrid")).isEqualTo("it00000000001");
    assertThat(getTags(updatedItem)).containsExactly("new tag");
  }

  @Test
  @DisplayName("should update an item with circulation note ids populated")
  void shouldUpdateItem_withCirculationNoteIdsPopulated() {
    var holdingId = createHoldingRecord();
    var itemId = UUID.randomUUID();
    setItemSequence(1);
    createItem(minimalItemRequest(itemId, holdingId));

    var circulationNoteId = UUID.randomUUID();
    var itemToUpdate = getItemJsonById(itemId.toString())
      .put("circulationNotes", circulationNotes(circulationNoteId));
    assertThat(updateItem(itemToUpdate).status()).isEqualTo(SC_NO_CONTENT);

    assertCirculationNotesPopulated(getItemJsonById(itemId.toString()), circulationNoteId);
  }

  @Test
  @DisplayName("should return 422 when the permanent location does not exist")
  void shouldReturn422_whenPermanentLocationDoesNotExist() {
    var holdingId = createHoldingRecord();
    var badLocation = UUID.randomUUID();
    var itemToCreate = minimalItemRequest(UUID.randomUUID(), holdingId).put("permanentLocationId", badLocation);

    var response = await(doPost(client, ResourcePaths.ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    assertThat(response.body().toString()).contains("Cannot set item.permanentlocationid");
  }

  @Test
  @DisplayName("should return 422 when the temporary location does not exist")
  void shouldReturn422_whenTemporaryLocationDoesNotExist() {
    var holdingId = createHoldingRecord();
    var badLocation = UUID.randomUUID();
    var itemToCreate = minimalItemRequest(UUID.randomUUID(), holdingId).put("temporaryLocationId", badLocation);

    var response = await(doPost(client, ResourcePaths.ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    assertThat(response.body().toString()).contains("Cannot set item.temporarylocationid");
  }

  @Test
  @DisplayName("should return 422 when the item id is not a UUID")
  void shouldReturn422_whenItemIdIsNotUuid() {
    var holdingId = createHoldingRecord();
    var itemToCreate = minimalItemRequest(UUID.randomUUID(), holdingId).put("id", "1234")
      .put("temporaryLocationId", annexLibraryLocationId);

    var response = await(doPost(client, ResourcePaths.ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    assertThat(response.body().toString()).contains("UUID");
  }

  @Test
  @DisplayName("should return 422 when the material type is missing")
  void shouldReturn422_whenMaterialTypeIsMissing() {
    var holdingId = createHoldingRecord();
    var itemToCreate = new JsonObject().put("id", UUID.randomUUID().toString())
      .put(STATUS_KEY, new JsonObject().put("name", "Available")).put("holdingsRecordId", holdingId)
      .put("permanentLoanTypeId", loanTypeId);

    var response = await(doPost(client, ResourcePaths.ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    var errors = response.jsonBody().mapTo(Errors.class).getErrors();
    assertThat(errors).hasSize(1);
    assertThat(errors.getFirst().getMessage()).isIn("may not be null", "must not be null");
    assertThat(errors.getFirst().getParameters().getFirst().getKey()).isEqualTo("materialTypeId");
  }

  @Test
  @DisplayName("should return 422 when the material type does not exist")
  void shouldReturn422_whenMaterialTypeDoesNotExist() {
    var holdingId = createHoldingRecord();
    var itemToCreate = minimalItemRequest(UUID.randomUUID(), holdingId).put("materialTypeId", UUID.randomUUID());

    var response = await(doPost(client, ResourcePaths.ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    assertThat(response.body().toString()).contains("Cannot set item.materialtypeid");
  }

  @Test
  @DisplayName("should return 422 when creating an item whose note exceeds the maximum length")
  void shouldReturn422_whenCreatingItemNoteExceedsMaximumLength() {
    var holdingId = createHoldingRecord();
    var itemToCreate = minimalItemRequest(UUID.randomUUID(), holdingId)
      .put("notes", new JsonArray().add(pojo2JsonObject(new ItemNote().withNote("x".repeat(MAX_NOTE_LENGTH + 1)))));

    var response = await(doPost(client, ResourcePaths.ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  @DisplayName("should return 422 when creating an item whose administrative note exceeds the maximum length")
  void shouldReturn422_whenCreatingItemAdministrativeNoteExceedsMaximumLength() {
    var holdingId = createHoldingRecord();
    var itemToCreate = minimalItemRequest(UUID.randomUUID(), holdingId)
      .put("administrativeNotes", new JsonArray().add("x".repeat(MAX_NOTE_LENGTH + 1)));

    var response = await(doPost(client, ResourcePaths.ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  @DisplayName("should return 422 when updating an item's administrative note to exceed the maximum length")
  void shouldReturn422_whenUpdatingItemAdministrativeNoteExceedsMaximumLength() {
    var holdingId = createHoldingRecord();
    var itemId = UUID.randomUUID();
    createItem(minimalItemRequest(itemId, holdingId).put("hrid", "testHRID"));
    var item = getItemJsonById(itemId.toString())
      .put("administrativeNotes", new JsonArray().add("x".repeat(MAX_NOTE_LENGTH + 1)));

    assertThat(updateItem(item).status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  @DisplayName("should return 422 when updating an item's note to exceed the maximum length")
  void shouldReturn422_whenUpdatingItemNoteExceedsMaximumLength() {
    var holdingId = createHoldingRecord();
    var itemId = UUID.randomUUID();
    createItem(smallAngryPlanet(itemId, holdingId));
    var item = getItemJsonById(itemId.toString())
      .put("notes", new JsonArray().add(pojo2JsonObject(new ItemNote().withNote("x".repeat(MAX_NOTE_LENGTH + 1)))));

    assertThat(updateItem(item).status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  @DisplayName("should return 400 when creating an item with a duplicate hrid")
  void shouldReturn400_whenCreatingItemWithDuplicateHrid() {
    var holdingId = createHoldingRecord();
    var itemToCreate = minimalItemRequest(UUID.randomUUID(), holdingId);
    setItemSequence(1);
    createItem(itemToCreate);
    assertThat(getItemJsonById(itemToCreate.getString("id")).getString("hrid")).isEqualTo("it00000000001");

    var duplicate = minimalItemRequest(UUID.randomUUID(), holdingId).put("hrid", "it00000000001");
    var response = await(doPost(client, ResourcePaths.ITEMS, duplicate));

    assertHridError(response, "it00000000001");
  }

  @Test
  @DisplayName("should return 500 when hrid generation fails because the sequence is exhausted")
  void shouldReturn500_whenHridGenerationFails() {
    var holdingId = createHoldingRecord();
    var itemToCreate = minimalItemRequest(UUID.randomUUID(), holdingId);
    setItemSequence(99_999_999_999L);
    createItem(itemToCreate);
    assertThat(getItemJsonById(itemToCreate.getString("id")).getString("hrid")).isEqualTo("it99999999999");

    var response =
      await(doPost(client, ResourcePaths.ITEMS, minimalItemRequest(UUID.randomUUID(), holdingId)));

    assertThat(response.status()).isEqualTo(SC_INTERNAL_SERVER_ERROR);
    assertThat(response.body().toString()).contains("hrid_items_seq");
  }

  @Test
  @DisplayName("should return 422 when updating an item with a non-existing material type")
  void shouldReturn422_whenUpdatingItemWithNonExistingMaterialType() {
    var holdingId = createHoldingRecord();
    var itemId = UUID.randomUUID();
    createItem(minimalItemRequest(itemId, holdingId).put("hrid", "testHRID"));
    var item = getItemJsonById(itemId.toString()).put("materialTypeId", UUID.randomUUID().toString());

    var response = updateItem(item);

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString()).contains("Cannot set item").contains("materialtypeid");
  }

  @Test
  @DisplayName("should return 400 when changing the hrid after creation")
  void shouldReturn400_whenChangingHridAfterCreation() {
    var holdingId = createHoldingRecord();
    var itemId = UUID.randomUUID();
    setItemSequence(1);
    createItem(minimalItemRequest(itemId, holdingId));
    var item = getItemJsonById(itemId.toString()).put("hrid", "ABC123");

    var response = updateItem(item);

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body()).hasToString("The hrid field cannot be changed: new=ABC123, old=it00000000001");
  }

  @Test
  @DisplayName("should return 400 when removing the hrid after creation")
  void shouldReturn400_whenRemovingHridAfterCreation() {
    var holdingId = createHoldingRecord();
    var itemId = UUID.randomUUID();
    setItemSequence(1);
    createItem(minimalItemRequest(itemId, holdingId));
    var item = getItemJsonById(itemId.toString());
    item.remove("hrid");

    var response = updateItem(item);

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body()).hasToString("The hrid field cannot be changed: new=null, old=it00000000001");
  }

  @Test
  @DisplayName("should return 400 when the statistical code ids are not UUIDs")
  void shouldReturn400_whenStatisticalCodeIdsAreNotUuids() {
    var holdingId = createHoldingRecord();
    var itemToCreate = minimalItemRequest(UUID.randomUUID(), holdingId).put(STATISTICAL_CODE_IDS_KEY,
      new JsonArray().add("07"));

    var response = await(doPost(client, ResourcePaths.ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString())
      .contains("invalid input syntax for type uuid: \"07\"");
  }

  @Test
  @DisplayName("should return 400 when a synchronous batch has non-UUID statistical code ids")
  void shouldReturn400_whenSynchronousBatchHasNonUuidStatisticalCodeIds() {
    var holdingId = createHoldingRecord();
    var itemToCreate = minimalItemRequest(UUID.randomUUID(), holdingId).put(STATISTICAL_CODE_IDS_KEY,
      new JsonArray().add("00000000-0000-4444-8888-000000000000").add("12345678"));

    var response = syncBatch(new JsonArray().add(itemToCreate));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString()).contains("invalid input syntax for type uuid: \"12345678\"");
  }

  @Test
  @DisplayName("should return 400 when replacing an item with non-UUID statistical code ids")
  void shouldReturn400_whenReplacingItemWithNonUuidStatisticalCodeIds() {
    var holdingId = createHoldingRecord();
    var item = createItem(minimalItemRequest(UUID.randomUUID(), holdingId));
    item.put(STATISTICAL_CODE_IDS_KEY, new JsonArray().add("1234567890123456789012345678901234567890"));

    var response = updateItem(item);

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString())
      .contains("invalid input syntax for type uuid: \"1234567890123456789012345678901234567890\"");
  }

  @Test
  @DisplayName("should return 422 when an item has an additional unrecognized property")
  void shouldReturn422_whenItemHasAdditionalProperty() {
    var holdingId = createHoldingRecord();
    var itemToCreate = smallAngryPlanet(UUID.randomUUID(), holdingId).put("somethingAdditional", "foo");

    var response = await(doPost(client, ResourcePaths.ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    assertThat(response.jsonBody().mapTo(Errors.class).getErrors().getFirst().getMessage())
      .contains("Unrecognized field");
  }

  @Test
  @DisplayName("should return 422 when an item's status has an additional unrecognized property")
  void shouldReturn422_whenItemStatusHasAdditionalProperty() {
    var holdingId = createHoldingRecord();
    var itemToCreate = smallAngryPlanet(UUID.randomUUID(), holdingId)
      .put(STATUS_KEY, new JsonObject().put("somethingAdditional", "foo"));

    var response = await(doPost(client, ResourcePaths.ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    assertThat(response.jsonBody().mapTo(Errors.class).getErrors().getFirst().getMessage())
      .contains("Unrecognized field");
  }

  @Test
  @DisplayName("should create items via a synchronous batch")
  void shouldCreateItems_viaSynchronousBatch() {
    var itemsArray = threeItems();

    assertThat(syncBatch(itemsArray).status()).isEqualTo(SC_CREATED);

    itemsArray.forEach(item -> assertExists((JsonObject) item));
  }

  @Test
  @DisplayName("should create an item without an id via a synchronous batch with upsert=true")
  void shouldCreateItem_withoutIdViaSynchronousBatchWithUpsertTrue() {
    var holdingId = createHoldingRecord();
    var item = minimalItemRequest(null, holdingId).put("barcode", "item1");

    assertThat(syncBatch("?upsert=true", new JsonArray().add(item)).status()).isEqualTo(SC_CREATED);

    var found = await(doGet(client, ResourcePaths.ITEMS + "?query=barcode=item1")).jsonBody()
      .getJsonArray(ITEMS_KEY).getJsonObject(0);
    assertThat(found.getString("id")).isNotNull();
    assertThat(found.getString("barcode")).isEqualTo("item1");
  }

  @Test
  @DisplayName("should return 400 when a synchronous batch has invalid statistical code ids")
  void shouldReturn400_whenSynchronousBatchHasInvalidStatisticalCodeIds() {
    var itemsArray = threeItems();
    itemsArray.getJsonObject(1).put(STATISTICAL_CODE_IDS_KEY, List.of(INVALID_VALUE));

    var response = syncBatch(itemsArray);

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString()).contains(INVALID_TYPE_ERROR_MESSAGE);
  }

  @Test
  @DisplayName("should return 413 when an unsafe synchronous batch is not allowed")
  void shouldReturn413_whenUnsafeSynchronousBatchNotAllowed() {
    // not allowed because DB_ALLOW_SUPPRESS_OPTIMISTIC_LOCKING is not configured
    assertThat(syncBatchUnsafe(threeItems()).status()).isEqualTo(SC_REQUEST_TOO_LONG);
  }

  @Test
  @DisplayName("should create items via an unsafe synchronous batch")
  void shouldCreateItems_viaUnsafeSynchronousBatch() {
    OptimisticLockingUtil.configureAllowSuppressOptimisticLocking(
      Map.of(OptimisticLockingUtil.DB_ALLOW_SUPPRESS_OPTIMISTIC_LOCKING, "9999-12-31T23:59:59Z"));
    var itemsArray = threeItems();
    assertThat(syncBatchUnsafe(itemsArray).status()).isEqualTo(SC_CREATED);

    itemsArray.getJsonObject(1).put("barcode", "123");
    assertThat(syncBatchUnsafe(itemsArray).status()).isEqualTo(SC_CREATED);

    // safe update, env var should not influence the regular API
    itemsArray.getJsonObject(1).put("barcode", "456");
    assertThat(syncBatch("?upsert=true", itemsArray).status()).isEqualTo(SC_CONFLICT);
  }

  @Test
  @DisplayName("should return 400 when an unsafe synchronous batch has invalid statistical code ids")
  void shouldReturn400_whenUnsafeSynchronousBatchHasInvalidStatisticalCodeIds() {
    OptimisticLockingUtil.configureAllowSuppressOptimisticLocking(
      Map.of(OptimisticLockingUtil.DB_ALLOW_SUPPRESS_OPTIMISTIC_LOCKING, "9999-12-31T23:59:59Z"));
    var itemsArray = threeItems();
    itemsArray.getJsonObject(1).put(STATISTICAL_CODE_IDS_KEY, List.of(INVALID_VALUE));

    var response = syncBatchUnsafe(itemsArray);

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString()).contains(INVALID_TYPE_ERROR_MESSAGE);
  }

  @Test
  @DisplayName("should return 422 when a synchronous batch has a duplicate id")
  void shouldReturn422_whenSynchronousBatchHasDuplicateId() {
    var itemsArray = threeItems();
    var duplicateId = itemsArray.getJsonObject(0).getString("id");
    itemsArray.getJsonObject(1).put("id", duplicateId);

    var response = syncBatch(itemsArray);

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    itemsArray.forEach(item -> assertGetNotFound(ResourcePaths.ITEMS + "/" + ((JsonObject) item).getString("id")));
  }

  @Test
  @DisplayName("should return 422 when a synchronous batch reuses an existing id without upsert")
  void shouldReturn422_whenSynchronousBatchReusesExistingId_withoutUpsert() {
    assertReturns422ForExistingId("");
  }

  @Test
  @DisplayName("should return 422 when a synchronous batch reuses an existing id with upsert=false")
  void shouldReturn422_whenSynchronousBatchReusesExistingId_withUpsertFalse() {
    assertReturns422ForExistingId("?upsert=false");
  }

  @Test
  @DisplayName("should create items via a synchronous batch reusing an existing id with upsert=true")
  void shouldCreateItems_viaSynchronousBatchReusingExistingIdWithUpsertTrue() {
    var holdingId = createHoldingRecord();
    var existingItemId = UUID.randomUUID();
    var itemsArray1 = threeItemsWithId(holdingId, existingItemId);
    var itemsArray2 = threeItemsWithId(holdingId, existingItemId);

    var firstResponse = syncBatch("?upsert=true", itemsArray1);
    var secondResponse = syncBatch("?upsert=true", itemsArray2);

    assertThat(firstResponse.status()).isEqualTo(SC_CREATED);
    assertThat(secondResponse.status()).isEqualTo(SC_CREATED);
    Stream.concat(itemsArray1.stream(), itemsArray2.stream())
      .map(item -> ((JsonObject) item).getString("id"))
      .forEach(id -> assertThat(getItemById(id).status()).isEqualTo(SC_OK));
  }

  @Test
  @DisplayName("should generate hrids for a synchronous batch")
  void shouldGenerateHrids_forSynchronousBatch() {
    setItemSequence(1);
    var itemsArray = threeItems();

    assertThat(syncBatch(itemsArray).status()).isEqualTo(SC_CREATED);

    itemsArray.forEach(item -> {
      var id = ((JsonObject) item).getString("id");
      assertExists((JsonObject) item);
      assertThat(getItemJsonById(id).getString("hrid")).isBetween("it00000000001", "it00000000003");
    });
  }

  @Test
  @DisplayName("should generate hrids for items missing one in a synchronous batch")
  void shouldGenerateHrids_forItemsMissingOneInSynchronousBatch() {
    setItemSequence(1);
    var hrid = "ABC123";
    var itemsArray = threeItems();
    itemsArray.getJsonObject(1).put("hrid", hrid);

    assertThat(syncBatch(itemsArray).status()).isEqualTo(SC_CREATED);

    var first = getItemJsonById(itemsArray.getJsonObject(0).getString("id"));
    assertExists(itemsArray.getJsonObject(0));
    assertThat(first.getString("hrid")).isBetween("it00000000001", "it00000000002");
    assertThat(getItemJsonById(itemsArray.getJsonObject(1).getString("id")).getString("hrid")).isEqualTo(hrid);
    var third = getItemJsonById(itemsArray.getJsonObject(2).getString("id"));
    assertThat(third.getString("hrid")).isBetween("it00000000001", "it00000000002");
  }

  @Test
  @DisplayName("should return 422 when a synchronous batch has duplicate hrids")
  void shouldReturn422_whenSynchronousBatchHasDuplicateHrids() {
    setItemSequence(1);
    var duplicateHrid = "it00000000001";
    var itemsArray = threeItems();
    itemsArray.getJsonObject(0).put("hrid", duplicateHrid);
    itemsArray.getJsonObject(1).put("hrid", duplicateHrid);

    var response = syncBatch(itemsArray);

    assertHridError(response, duplicateHrid);
    itemsArray.forEach(item -> assertGetNotFound(ResourcePaths.ITEMS + "/" + ((JsonObject) item).getString("id")));
  }

  @Test
  @DisplayName("should return 500 when hrid generation fails for a synchronous batch")
  void shouldReturn500_whenSynchronousBatchHridGenerationFails() {
    setItemSequence(99_999_999_999L);
    var itemsArray = threeItems();

    var response = syncBatch(itemsArray);

    assertThat(response.status()).isEqualTo(SC_INTERNAL_SERVER_ERROR);
    assertThat(response.body().toString()).contains("hrid_items_seq");
    itemsArray.forEach(item -> assertGetNotFound(ResourcePaths.ITEMS + "/" + ((JsonObject) item).getString("id")));
  }

  @Test
  @DisplayName("should replace an item at a specific location")
  void shouldReplaceItem_atSpecificLocation() {
    var holdingId = createHoldingRecord();
    var id = UUID.randomUUID();
    createItem(smallAngryPlanet(id, holdingId).put("hrid", "testHRID"));

    var replacement = getItemJsonById(id.toString()).put("barcode", "125845734657")
      .put("temporaryLocationId", mainLibraryLocationId).put("tags", tags(TAG_VALUE));
    assertThat(updateItem(replacement).status()).isEqualTo(SC_NO_CONTENT);

    var item = getItemJsonById(id.toString());
    assertThat(item.getString("barcode")).isEqualTo("125845734657");
    assertThat(item.getString("temporaryLocationId")).isEqualTo(mainLibraryLocationId);
    assertThat(getTags(item)).containsExactly(TAG_VALUE);
  }

  @Test
  @DisplayName("should place an item in transit")
  void shouldPlaceItem_inTransit() {
    var holdingId = createHoldingRecord();
    var id = UUID.randomUUID();
    createItem(smallAngryPlanet(id, holdingId).put("hrid", "testHRID"));
    var inTransitServicePointId = UUID.randomUUID().toString();

    var replacement = getItemJsonById(id.toString())
      .put(STATUS_KEY, new JsonObject().put("name", "In transit"))
      .put("inTransitDestinationServicePointId", inTransitServicePointId).put("tags", tags(TAG_VALUE));
    assertThat(updateItem(replacement).status()).isEqualTo(SC_NO_CONTENT);

    var item = getItemJsonById(id.toString());
    assertThat(item.getJsonObject(STATUS_KEY).getString("name")).isEqualTo("In transit");
    assertThat(item.getString("inTransitDestinationServicePointId")).isEqualTo(inTransitServicePointId);
  }

  @Test
  @DisplayName("should set the status date when an item's status is updated")
  void shouldSetStatusDate_whenItemStatusUpdated() {
    var holdingId = createHoldingRecord();
    var id = UUID.randomUUID();
    var initialItem = createItem(smallAngryPlanet(id, holdingId).put("hrid", "testHRID")).mapTo(Item.class);
    var initialStatusDate = initialItem.getStatus().getDate().toInstant();

    var replacement = getItemJsonById(id.toString()).put(STATUS_KEY, new JsonObject().put("name", "Checked out"));
    assertThat(updateItem(replacement).status()).isEqualTo(SC_NO_CONTENT);

    var item = getItemJsonById(id.toString()).mapTo(Item.class);
    assertThat(item.getStatus().getName().value()).isEqualTo("Checked out");
    assertThat(item.getStatus().getDate().toInstant()).isAfter(initialStatusDate);
  }

  @Test
  @DisplayName("should change the status date when the item's status changes")
  void shouldChangeStatusDate_whenItemStatusChanges() {
    var holdingId = createHoldingRecord();
    var id = UUID.randomUUID();
    createItem(smallAngryPlanet(id, holdingId).put("hrid", "testHRID"));

    var checkedOutStatusDate = replaceStatusAndGetDate(id, "Checked out");
    var availableStatusDate = replaceStatusAndGetDate(id, "Available");

    assertThat(availableStatusDate).isAfter(checkedOutStatusDate);
  }

  @Test
  @DisplayName("should not allow the item status date to be set directly")
  void shouldNotAllowItemStatusDate_toBeSetDirectly() {
    var holdingId = createHoldingRecord();
    var id = UUID.randomUUID();
    var createdItem = createItem(smallAngryPlanet(id, holdingId));
    var initialStatusDate = createdItem.getJsonObject(STATUS_KEY).getString("date");

    var itemWithUpdatedStatus = getItemJsonById(id.toString())
      .put(STATUS_KEY, new JsonObject().put("name", "Checked out"));
    updateItem(itemWithUpdatedStatus);
    var updatedStatus = getItemJsonById(id.toString()).getJsonObject(STATUS_KEY);
    assertThat(updatedStatus.getString("name")).isEqualTo("Checked out");
    assertThat(Instant.parse(updatedStatus.getString("date"))).isAfter(Instant.parse(initialStatusDate));

    var itemWithUpdatedStatusDate = getItemJsonById(id.toString());
    itemWithUpdatedStatusDate.getJsonObject(STATUS_KEY).put("date", Instant.now().plusSeconds(86_400).toString());
    updateItem(itemWithUpdatedStatusDate);

    assertThat(getItemJsonById(id.toString()).getJsonObject(STATUS_KEY).getString("date"))
      .isEqualTo(updatedStatus.getString("date"));
  }

  @Test
  @DisplayName("should not change the item status date when the status has not changed")
  void shouldNotChangeItemStatusDate_whenStatusHasNotChanged() {
    var holdingId = createHoldingRecord();
    var id = UUID.randomUUID();
    createItem(smallAngryPlanet(id, holdingId));
    var initialStatusDate = getItemJsonById(id.toString()).getJsonObject(STATUS_KEY).getString("date");

    var itemWithUpdatedStatus = getItemJsonById(id.toString())
      .put(STATUS_KEY, new JsonObject().put("name", "Available").put("date", Instant.now().toString()));
    updateItem(itemWithUpdatedStatus);

    assertThat(getItemJsonById(id.toString()).getJsonObject(STATUS_KEY).getString("date")).isEqualTo(initialStatusDate);
  }

  @Test
  @DisplayName("should not change the status date after an update unrelated to status")
  void shouldNotChangeStatusDate_afterUnrelatedUpdate() {
    var holdingId = createHoldingRecord();
    var id = UUID.randomUUID();
    createItem(smallAngryPlanet(id, holdingId));
    var initialStatusDate = getItemJsonById(id.toString()).getJsonObject(STATUS_KEY).getString("date");

    var itemWithUpdatedStatus = getItemJsonById(id.toString())
      .put(STATUS_KEY, new JsonObject().put("name", "Checked out"));
    updateItem(itemWithUpdatedStatus);
    var checkedOutStatusDate = getItemJsonById(id.toString()).getJsonObject(STATUS_KEY).getString("date");
    assertThat(checkedOutStatusDate).isNotEqualTo(initialStatusDate);

    var itemWithUpdatedCallNumber = getItemJsonById(id.toString()).put("itemLevelCallNumber", "newItemLevelCallNumber");
    updateItem(itemWithUpdatedCallNumber);

    var afterCallNumberUpdate = getItemJsonById(id.toString());
    assertThat(afterCallNumberUpdate.getString("itemLevelCallNumber")).isEqualTo("newItemLevelCallNumber");
    assertThat(afterCallNumberUpdate.getJsonObject(STATUS_KEY).getString("date")).isEqualTo(checkedOutStatusDate);
  }

  @Test
  @DisplayName("should delete an item")
  void shouldDeleteItem() {
    var holdingId = createHoldingRecord();
    var id = UUID.randomUUID();
    createItem(smallAngryPlanet(id, holdingId));

    var deleteResponse = await(doDelete(client, ResourcePaths.ITEMS + "/" + id));

    assertThat(deleteResponse.status()).isEqualTo(SC_NO_CONTENT);
    assertGetNotFound(ResourcePaths.ITEMS + "/" + id);
  }

  @Test
  @DisplayName("should page through all items")
  void shouldPageThroughAllItems() {
    var holdingId = createHoldingRecord();
    createFiveItems(holdingId);

    var firstPage = await(doGet(client, ResourcePaths.ITEMS + "?limit=3&offset=0")).jsonBody();
    var secondPage = await(doGet(client, ResourcePaths.ITEMS + "?limit=3&offset=3")).jsonBody();

    assertThat(firstPage.getJsonArray(ITEMS_KEY)).hasSize(3);
    assertThat(secondPage.getJsonArray(ITEMS_KEY)).hasSize(2);
  }

  @Test
  @DisplayName("should retrieve items via the retrieve endpoint")
  void shouldRetrieveItems_viaRetrieveEndpoint() {
    var holdingId = createHoldingRecord();
    var itemIds = new ArrayList<String>();
    for (var i = 0; i < 5; i++) {
      itemIds.add(createItem(minimalItemRequest(UUID.randomUUID(), holdingId)
        .put("barcode", UUID.randomUUID().toString())).getString("id"));
    }
    var query = "id==(" + String.join(" or ", itemIds) + ")";

    var response = await(doPost(client, ResourcePaths.ITEMS_RETRIEVE, new JsonObject().put("query", query)
      .put("limit", 2000))).jsonBody();

    assertThat(response.getJsonArray(ITEMS_KEY)).hasSize(5);
    assertThat(response.getInteger("totalRecords")).isEqualTo(5);
  }

  @Test
  @DisplayName("should page through all items via the retrieve endpoint")
  void shouldPageThroughAllItems_viaRetrieveEndpoint() {
    var holdingId = createHoldingRecord();
    createFiveItems(holdingId);

    var firstPage = await(doPost(client, ResourcePaths.ITEMS_RETRIEVE,
      new JsonObject().put("limit", 3).put("offset", 0))).jsonBody();
    var secondPage = await(doPost(client, ResourcePaths.ITEMS_RETRIEVE,
      new JsonObject().put("limit", 3).put("offset", 3))).jsonBody();

    assertThat(firstPage.getJsonArray(ITEMS_KEY)).hasSize(3);
    assertThat(secondPage.getJsonArray(ITEMS_KEY)).hasSize(2);
  }

  @Test
  @DisplayName("should reset the item order when all items are deleted and a new one is created")
  void shouldResetItemOrder_whenAllItemsAreDeletedAndNewOneIsCreated() {
    var holdingId = createHoldingRecord();
    var item1 = smallAngryPlanet(UUID.randomUUID(), holdingId);
    var item2 = smallAngryPlanet(UUID.randomUUID(), holdingId).put("barcode", "1234567890");
    createItem(item1);
    createItem(item2);
    assertThat(getMaxOrder(holdingId)).isEqualTo(2);

    assertThat(await(doDelete(client, ResourcePaths.ITEMS + "?query=holdingsRecordId==" + holdingId)).status())
      .isEqualTo(SC_NO_CONTENT);
    assertGetNotFound(ResourcePaths.ITEMS + "/" + item1.getString("id"));
    assertGetNotFound(ResourcePaths.ITEMS + "/" + item2.getString("id"));
    assertThat(getMaxOrder(holdingId)).isEqualTo(2);

    var newItem = createItem(item2);

    assertThat(getMaxOrder(holdingId)).isEqualTo(1);
    assertThat(newItem.getInteger(ORDER_FIELD)).isEqualTo(1);
  }

  @Test
  @DisplayName("should set the new item order to the next value even after an item is deleted")
  void shouldSetNewItemOrder_toNextValueEvenAfterItemDeletion() {
    var holdingId = createHoldingRecord();
    var item1 = createItem(smallAngryPlanet(UUID.randomUUID(), holdingId));
    var item2 = createItem(smallAngryPlanet(UUID.randomUUID(), holdingId).put("barcode", "1234567890"));
    assertThat(item1.getInteger(ORDER_FIELD)).isEqualTo(1);
    assertThat(item2.getInteger(ORDER_FIELD)).isEqualTo(2);
    assertThat(getMaxOrder(holdingId)).isEqualTo(2);

    assertThat(await(doDelete(client, ResourcePaths.ITEMS + "?query=id==" + item2.getString("id"))).status())
      .isEqualTo(SC_NO_CONTENT);
    assertGetNotFound(ResourcePaths.ITEMS + "/" + item2.getString("id"));
    assertThat(getMaxOrder(holdingId)).isEqualTo(2);

    var item3 = createItem(smallAngryPlanet(UUID.randomUUID(), holdingId).put("barcode", "1111111111"));
    assertThat(item3.getInteger(ORDER_FIELD)).isEqualTo(3);
    assertThat(getMaxOrder(holdingId)).isEqualTo(3);
  }

  @Test
  @DisplayName("should create multiple items without a barcode")
  void shouldCreateMultipleItems_withoutBarcode() {
    var holdingId = createHoldingRecord();

    createItem(removeBarcode(smallAngryPlanet(UUID.randomUUID(), holdingId)));
    createItem(removeBarcode(smallAngryPlanet(UUID.randomUUID(), holdingId)));
    createItem(smallAngryPlanet(UUID.randomUUID(), holdingId).putNull("barcode"));
    createItem(smallAngryPlanet(UUID.randomUUID(), holdingId).putNull("barcode"));

    var response = await(doGet(client, ResourcePaths.ITEMS + "?query=" + urlEncode("id==*"))).jsonBody();
    assertThat(response.getInteger("totalRecords")).isEqualTo(4);
  }

  @Test
  @DisplayName("should return 400 when creating an item with a duplicate barcode")
  void shouldReturn400_whenCreatingItemWithDuplicateBarcode() {
    var holdingId = createHoldingRecord();
    createItem(smallAngryPlanet(UUID.randomUUID(), holdingId).put("barcode", "9876a"));

    var response1 = await(doPost(client, ResourcePaths.ITEMS,
      smallAngryPlanet(UUID.randomUUID(), holdingId).put("barcode", "9876a")));
    var response2 = await(doPost(client, ResourcePaths.ITEMS,
      smallAngryPlanet(UUID.randomUUID(), holdingId).put("barcode", "9876A")));

    assertThat(response1.body().toString()).contains("9876a");
    assertThat(response2.body().toString()).contains("9876a");
  }

  @Test
  @DisplayName("should return 400 when updating an item with a duplicate barcode")
  void shouldReturn400_whenUpdatingItemWithDuplicateBarcode() {
    var holdingId = createHoldingRecord();
    createItem(smallAngryPlanet(UUID.randomUUID(), holdingId).put("barcode", "9876a"));
    var otherItemId = UUID.randomUUID();
    var otherItem = createItem(smallAngryPlanet(otherItemId, holdingId).put("barcode", "123"));

    var response = updateItem(otherItem.put("barcode", "9876A"));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString()).contains("already exists in table item: 9876a");
  }

  @Test
  @DisplayName("should batch create items")
  void shouldBatchCreateItems() {
    var itemsArray = threeItems();

    assertThat(syncBatch(itemsArray).status()).isEqualTo(SC_CREATED);

    itemsArray.forEach(item -> {
      var itemFromGet = getItemJsonById(((JsonObject) item).getString("id"));
      assertThat(itemFromGet.getString("hrid")).isNotNull();
      assertThat(itemFromGet.getInteger(ORDER_FIELD)).isIn(1, 2, 3);
    });
  }

  @Test
  @DisplayName("should return 422 when creating an item with a non-existent holdings record id")
  void shouldReturn422_whenCreatingItemWithNonExistentHoldingsRecordId() {
    var nonExistingHoldingId = UUID.randomUUID();
    var itemToCreate = new ItemRequestBuilder().forHolding(nonExistingHoldingId).withMaterialType(
      UUID.fromString(materialTypeId)).withPermanentLoanType(UUID.fromString(loanTypeId)).create();

    var response = await(doPost(client, ResourcePaths.ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    assertThat(response.body().toString()).contains("Holdings record does not exist");
  }

  @Test
  @DisplayName("should return 422 when a synchronous batch has a non-existent holdings record id")
  void shouldReturn422_whenSynchronousBatchHasNonExistentHoldingsRecordId() {
    var itemsArray = threeItems();
    itemsArray.getJsonObject(2).put("holdingsRecordId", UUID.randomUUID().toString());

    var response = syncBatch(itemsArray);

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    assertThat(response.body().toString()).contains("Holdings record does not exist");
  }

  @Test
  @DisplayName("should create an item with a last check-in")
  void shouldCreateItem_withLastCheckIn() {
    var holdingId = createHoldingRecord();
    var itemId = UUID.randomUUID();
    var itemData = smallAngryPlanet(itemId, holdingId).put("hrid", "testHRID");
    createItem(itemData);
    var expected = new ItemLastCheckIn().withStaffMemberId(UUID.randomUUID().toString())
      .withServicePointId(UUID.randomUUID().toString()).withDateTime(new java.util.Date());

    itemData.put("lastCheckIn", pojo2JsonObject(expected));
    assertThat(updateItem(itemData.put("id", itemId.toString())).status()).isEqualTo(SC_NO_CONTENT);

    var actual = getItemJsonById(itemId.toString()).getJsonObject("lastCheckIn").mapTo(ItemLastCheckIn.class);
    assertThat(actual.getDateTime()).isEqualTo(expected.getDateTime());
    assertThat(actual.getServicePointId()).isEqualTo(expected.getServicePointId());
    assertThat(actual.getStaffMemberId()).isEqualTo(expected.getStaffMemberId());
  }

  @Test
  @DisplayName("should return 400 when creating an item with an unknown status")
  void shouldReturn400_whenCreatingItemWithUnknownStatus() {
    var itemToCreate = new JsonObject().put("id", UUID.randomUUID().toString())
      .put(STATUS_KEY, new JsonObject().put("name", "Wrong status name"));

    var response = await(doPost(client, ResourcePaths.ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString()).contains("problem: Wrong status name");
  }

  @Test
  @DisplayName("should return 422 when removing the item status")
  void shouldReturn422_whenRemovingItemStatus() {
    var holdingId = createHoldingRecord();
    var id = UUID.randomUUID();
    createItem(smallAngryPlanet(id, holdingId));
    var replacement = getItemJsonById(id.toString());
    replacement.remove(STATUS_KEY);

    var response = updateItem(replacement);

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    var errors = response.jsonBody().mapTo(Errors.class).getErrors();
    assertThat(errors).hasSize(1);
    assertThat(errors.getFirst().getMessage()).isIn("may not be null", "must not be null");
    assertThat(errors.getFirst().getParameters().getFirst().getKey()).isEqualTo(STATUS_KEY);
  }

  @Test
  @DisplayName("should return 422 when removing the item status name")
  void shouldReturn422_whenRemovingItemStatusName() {
    var holdingId = createHoldingRecord();
    var id = UUID.randomUUID();
    createItem(smallAngryPlanet(id, holdingId));
    var replacement = getItemJsonById(id.toString());
    replacement.getJsonObject(STATUS_KEY).remove("name");

    var response = updateItem(replacement);

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    var errors = response.jsonBody().mapTo(Errors.class).getErrors();
    assertThat(errors).hasSize(1);
    assertThat(errors.getFirst().getMessage()).isIn("may not be null", "must not be null");
    assertThat(errors.getFirst().getParameters().getFirst().getKey()).isEqualTo("status.name");
  }

  @Test
  @DisplayName("should return 422 when a synchronous batch item is missing a status")
  void shouldReturn422_whenSynchronousBatchItemMissingStatus() {
    var itemsArray = threeItems();
    itemsArray.getJsonObject(1).remove(STATUS_KEY);

    var response = syncBatch(itemsArray);

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    itemsArray.forEach(item -> assertGetNotFound(ResourcePaths.ITEMS + "/" + ((JsonObject) item).getString("id")));
  }

  @Test
  @DisplayName("should set the status date for items created via a synchronous batch")
  void shouldSetStatusDate_forItemsCreatedViaSynchronousBatch() {
    var itemsArray = threeItems();

    assertThat(syncBatch(itemsArray).status()).isEqualTo(SC_CREATED);

    itemsArray.forEach(item -> assertThat(getItemJsonById(((JsonObject) item).getString("id"))
      .getJsonObject(STATUS_KEY).getString("date")).isNotNull());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "Aged to lost", "Available", "Awaiting pickup", "Awaiting delivery", "Checked out", "Claimed returned",
    "Declared lost", "In process", "In process (non-requestable)", "In transit", "Intellectual item",
    "Long missing", "Lost and paid", "Missing", "On order", "Paged", "Restricted", "Order closed",
    "Unavailable", "Unknown", "Withdrawn"
  })
  @DisplayName("should create an item with each allowed status")
  void shouldCreateItem_withEachAllowedStatus(String status) {
    var holdingId = createHoldingRecord();
    var itemToCreate = new ItemRequestBuilder().forHolding(UUID.fromString(holdingId))
      .withMaterialType(UUID.fromString(materialTypeId)).withPermanentLoanType(UUID.fromString(loanTypeId))
      .withStatus(status);

    var item = createItem(itemToCreate.create());

    assertThat(item.getJsonObject(STATUS_KEY).getString("name")).isEqualTo(status);
    assertThat(getItemJsonById(item.getString("id")).getJsonObject(STATUS_KEY).getString("name")).isEqualTo(status);
  }

  @Test
  @DisplayName("should return 422 when the statistical code id does not exist")
  void shouldReturn422_whenStatisticalCodeIdDoesNotExist() {
    var holdingId = createHoldingRecord();
    var nonExistingStatisticalCodeId = UUID.randomUUID();
    var itemToCreate = new ItemRequestBuilder().forHolding(UUID.fromString(holdingId))
      .withMaterialType(UUID.fromString(materialTypeId)).withPermanentLoanType(UUID.fromString(loanTypeId))
      .withStatisticalCodeIds(List.of(nonExistingStatisticalCodeId)).create();

    var response = await(doPost(client, ResourcePaths.ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    assertThat(response.body().toString()).contains(("Cannot set item.statistical_code_id = %s "
                                                     + "because it does not exist in statistical_code.id.").formatted(
      nonExistingStatisticalCodeId));
  }

  @Test
  @DisplayName("should create an item with multiple statistical code ids")
  void shouldCreateItem_withMultipleStatisticalCodeIds() {
    var holdingId = createHoldingRecord();
    var firstStatisticalCodeId = createStatisticalCode(client);
    var secondStatisticalCodeId = createStatisticalCode(client);
    var statisticalCodeIds = List.of(UUID.fromString(firstStatisticalCodeId), UUID.fromString(secondStatisticalCodeId));
    var itemToCreate = new ItemRequestBuilder().forHolding(UUID.fromString(holdingId))
      .withMaterialType(UUID.fromString(materialTypeId)).withPermanentLoanType(UUID.fromString(loanTypeId))
      .withStatisticalCodeIds(statisticalCodeIds).create();

    var response = await(doPost(client, ResourcePaths.ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_CREATED);
  }

  @Test
  @DisplayName("should return 422 when at least one statistical code id does not exist")
  void shouldReturn422_whenAtLeastOneStatisticalCodeIdDoesNotExist() {
    var holdingId = createHoldingRecord();
    var statisticalCodeId = createStatisticalCode(client);
    var nonExistingStatisticalCodeId = UUID.randomUUID();
    var itemToCreate = new ItemRequestBuilder().forHolding(UUID.fromString(holdingId))
      .withMaterialType(UUID.fromString(materialTypeId)).withPermanentLoanType(UUID.fromString(loanTypeId))
      .withStatisticalCodeIds(List.of(UUID.fromString(statisticalCodeId), nonExistingStatisticalCodeId)).create();

    var response = await(doPost(client, ResourcePaths.ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    assertThat(response.body().toString()).contains(nonExistingStatisticalCodeId.toString());
  }

  @Test
  @DisplayName("should update an item with a statistical code id")
  void shouldUpdateItem_withStatisticalCodeId() {
    var holdingId = createHoldingRecord();
    var statisticalCodeId = createStatisticalCode(client);
    var item = createItem(minimalItemRequest(UUID.randomUUID(), holdingId).put("hrid", "testHRID"));
    item.put(STATISTICAL_CODE_IDS_KEY, List.of(statisticalCodeId));

    assertThat(updateItem(item).status()).isEqualTo(SC_NO_CONTENT);
  }

  @Test
  @DisplayName("should return 400 when updating an item with a non-existent statistical code id")
  void shouldReturn400_whenUpdatingItemWithNonExistentStatisticalCodeId() {
    var holdingId = createHoldingRecord();
    var nonExistingStatisticalCodeId = UUID.randomUUID();
    var item = createItem(minimalItemRequest(UUID.randomUUID(), holdingId).put("hrid", "testHRID"));
    item.put(STATISTICAL_CODE_IDS_KEY, List.of(nonExistingStatisticalCodeId.toString()));

    var response = updateItem(item);

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body()).hasToString(("Cannot set item statistical_code_id = %s "
                                             + "because it does not exist in statistical_code.id.").formatted(
      nonExistingStatisticalCodeId));
  }

  @Test
  @DisplayName("should return 422 when the permanent loan type does not exist")
  void shouldReturn422_whenPermanentLoanTypeDoesNotExist() {
    var holdingId = createHoldingRecord();

    var response = await(doPost(client, ResourcePaths.ITEMS,
      itemRequestForLoanTypes(holdingId, UUID.randomUUID().toString(), null)));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  @DisplayName("should return 422 when the temporary loan type does not exist")
  void shouldReturn422_whenTemporaryLoanTypeDoesNotExist() {
    var holdingId = createHoldingRecord();

    var response = await(doPost(client, ResourcePaths.ITEMS,
      itemRequestForLoanTypes(holdingId, loanTypeId, UUID.randomUUID().toString())));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  @DisplayName("should return 400 when updating an item with a non-existing permanent loan type id")
  void shouldReturn400_whenUpdatingItemWithNonExistingPermanentLoanTypeId() {
    var holdingId = createHoldingRecord();
    var item = createItem(itemRequestForLoanTypes(holdingId, loanTypeId, loanTypeId));

    var response = updateItem(item.put("permanentLoanTypeId", UUID.randomUUID().toString()));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
  }

  @Test
  @DisplayName("should return 400 when updating an item with a non-existing temporary loan type id")
  void shouldReturn400_whenUpdatingItemWithNonExistingTemporaryLoanTypeId() {
    var holdingId = createHoldingRecord();
    var item = createItem(itemRequestForLoanTypes(holdingId, loanTypeId, loanTypeId));

    var response = updateItem(item.put("temporaryLoanTypeId", UUID.randomUUID().toString()));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
  }

  @Test
  @DisplayName("should return 400 when deleting a loan type permanently associated with an item")
  void shouldReturn400_whenDeletingLoanTypePermanentlyAssociatedWithItem() {
    var holdingId = createHoldingRecord();
    createItem(itemRequestForLoanTypes(holdingId, loanTypeId, null));

    var response = await(doDelete(client, ResourcePaths.LOAN_TYPES + "/" + loanTypeId));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
  }

  @Test
  @DisplayName("should return 400 when deleting a loan type temporarily associated with an item")
  void shouldReturn400_whenDeletingLoanTypeTemporarilyAssociatedWithItem() {
    var holdingId = createHoldingRecord();
    createItem(itemRequestForLoanTypes(holdingId, secondLoanTypeId, loanTypeId));

    var response = await(doDelete(client, ResourcePaths.LOAN_TYPES + "/" + loanTypeId));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
  }

  @Test
  @DisplayName("should return 400 when deleting a material type associated with an item")
  void shouldReturn400_whenDeletingMaterialTypeAssociatedWithItem() {
    var holdingId = createHoldingRecord();
    createItem(itemRequestForLoanTypes(holdingId, loanTypeId, null));

    var response = await(doDelete(client, ResourcePaths.MATERIAL_TYPES + "/" + materialTypeId));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
  }

  @Test
  @DisplayName("should return 400 when deleting a location associated with an item")
  void shouldReturn400_whenDeletingLocationAssociatedWithItem() {
    var holdingId = createHoldingRecord();
    createItem(smallAngryPlanet(UUID.randomUUID(), holdingId));

    var response = await(doDelete(client, ResourcePaths.LOCATIONS + "/" + annexLibraryLocationId));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
  }

  @Test
  @DisplayName("should create an item with a minimal additional call number")
  void shouldCreateItem_withMinimalAdditionalCallNumber() {
    var holdingId = createHoldingRecord();
    var itemToCreate = minimalItemRequest(UUID.randomUUID(), holdingId).put("hrid", "123456789")
      .put("additionalCallNumbers", new JsonArray().add(additionalCallNumber("This is the only mandatory field",
        null, null, null)));

    var response = await(doPost(client, ResourcePaths.ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_CREATED);
  }

  @Test
  @DisplayName("should return 422 when an additional call number is missing its call number")
  void shouldReturn422_whenAdditionalCallNumberMissingCallNumber() {
    var holdingId = createHoldingRecord();
    var itemToCreate = minimalItemRequest(UUID.randomUUID(), holdingId).put("hrid", "123456789")
      .put("additionalCallNumbers", new JsonArray().add(
        additionalCallNumber(null, "prefix", "suffix", lcCallNumberTypeId)));

    var response = await(doPost(client, ResourcePaths.ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  @DisplayName("should create and update an item with additional call numbers")
  void shouldCreateAndUpdateItem_withAdditionalCallNumbers() {
    var holdingId = createHoldingRecord();
    var itemToCreate = minimalItemRequest(UUID.randomUUID(), holdingId).put("hrid", "123456789")
      .put("additionalCallNumbers", new JsonArray().add(
        additionalCallNumber("Test", "A", "Z", lcCallNumberTypeId)));
    setItemSequence(1);

    var item = createItem(itemToCreate);
    var created = item.getJsonArray("additionalCallNumbers").getJsonObject(0);
    assertThat(created.getString("callNumber")).isEqualTo("Test");
    assertThat(created.getString("prefix")).isEqualTo("A");
    assertThat(created.getString("suffix")).isEqualTo("Z");
    assertThat(created.getString("typeId")).isEqualTo(lcCallNumberTypeId);

    item.getJsonArray("additionalCallNumbers").add(additionalCallNumber("some", "prefix", "suffix",
      lcCallNumberTypeId));
    assertThat(updateItem(item).status()).isEqualTo(SC_NO_CONTENT);

    assertThat(getItemJsonById(item.getString("id")).getJsonArray("additionalCallNumbers")).hasSize(2);
  }

  @Test
  @DisplayName("should delete the additional call numbers from an item")
  void shouldDeleteAdditionalCallNumbers_fromItem() {
    var holdingId = createHoldingRecord();
    var itemToCreate = minimalItemRequest(UUID.randomUUID(), holdingId).put("additionalCallNumbers",
      new JsonArray().add(additionalCallNumber("Test", "A", "Z", lcCallNumberTypeId)));
    var item = createItem(itemToCreate);

    item.remove("additionalCallNumbers");
    assertThat(updateItem(item).status()).isEqualTo(SC_NO_CONTENT);

    var updated = getItemJsonById(item.getString("id"));
    assertThat(updated.containsKey("additionalCallNumbers")).isTrue();
    assertThat(updated.getJsonArray("additionalCallNumbers")).isEmpty();
  }

  @Test
  @DisplayName("should create an item with empty additional call numbers")
  void shouldCreateItem_withEmptyAdditionalCallNumbers() {
    var holdingId = createHoldingRecord();
    var itemToCreate = minimalItemRequest(UUID.randomUUID(), holdingId).put("hrid", "123456789")
      .put("additionalCallNumbers", new JsonArray());

    var response = await(doPost(client, ResourcePaths.ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_CREATED);
  }

  @Test
  @DisplayName("should return 422 when an additional call number type id is not a UUID")
  void shouldReturn422_whenAdditionalCallNumberTypeIdIsNotUuid() {
    var holdingId = createHoldingRecord();
    var itemToCreate = minimalItemRequest(UUID.randomUUID(), holdingId).put("hrid", "123456789")
      .put("additionalCallNumbers", new JsonArray().add(additionalCallNumber("Test", "A", "Z", "non-uuid")));

    var response = await(doPost(client, ResourcePaths.ITEMS, itemToCreate));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  @DisplayName("should delete all items")
  void shouldDeleteAllItems() {
    var holdingId = createHoldingRecord();
    createItem(smallAngryPlanet(UUID.randomUUID(), holdingId).put("barcode", UUID.randomUUID().toString()));
    createItem(smallAngryPlanet(UUID.randomUUID(), holdingId).put("barcode", UUID.randomUUID().toString()));
    createItem(smallAngryPlanet(UUID.randomUUID(), holdingId).put("barcode", UUID.randomUUID().toString()));

    var deleteResponse = await(doDelete(client, ResourcePaths.ITEMS + "?query=cql.allRecords=1"));

    assertThat(deleteResponse.status()).isEqualTo(SC_NO_CONTENT);
    var response = await(doGet(client, ResourcePaths.ITEMS)).jsonBody();
    assertThat(response.getJsonArray(ITEMS_KEY)).isEmpty();
    assertThat(response.getInteger("totalRecords")).isZero();
  }

  @Test
  @DisplayName("should delete items matching a CQL query")
  void shouldDeleteItems_matchingCqlQuery() {
    var holdingId = createHoldingRecord();
    final var item1 = createItem(smallAngryPlanet(UUID.randomUUID(), holdingId).put("barcode", "1234"));
    var item2 = createItem(smallAngryPlanet(UUID.randomUUID(), holdingId).put("barcode", "23"));
    final var item3 = createItem(smallAngryPlanet(UUID.randomUUID(), holdingId).put("barcode", "12"));
    var item4 = createItem(smallAngryPlanet(UUID.randomUUID(), holdingId).put("barcode", "234"));
    final var item5 = createItem(smallAngryPlanet(UUID.randomUUID(), holdingId).put("barcode", "123"));

    var response = await(doDelete(client, ResourcePaths.ITEMS + "?query=barcode==12*"));

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
    assertExists(item2);
    assertExists(item4);
    assertNotExists(item1);
    assertNotExists(item3);
    assertNotExists(item5);
  }

  @ValueSource(strings = {"", "?query=", "?query=%20%20"})
  @ParameterizedTest
  @DisplayName("should return 400 when deleting items without a CQL query")
  void shouldReturn400_whenDeletingItemsWithoutCql(String query) {
    var response = await(doDelete(client, ResourcePaths.ITEMS + query));

    assertThat(response.body()).hasToString("Expected CQL but query parameter is empty");
    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
  }

  @Test
  @DisplayName("should return 400 when deleting items with an invalid CQL query")
  void shouldReturn400_whenDeletingItemsWithInvalidCql() {
    var response = await(doDelete(client, ResourcePaths.ITEMS + "?query=\""));

    assertThat(response.body().toString().toLowerCase()).contains("parse");
    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
  }

  // -- shared helpers --

  private static String createHoldingRecord() {
    return createHoldingRecord(mainLibraryLocationId);
  }

  private static String createHoldingRecord(String locationId) {
    var instanceId = createInstance(client, "an instance " + UUID.randomUUID(), instanceTypeId);
    return HoldingsStorageFixtures.createHolding(client, instanceId, locationId);
  }

  private static String createHoldingRecordWithCallNumber(String callNumber) {
    var instanceId = createInstance(client, "an instance " + UUID.randomUUID(), instanceTypeId);
    var request = new HoldingRequestBuilder().forInstance(UUID.fromString(instanceId))
      .withSource(UUID.fromString(createHoldingsRecordsSource(client)))
      .withPermanentLocation(UUID.fromString(mainLibraryLocationId)).withCallNumber(callNumber).create();
    return await(doPost(client, ResourcePaths.HOLDINGS, request)).jsonBody().getString("id");
  }

  private static JsonObject minimalItemRequest(UUID id, String holdingId) {
    var item = new JsonObject().put(STATUS_KEY, new JsonObject().put("name", "Available"))
      .put("holdingsRecordId", holdingId).put("materialTypeId", materialTypeId)
      .put("permanentLoanTypeId", loanTypeId).put("tags", tags(TAG_VALUE));
    if (id != null) {
      item.put("id", id.toString());
    }
    return item;
  }

  private static JsonObject smallAngryPlanet(UUID itemId, String holdingId) {
    var item = minimalItemRequest(itemId, holdingId).put("barcode", "036000291452")
      .put("temporaryLocationId", annexLibraryLocationId).put("_version", 1);
    item.remove("tags");
    return item;
  }

  private static JsonObject itemRequestWithCallNumberComponents(String holdingId, String prefix, String callNumber,
                                                                String volume, String enumeration,
                                                                String chronology, String copy, String suffix) {
    return minimalItemRequest(UUID.randomUUID(), holdingId).put("barcode", "565578437802")
      .put("temporaryLocationId", annexLibraryLocationId).put("tags", tags(TAG_VALUE))
      .put("itemLevelCallNumber", callNumber).put("itemLevelCallNumberSuffix", suffix)
      .put("itemLevelCallNumberPrefix", prefix).put("itemLevelCallNumberTypeId", lcCallNumberTypeId)
      .put("volume", volume).put("enumeration", enumeration).put("chronology", chronology).put("copyNumber", copy)
      .put("inTransitDestinationServicePointId", UUID.randomUUID().toString());
  }

  private static JsonObject detailedItemRequest(UUID id, String holdingId, String adminNote, String displaySummary,
                                                String inTransitServicePointId, String statisticalCodeId) {
    return minimalItemRequest(id, holdingId).put(ORDER_FIELD, 100)
      .put("administrativeNotes", new JsonArray().add(adminNote)).put("barcode", "565578437802")
      .put("displaySummary", displaySummary).put("temporaryLocationId", annexLibraryLocationId)
      .put("copyNumber", "copy1").put("itemLevelCallNumber", "PS3623.R534 P37 2005")
      .put("itemLevelCallNumberSuffix", "allOwnComponentsCNS").put("itemLevelCallNumberPrefix", "allOwnComponentsCNP")
      .put("itemLevelCallNumberTypeId", lcCallNumberTypeId).put(STATISTICAL_CODE_IDS_KEY, List.of(statisticalCodeId))
      .put("inTransitDestinationServicePointId", inTransitServicePointId);
  }

  private static void assertDetailedItem(JsonObject item, UUID id, String adminNote, String holdingId,
                                         String displaySummary, String inTransitServicePointId,
                                         String statisticalCodeId) {
    assertThat(item.getString("id")).isEqualTo(id.toString());
    assertThat(item.getInteger(ORDER_FIELD)).isEqualTo(100);
    assertThat(item.getJsonArray("administrativeNotes")).contains(adminNote);
    assertThat(item.getString("holdingsRecordId")).isEqualTo(holdingId);
    assertThat(item.getString("barcode")).isEqualTo("565578437802");
    assertThat(item.getJsonObject(STATUS_KEY).getString("name")).isEqualTo("Available");
    assertThat(item.getString("displaySummary")).isEqualTo(displaySummary);
    assertThat(item.getString("temporaryLocationId")).isEqualTo(annexLibraryLocationId);
    assertThat(item.getString("inTransitDestinationServicePointId")).isEqualTo(inTransitServicePointId);
    assertThat(item.getString("hrid")).isNotNull();
    assertThat(getTags(item)).containsExactly(TAG_VALUE);
    assertThat(item.getString("copyNumber")).isEqualTo("copy1");
    assertThat(item.getJsonArray(STATISTICAL_CODE_IDS_KEY)).contains(statisticalCodeId);
  }

  private static JsonObject itemRequestWithCirculationNotes(String holdingId, UUID itemId, UUID circulationNoteId) {
    return minimalItemRequest(itemId, holdingId).put("circulationNotes", circulationNotes(circulationNoteId));
  }

  private static JsonArray circulationNotes(UUID circulationNoteId) {
    var checkInNote = new JsonObject().put("noteType", "Check in").put("note", "Check in note")
      .put("staffOnly", false);
    var checkOutNote = new JsonObject().put("id", circulationNoteId.toString()).put("noteType", "Check out")
      .put("note", "Check out note").put("staffOnly", false);
    return new JsonArray().add(checkInNote).add(checkOutNote);
  }

  private static void assertCirculationNotesPopulated(JsonObject item, UUID circulationNoteId) {
    var notes = item.getJsonArray("circulationNotes");
    assertThat(notes).hasSize(2);
    notes.forEach(note -> {
      var noteJson = (JsonObject) note;
      assertThat(noteJson.getString("id")).isNotNull();
      if ("Check out".equals(noteJson.getString("noteType"))) {
        assertThat(noteJson.getString("id")).isEqualTo(circulationNoteId.toString());
      }
    });
  }

  private static JsonObject additionalCallNumber(String callNumber, String prefix, String suffix, String typeId) {
    var json = new JsonObject();
    json.put("callNumber", callNumber);
    json.put("prefix", prefix);
    json.put("suffix", suffix);
    json.put("typeId", typeId);
    return json;
  }

  private static JsonObject itemRequestForLoanTypes(String holdingId, String permanentLoanTypeId,
                                                    String temporaryLoanTypeId) {
    var item = new JsonObject().put(STATUS_KEY, new JsonObject().put("name", "Available"))
      .put("holdingsRecordId", holdingId).put("barcode", UUID.randomUUID().toString())
      .put("materialTypeId", materialTypeId);
    if (permanentLoanTypeId != null) {
      item.put("permanentLoanTypeId", permanentLoanTypeId);
    }
    if (temporaryLoanTypeId != null) {
      item.put("temporaryLoanTypeId", temporaryLoanTypeId);
    }
    return item;
  }

  private static JsonObject removeBarcode(JsonObject item) {
    item.remove("barcode");
    return item;
  }

  private static JsonObject tags(String... tagValues) {
    return new JsonObject().put("tagList", new JsonArray(List.of(tagValues)));
  }

  private static List<String> getTags(JsonObject item) {
    return item.getJsonObject("tags").getJsonArray("tagList").stream().map(String.class::cast).toList();
  }

  private static JsonArray threeItems() {
    var holdingId = createHoldingRecordWithCallNumber("hrCallNumber");
    return new JsonArray()
      .add(minimalItemRequest(UUID.randomUUID(), holdingId).put("barcode", UUID.randomUUID().toString()))
      .add(minimalItemRequest(UUID.randomUUID(), holdingId).put("barcode", UUID.randomUUID().toString()))
      .add(minimalItemRequest(UUID.randomUUID(), holdingId).put("barcode", UUID.randomUUID().toString()));
  }

  private static JsonArray threeItemsWithId(String holdingId, UUID existingItemId) {
    // _version=1 matches the existing item's stored version after its first upsert (an insert),
    // so a second upsert reusing existingItemId is accepted as a same-version update rather than
    // rejected by optimistic locking.
    var array = new JsonArray()
      .add(minimalItemRequest(existingItemId, holdingId).put("barcode", UUID.randomUUID().toString())
        .put("_version", 1))
      .add(minimalItemRequest(UUID.randomUUID(), holdingId).put("barcode", UUID.randomUUID().toString())
        .put("_version", 1))
      .add(minimalItemRequest(UUID.randomUUID(), holdingId).put("barcode", UUID.randomUUID().toString())
        .put("_version", 1));
    for (var i = 0; i < array.size(); i++) {
      array.getJsonObject(i).put(ORDER_FIELD, i);
    }
    return array;
  }

  private static void createFiveItems(String holdingId) {
    for (var i = 0; i < 5; i++) {
      createItem(minimalItemRequest(UUID.randomUUID(), holdingId).put("barcode", UUID.randomUUID().toString()));
    }
  }

  private static JsonObject createItem(JsonObject request) {
    var response = await(doPost(client, ResourcePaths.ITEMS, request));
    assertThat(response.status()).isEqualTo(SC_CREATED);
    return response.jsonBody();
  }

  private static TestResponse getItemById(String id) {
    return await(doGet(client, ResourcePaths.ITEMS + "/" + id));
  }

  private static JsonObject getItemJsonById(String id) {
    return getItemById(id).jsonBody();
  }

  private static TestResponse updateItem(JsonObject item) {
    return await(doPut(client, ResourcePaths.ITEMS + "/" + item.getString("id"), item));
  }

  private static Instant replaceStatusAndGetDate(UUID id, String status) {
    var replacement = getItemJsonById(id.toString()).put(STATUS_KEY, new JsonObject().put("name", status));
    assertThat(updateItem(replacement).status()).isEqualTo(SC_NO_CONTENT);
    return Instant.parse(getItemJsonById(id.toString()).getJsonObject(STATUS_KEY).getString("date"));
  }

  private static void assertGetNotFound(String path) {
    assertThat(await(doGet(client, path)).status()).isEqualTo(SC_NOT_FOUND);
  }

  private static void assertExists(JsonObject expectedItem) {
    var response = await(doGet(client, ResourcePaths.ITEMS + "/" + expectedItem.getString("id")));
    assertThat(response.status()).isEqualTo(SC_OK);
    assertThat(response.body().toString()).contains(expectedItem.getString("holdingsRecordId"));
  }

  private static void assertNotExists(JsonObject item) {
    assertGetNotFound(ResourcePaths.ITEMS + "/" + item.getString("id"));
  }

  private static void assertReturns422ForExistingId(String queryParams) {
    var itemsArray1 = threeItems();
    var itemsArray2 = threeItems();
    var existingId = itemsArray1.getJsonObject(1).getString("id");
    itemsArray2.getJsonObject(1).put("id", existingId);

    assertThat(syncBatch(queryParams, itemsArray1).status()).isEqualTo(SC_CREATED);
    assertThat(syncBatch(queryParams, itemsArray2).status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
  }

  private static void assertHridError(TestResponse response, String hrid) {
    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    var error = response.jsonBody().mapTo(Errors.class).getErrors().getFirst();
    assertThat(error.getMessage()).isEqualTo("HRID value already exists in table item: " + hrid);
    var parameter = error.getParameters().getFirst();
    assertThat(parameter.getKey()).isEqualTo("lower(f_unaccent(jsonb ->> 'hrid'::text))");
    assertThat(parameter.getValue()).isEqualTo(hrid);
  }

  private static TestResponse updateOptimizeUpdatesSetting(boolean value) {
    return await(doPatch(client, "/inventory-settings/inventory.optimize-updates.enabled",
      new JsonObject().put("value", value)));
  }

  private static TestResponse syncBatch(JsonArray itemsArray) {
    return syncBatch("", itemsArray);
  }

  private static TestResponse syncBatch(String queryParams, JsonArray itemsArray) {
    return await(doPost(client, ResourcePaths.ITEMS_SYNC + queryParams, new JsonObject().put(ITEMS_KEY, itemsArray)));
  }

  private static TestResponse syncBatchUnsafe(JsonArray itemsArray) {
    return await(doPost(client, ResourcePaths.ITEMS_SYNC_UNSAFE, new JsonObject().put(ITEMS_KEY, itemsArray)));
  }

  private static void setItemSequence(long sequenceNumber) {
    runQuery("select setval('hrid_items_seq'," + sequenceNumber + ",FALSE)");
  }

  private static int getMaxOrder(String holdingId) {
    var result = runQuery("SELECT max_order FROM item_order_tracker WHERE holdings_id = '" + holdingId + "'");
    return result.iterator().next().toJson().getInteger("max_order");
  }

  private static String urlEncode(String value) {
    return java.net.URLEncoder.encode(value, UTF_8);
  }

  private static List<TestResponse> runConcurrentPosts(Map<UUID, JsonObject> items) {
    var barrier = new CyclicBarrier(items.size());

    List<Callable<TestResponse>> tasks = items.values().stream()
      .map(itemJson -> (Callable<TestResponse>) () -> {
        barrier.await();
        return await(doPost(client, ResourcePaths.ITEMS, itemJson));
      }).toList();

    try (ExecutorService executor = Executors.newFixedThreadPool(items.size())) {
      var futures = tasks.stream().map(executor::submit).toList();
      var results = new ArrayList<TestResponse>();
      for (var future : futures) {
        results.add(future.get());
      }
      return results;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
