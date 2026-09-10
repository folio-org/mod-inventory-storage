package org.folio.rest.impl;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.rest.impl.HoldingsStorageFixtures.createHolding;
import static org.folio.rest.impl.HoldingsStorageFixtures.createItem;
import static org.folio.rest.impl.HoldingsStorageFixtures.createLoanType;
import static org.folio.rest.impl.HoldingsStorageFixtures.createMaterialType;
import static org.folio.rest.impl.InstanceStorageFixtures.createInstance;
import static org.folio.rest.impl.InstanceStorageFixtures.createInstanceType;
import static org.folio.rest.impl.LocationStorageFixtures.createLocation;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.UUID;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.support.messages.BoundWithEventMessageChecks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BoundWithStorageIT extends BaseIntegrationTest {

  private static final String ID_FIELD = "id";
  private static final String ITEM_ID_FIELD = "itemId";
  private static final String HOLDINGS_RECORD_ID_FIELD = "holdingsRecordId";
  private static final String BOUND_WITH_CONTENTS_FIELD = "boundWithContents";
  private static final String TOTAL_RECORDS_FIELD = "totalRecords";

  private static String instanceTypeId;
  private static String materialTypeId;
  private static String loanTypeId;
  private static String locationId;

  private final BoundWithEventMessageChecks boundWithEventMessageChecks = eventMessageChecks();

  @BeforeAll
  static void seedReferenceData() {
    instanceTypeId = createInstanceType(client);
    materialTypeId = createMaterialType(client);
    loanTypeId = createLoanType(client);
    locationId = createLocation(client);
  }

  @BeforeEach
  void clearBoundWithData() {
    runQuery("TRUNCATE TABLE bound_with_part, item, holdings_record, instance CASCADE");
  }

  @Test
  @DisplayName("should create and retrieve bound-with parts for an item shared across holdings")
  void shouldCreateAndRetrieveBoundWithParts() {
    var mainInstanceId = createInstance(client, "Main Instance", instanceTypeId);
    var mainHoldingId = createHolding(client, mainInstanceId, locationId);
    var itemId = createItem(client, mainHoldingId, materialTypeId, loanTypeId);

    var anotherInstanceId = createInstance(client, "Another Instance", instanceTypeId);
    var anotherHoldingId = createHolding(client, anotherInstanceId, locationId);
    var thirdInstanceId = createInstance(client, "Third Instance", instanceTypeId);
    var thirdHoldingId = createHolding(client, thirdInstanceId, locationId);

    var firstPart = createBoundWithPart(mainHoldingId, itemId);
    var secondPart = createBoundWithPart(anotherHoldingId, itemId);
    final var thirdPart = createBoundWithPart(thirdHoldingId, itemId);

    var getById = get(doGet(client, ResourcePaths.BOUND_WITH_PARTS + "/" + secondPart.getString(ID_FIELD)));
    var allPartsForItem = getPartsByItemId(itemId);

    assertThat(getById.status()).isEqualTo(SC_OK);
    assertThat(allPartsForItem.getInteger(TOTAL_RECORDS_FIELD)).isEqualTo(3);

    boundWithEventMessageChecks.createdMessagePublished(firstPart, mainInstanceId);
    boundWithEventMessageChecks.createdMessagePublished(secondPart, anotherInstanceId);
    boundWithEventMessageChecks.createdMessagePublished(thirdPart, thirdInstanceId);
  }

  @Test
  @DisplayName("should return 400 when deleting an item that has bound-with parts")
  void shouldReturn400_whenDeletingItemThatHasBoundWithParts() {
    var instance1Id = createInstance(client, "Instance 1", instanceTypeId);
    var holding1Id = createHolding(client, instance1Id, locationId);
    var instance2Id = createInstance(client, "Instance 2", instanceTypeId);
    var holding2Id = createHolding(client, instance2Id, locationId);
    var itemId = createItem(client, holding1Id, materialTypeId, loanTypeId);
    createBoundWithPart(holding1Id, itemId);
    createBoundWithPart(holding2Id, itemId);

    var response = get(doDelete(client, ResourcePaths.ITEMS + "/" + itemId));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
  }

  @Test
  @DisplayName("should change one part of a bound-with by replacing it")
  void shouldChangeOnePartOfBoundWith_byReplacing() {
    var instance1Id = createInstance(client, "Instance 1", instanceTypeId);
    var holding1Id = createHolding(client, instance1Id, locationId);
    var instance2Id = createInstance(client, "Instance 2", instanceTypeId);
    var holding2Id = createHolding(client, instance2Id, locationId);
    var itemId = createItem(client, holding1Id, materialTypeId, loanTypeId);

    final var partOneCreated = createBoundWithPart(holding1Id, itemId);
    var partTwoCreated = createBoundWithPart(holding2Id, itemId);

    assertThat(getPartsByItemId(itemId).getInteger(TOTAL_RECORDS_FIELD)).isEqualTo(2);
    assertThat(getPartsByHoldingsRecordId(holding2Id).getInteger(TOTAL_RECORDS_FIELD)).isEqualTo(1);

    var instance3Id = createInstance(client, "Instance 3", instanceTypeId);
    var holding3Id = createHolding(client, instance3Id, locationId);
    var partTwoId = partTwoCreated.getString(ID_FIELD);
    var replaceResponse = get(doPut(client, ResourcePaths.BOUND_WITH_PARTS + "/" + partTwoId,
      createBoundWithPartJson(holding3Id, itemId)));
    assertThat(replaceResponse.status()).isEqualTo(SC_NO_CONTENT);

    final var partTwoUpdated = get(doGet(client, ResourcePaths.BOUND_WITH_PARTS + "/" + partTwoId));

    assertThat(getPartsByItemId(itemId).getInteger(TOTAL_RECORDS_FIELD)).isEqualTo(2);
    assertThat(getPartsByHoldingsRecordId(holding2Id).getInteger(TOTAL_RECORDS_FIELD)).isEqualTo(0);
    assertThat(getPartsByHoldingsRecordId(holding3Id).getInteger(TOTAL_RECORDS_FIELD)).isEqualTo(1);

    boundWithEventMessageChecks.createdMessagePublished(partOneCreated, instance1Id);
    boundWithEventMessageChecks.createdMessagePublished(partTwoCreated, instance2Id);
    boundWithEventMessageChecks.updatedMessagePublished(
      partTwoCreated, partTwoUpdated.jsonBody(), instance2Id, instance3Id);
  }

  @Test
  @DisplayName("should create and delete bound-with parts by replacing the set of parts")
  void shouldCreateAndDeleteBoundWithParts_byReplacingSetOfParts() {
    var instance1Id = createInstance(client, "Instance 1", instanceTypeId);
    var holding1Id = createHolding(client, instance1Id, locationId);
    var itemId = createItem(client, holding1Id, materialTypeId, loanTypeId);
    var instance2Id = createInstance(client, "Instance 2", instanceTypeId);
    var holding2Id = createHolding(client, instance2Id, locationId);
    var instance3Id = createInstance(client, "Instance 3", instanceTypeId);
    var holding3Id = createHolding(client, instance3Id, locationId);
    var instance4Id = createInstance(client, "Instance 4", instanceTypeId);
    var holding4Id = createHolding(client, instance4Id, locationId);

    putCompositeBoundWithAndVerify(itemId, List.of(holding1Id, holding2Id), 2);
    putCompositeBoundWithAndVerify(itemId, List.of(holding1Id, holding2Id, holding3Id), 3);
    putCompositeBoundWithAndVerify(itemId, List.of(holding4Id), 2);
  }

  @Test
  @DisplayName("should delete all parts of a bound-with when replaced with an empty contents list")
  void shouldDeleteAllPartsOfBoundWith_whenReplacedWithEmptyContentsList() {
    var instance1Id = createInstance(client, "Instance 1", instanceTypeId);
    var holding1Id = createHolding(client, instance1Id, locationId);
    var itemId = createItem(client, holding1Id, materialTypeId, loanTypeId);
    var holding2Id = createHolding(client, createInstance(client, "Instance 2", instanceTypeId), locationId);

    putCompositeBoundWithAndVerify(itemId, List.of(holding2Id), 2);

    var response = putCompositeBoundWith(createBoundWithCompositeJson(itemId, List.of()));

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
    assertThat(getPartsByItemId(itemId).getInteger(TOTAL_RECORDS_FIELD)).isEqualTo(0);
  }

  @Test
  @DisplayName("should remove all parts of a bound-with when only the main holdings record id is provided")
  void shouldRemoveAllPartsOfBoundWith_whenOnlyMainHoldingsRecordIdProvided() {
    var instance1Id = createInstance(client, "Instance 1", instanceTypeId);
    var holding1Id = createHolding(client, instance1Id, locationId);
    var itemId = createItem(client, holding1Id, materialTypeId, loanTypeId);
    var holding2Id = createHolding(client, createInstance(client, "Instance 2", instanceTypeId), locationId);

    putCompositeBoundWithAndVerify(itemId, List.of(holding2Id), 2);

    var response = putCompositeBoundWith(createBoundWithCompositeJson(itemId, List.of(holding1Id)));

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
    assertThat(getPartsByItemId(itemId).getInteger(TOTAL_RECORDS_FIELD)).isEqualTo(0);
  }

  @Test
  @DisplayName("should have no effect when only the main holdings record id is provided and no bound-with exists yet")
  void shouldHaveNoEffect_whenOnlyMainHoldingsRecordIdProvidedAndBoundWithDoesNotExistYet() {
    var instance1Id = createInstance(client, "Instance 1", instanceTypeId);
    var holding1Id = createHolding(client, instance1Id, locationId);
    var itemId = createItem(client, holding1Id, materialTypeId, loanTypeId);

    assertThat(getPartsByItemId(itemId).getInteger(TOTAL_RECORDS_FIELD)).isEqualTo(0);

    var response = putCompositeBoundWith(createBoundWithCompositeJson(itemId, List.of(holding1Id)));

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
    assertThat(getPartsByItemId(itemId).getInteger(TOTAL_RECORDS_FIELD)).isEqualTo(0);
  }

  @Test
  @DisplayName("should have no effect when an empty list of parts is provided and no bound-with exists yet")
  void shouldHaveNoEffect_whenEmptyListOfPartsProvidedAndBoundWithDoesNotExistYet() {
    var instance1Id = createInstance(client, "Instance 1", instanceTypeId);
    var holding1Id = createHolding(client, instance1Id, locationId);
    var itemId = createItem(client, holding1Id, materialTypeId, loanTypeId);

    assertThat(getPartsByItemId(itemId).getInteger(TOTAL_RECORDS_FIELD)).isEqualTo(0);

    var response = putCompositeBoundWith(createBoundWithCompositeJson(itemId, List.of()));

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
    assertThat(getPartsByItemId(itemId).getInteger(TOTAL_RECORDS_FIELD)).isEqualTo(0);
  }

  @Test
  @DisplayName("should not update a bound-with when the item or some holdings do not exist")
  void shouldNotUpdateBoundWith_whenItemOrSomeHoldingsDoNotExist() {
    var instance1Id = createInstance(client, "Instance 1", instanceTypeId);
    var holding1Id = createHolding(client, instance1Id, locationId);
    var itemId = createItem(client, holding1Id, materialTypeId, loanTypeId);
    var instance2Id = createInstance(client, "Instance 2", instanceTypeId);
    var holding2Id = createHolding(client, instance2Id, locationId);

    var nonExistentItemId = UUID.randomUUID().toString();
    var responseForNonExistentItem = putCompositeBoundWith(
      createBoundWithCompositeJson(nonExistentItemId, List.of(holding1Id, holding2Id)));

    assertThat(responseForNonExistentItem.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    var itemError = responseForNonExistentItem.bodyAsClass(Errors.class).getErrors().getFirst();
    assertThat(itemError.getMessage()).isEqualTo("Item not found.");
    assertThat(itemError.getParameters().getFirst().getKey()).isEqualTo(ITEM_ID_FIELD);
    assertThat(itemError.getParameters().getFirst().getValue()).isEqualTo(nonExistentItemId);
    assertThat(getPartsByItemId(nonExistentItemId).getInteger(TOTAL_RECORDS_FIELD)).isEqualTo(0);

    var nonExistentHoldingsId = UUID.randomUUID().toString();
    var responseForNonExistentHoldings = putCompositeBoundWith(createBoundWithCompositeJson(
      itemId, List.of(holding1Id, nonExistentHoldingsId, holding1Id)));

    assertThat(responseForNonExistentHoldings.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    var holdingsError = responseForNonExistentHoldings.bodyAsClass(Errors.class).getErrors().getFirst();
    assertThat(holdingsError.getMessage()).isEqualTo("Holdings record not found.");
    assertThat(holdingsError.getParameters().getFirst().getKey()).isEqualTo(HOLDINGS_RECORD_ID_FIELD);
    assertThat(holdingsError.getParameters().getFirst().getValue()).isEqualTo(nonExistentHoldingsId);
    assertThat(getPartsByItemId(itemId).getInteger(TOTAL_RECORDS_FIELD)).isEqualTo(0);
  }

  /**
   * Unconventionally, the composite {@code /inventory-storage/bound-withs} API accepts a PUT but
   * takes no id on the path - the request body identifies the item and its full set of parts.
   */
  private BaseIntegrationTest.TestResponse putCompositeBoundWith(JsonObject body) {
    return get(doPut(client, ResourcePaths.BOUND_WITHS, body));
  }

  private void putCompositeBoundWithAndVerify(String itemId, List<String> holdingsRecordIds, int expectedParts) {
    var response = putCompositeBoundWith(createBoundWithCompositeJson(itemId, holdingsRecordIds));

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
    assertThat(getPartsByItemId(itemId).getInteger(TOTAL_RECORDS_FIELD)).isEqualTo(expectedParts);
  }

  private JsonObject getPartsByItemId(String itemId) {
    return get(doGet(client, ResourcePaths.BOUND_WITH_PARTS + "?query=" + ITEM_ID_FIELD + "==" + itemId))
      .jsonBody();
  }

  private JsonObject getPartsByHoldingsRecordId(String holdingsRecordId) {
    return get(doGet(client,
      ResourcePaths.BOUND_WITH_PARTS + "?query=" + HOLDINGS_RECORD_ID_FIELD + "==" + holdingsRecordId))
      .jsonBody();
  }

  private JsonObject createBoundWithPart(String holdingsRecordId, String itemId) {
    return get(doPost(client, ResourcePaths.BOUND_WITH_PARTS, createBoundWithPartJson(holdingsRecordId, itemId)))
      .jsonBody();
  }

  private JsonObject createBoundWithPartJson(String holdingsRecordId, String itemId) {
    return new JsonObject()
      .put(HOLDINGS_RECORD_ID_FIELD, holdingsRecordId)
      .put(ITEM_ID_FIELD, itemId);
  }

  private JsonObject createBoundWithCompositeJson(String itemId, List<String> holdingsRecordIds) {
    var contents = new JsonArray();
    holdingsRecordIds.forEach(id -> contents.add(new JsonObject().put(HOLDINGS_RECORD_ID_FIELD, id)));

    return new JsonObject()
      .put(ITEM_ID_FIELD, itemId)
      .put(BOUND_WITH_CONTENTS_FIELD, contents);
  }

  private static BoundWithEventMessageChecks eventMessageChecks() {
    try {
      return new BoundWithEventMessageChecks(KAFKA_CONSUMER, new URL(wm.baseUrl()));
    } catch (MalformedURLException e) {
      throw new IllegalStateException(e);
    }
  }
}
