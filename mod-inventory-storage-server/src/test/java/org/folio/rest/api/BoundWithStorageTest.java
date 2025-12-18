package org.folio.rest.api;

import static org.folio.rest.support.matchers.ResponseMatcher.hasValidationError;
import static org.folio.utility.ModuleUtility.getClient;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import junitparams.JUnitParamsRunner;
import lombok.SneakyThrows;
import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.builders.HoldingRequestBuilder;
import org.folio.rest.support.builders.ItemRequestBuilder;
import org.folio.rest.support.http.InterfaceUrls;
import org.folio.rest.support.http.ResourceClient;
import org.folio.rest.support.messages.BoundWithEventMessageChecks;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class BoundWithStorageTest extends TestBaseWithInventoryUtil {
  static ResourceClient boundWithPartsClient = ResourceClient.forBoundWithParts(getClient());

  private final BoundWithEventMessageChecks boundWithEventMessageChecks
    = new BoundWithEventMessageChecks(KAFKA_CONSUMER);

  @AfterClass
  public static void afterAll() {
    deleteAllById(boundWithPartsClient);
  }

  @SneakyThrows
  @After
  public void beforeEach() {
    deleteAllById(boundWithPartsClient);
    clearData();
    setupMaterialTypes();
    setupLoanTypes();
    setupLocations();
    removeAllEvents();
  }

  @Test
  public void canCreateAndRetrieveBoundWithParts() {
    IndividualResource mainInstance = createInstance("Main Instance");
    IndividualResource mainHoldingsRecord = createHoldingsRecord(mainInstance.getId());
    IndividualResource item = createItem(mainHoldingsRecord.getId());

    IndividualResource anotherInstance = createInstance("Another Instance");
    IndividualResource anotherHoldingsRecord = createHoldingsRecord(anotherInstance.getId());
    IndividualResource thirdInstance = createInstance("Third Instance");
    IndividualResource thirdHoldingsRecord = createHoldingsRecord(thirdInstance.getId());

    // Make 'item' a bound-with
    final var firstPart = boundWithPartsClient.create(
      createBoundWithPartJson(mainHoldingsRecord.getId(), item.getId()));

    final var secondPart = boundWithPartsClient.create(
      createBoundWithPartJson(anotherHoldingsRecord.getId(), item.getId()));

    final var thirdPart = boundWithPartsClient.create(
      createBoundWithPartJson(thirdHoldingsRecord.getId(), item.getId()));

    final var boundWithGetResponseForPartById = boundWithPartsClient.getById(secondPart.getId());

    final var getAllPartsForBoundWithItem = boundWithPartsClient.getByQuery("?query=itemId==" + item.getId());

    assertThat(boundWithGetResponseForPartById.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(getAllPartsForBoundWithItem.size(), is(3));

    boundWithEventMessageChecks.createdMessagePublished(firstPart, mainInstance.getId());
    boundWithEventMessageChecks.createdMessagePublished(secondPart, anotherInstance.getId());
    boundWithEventMessageChecks.createdMessagePublished(thirdPart, thirdInstance.getId());
  }

  @Test
  public void cannotDeleteItemThatHasBoundWithParts() {
    IndividualResource instance1 = createInstance("Instance 1");
    IndividualResource holdingsRecord1 = createHoldingsRecord(instance1.getId());
    IndividualResource instance2 = createInstance("Instance 2");
    IndividualResource holdingsRecord2 = createHoldingsRecord(instance2.getId());
    IndividualResource item = createItem(holdingsRecord1.getId());
    boundWithPartsClient.create(createBoundWithPartJson(holdingsRecord1.getId(), item.getId()));
    boundWithPartsClient.create(createBoundWithPartJson(holdingsRecord2.getId(), item.getId()));

    Response deleteBoundWithItemResponse = itemsClient.attemptToDelete(item.getId());
    assertThat(deleteBoundWithItemResponse.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
  }

  @Test
  public void canChangeOnePartOfBoundWith() {
    IndividualResource instance1 = createInstance("Instance 1");
    IndividualResource holdingsRecord1 = createHoldingsRecord(instance1.getId());
    IndividualResource instance2 = createInstance("Instance 2");
    IndividualResource holdingsRecord2 = createHoldingsRecord(instance2.getId());
    IndividualResource item = createItem(holdingsRecord1.getId());

    final var partOneCreated = createBoundWithPart(holdingsRecord1.getId(), item.getId());
    final var partTwoCreated = createBoundWithPart(holdingsRecord2.getId(), item.getId());

    verifyInitialBoundWithParts(item.getId(), holdingsRecord2.getId());

    IndividualResource instance3 = createInstance("Instance 3");
    IndividualResource holdingsRecord3 = createHoldingsRecord(instance3.getId());
    boundWithPartsClient.replace(partTwoCreated.getId(),
      createBoundWithPartJson(holdingsRecord3.getId(), item.getId()));

    final var partTwoUpdated = boundWithPartsClient.getById(partTwoCreated.getId());
    verifyUpdatedBoundWithParts(item.getId(), holdingsRecord2.getId(), holdingsRecord3.getId());
    verifyBoundWithEventMessages(partOneCreated, partTwoCreated, partTwoUpdated,
      instance1.getId(), instance2.getId(), instance3.getId());
  }

  @Test
  public void canCreateAndOrDeleteBoundWithPartsBycSetOfParts() {
    final IndividualResource instance1 = createInstance("Instance 1");
    final IndividualResource holdingsRecord1 = createHoldingsRecord(instance1.getId());
    final IndividualResource item = createItem(holdingsRecord1.getId());
    final IndividualResource instance2 = createInstance("Instance 2");
    final IndividualResource holdingsRecord2 = createHoldingsRecord(instance2.getId());
    final IndividualResource instance3 = createInstance("Instance 3");
    final IndividualResource holdingsRecord3 = createHoldingsRecord(instance3.getId());
    final IndividualResource instance4 = createInstance("Instance 4");
    final IndividualResource holdingsRecord4 = createHoldingsRecord(instance4.getId());

    createInitialBoundWithAndVerify(item.getId(), holdingsRecord1.getId(), holdingsRecord2.getId());
    performFirstUpdateAndVerify(item.getId(),
      holdingsRecord1.getId(), holdingsRecord2.getId(), holdingsRecord3.getId());
    performSecondUpdateAndVerify(item.getId(), holdingsRecord4.getId());
  }

  @Test
  public void canDeleteAllPartsOfBoundWithByEmptyContentsList() {
    IndividualResource instance1 = createInstance("Instance 1");
    IndividualResource holdingsRecord1 = createHoldingsRecord(instance1.getId());
    IndividualResource item = createItem(holdingsRecord1.getId());
    IndividualResource holdingsRecord2 = createHoldingsRecord(createInstance("Instance 2").getId());

    createInitialBoundWithAndVerifyParts(item.getId(), holdingsRecord2.getId(), 2);

    JsonObject emptyListOfContents = createBoundWithCompositeJson(item.getId(), List.of());
    Response responseOnEmptyListOfContents = putCompositeBoundWith(emptyListOfContents);
    assertThat("Expected 204 - no content on request with empty list of contents",
      responseOnEmptyListOfContents.getStatusCode(), is(204));

    verifyNoPartsRemaining(item.getId(), "Expected no parts left after update with empty list of contents.");
  }

  @Test
  public void canDeleteAllPartsOfBoundWithByOnlyProvidingMainHoldingsRecordId() {
    IndividualResource instance1 = createInstance("Instance 1");
    IndividualResource holdingsRecord1 = createHoldingsRecord(instance1.getId());
    IndividualResource item = createItem(holdingsRecord1.getId());
    IndividualResource holdingsRecord2 = createHoldingsRecord(createInstance("Instance 2").getId());

    createInitialBoundWithAndVerifyParts(item.getId(), holdingsRecord2.getId(), 2);

    updateWithOnlyMainHoldingsAndVerify(item.getId(), holdingsRecord1.getId());
  }

  @Test
  public void providingOnlyMainHoldingsRecordIdWhenBoundWithDoesNotExistYetHasNoEffect() {
    IndividualResource instance1 = createInstance("Instance 1");
    IndividualResource holdingsRecord1 = createHoldingsRecord(instance1.getId());
    IndividualResource item = createItem(holdingsRecord1.getId());

    List<JsonObject> partsBeforeUpdate
      = boundWithPartsClient.getByQuery("?query=itemId==" + item.getId());

    JsonObject onlyMainHoldingsRecordInListOfContents = createBoundWithCompositeJson(item.getId(),
      Collections.singletonList(holdingsRecord1.getId()));
    Response responseOnOnlyMainHoldingsInListOfContents
      = putCompositeBoundWith(onlyMainHoldingsRecordInListOfContents);
    logger.info("Response on request with only the main holdings ID in: {}",
      responseOnOnlyMainHoldingsInListOfContents.getBody());
    assertThat(
      "Expected 204 - no content on request with only the main holdings ID in",
      responseOnOnlyMainHoldingsInListOfContents.getStatusCode(), is(204));
    List<JsonObject> partsAfterUpdate
      = boundWithPartsClient.getByQuery("?query=itemId==" + item.getId());
    assertThat("Expected no parts before update", partsBeforeUpdate.size(), is(0));
    assertThat("Expected no parts after update that only provided the main holdings ID in contents",
      partsAfterUpdate.size(), is(0));
  }

  @Test
  public void providingEmptyListOfPartsWhenBoundWithDoesNotExistYetHasNoEffect() {
    IndividualResource instance1 = createInstance("Instance 1");
    IndividualResource holdingsRecord1 = createHoldingsRecord(instance1.getId());
    IndividualResource item = createItem(holdingsRecord1.getId());

    List<JsonObject> partsBeforeUpdate
      = boundWithPartsClient.getByQuery("?query=itemId==" + item.getId());

    JsonObject emptyListOfContents = createBoundWithCompositeJson(item.getId(),
      List.of());
    Response responseOnEmptyListOfContents
      = putCompositeBoundWith(emptyListOfContents);
    logger.info("Response on request with empty list of part: {}", responseOnEmptyListOfContents.getBody());
    assertThat(
      "Expected 204 - no content on request on empty list of parts",
      responseOnEmptyListOfContents.getStatusCode(), is(204));
    List<JsonObject> partsAfterUpdate
      = boundWithPartsClient.getByQuery("?query=itemId==" + item.getId());
    assertThat("Expected no parts before update", partsBeforeUpdate.size(), is(0));
    assertThat("Expected no parts after update with empty list",
      partsAfterUpdate.size(), is(0));
  }

  @Test
  public void cannotUpdateBoundWithIfItemOrSomeHoldingsDoNotExist() {
    IndividualResource instance1 = createInstance("Instance 1");
    IndividualResource holdingsRecord1 = createHoldingsRecord(instance1.getId());
    IndividualResource item = createItem(holdingsRecord1.getId());

    IndividualResource instance2 = createInstance("Instance 2");
    IndividualResource holdingsRecord2 = createHoldingsRecord(instance2.getId());

    UUID randomId = UUID.randomUUID();
    verifyCannotCreateBoundWithForNonExistentItem(randomId, holdingsRecord1.getId(), holdingsRecord2.getId());

    verifyCannotCreateBoundWithForNonExistentHoldings(item.getId(), holdingsRecord1.getId(), randomId);
  }

  /**
   * Unconventionally, the composite API accepts a PUT but takes no ID on the path.
   */
  public Response putCompositeBoundWith(JsonObject body) {

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    getClient().put(
      InterfaceUrls.boundWithsUrl(),
      body,
      TENANT_ID,
      ResponseHandler.any(putCompleted));

    return TestBase.get(putCompleted);
  }

  private JsonObject createBoundWithPartJson(UUID holdingsRecordId, UUID itemId) {
    JsonObject boundWithPart = new JsonObject();
    boundWithPart.put("holdingsRecordId", holdingsRecordId);
    boundWithPart.put("itemId", itemId);
    return boundWithPart;
  }

  private JsonObject createBoundWithCompositeJson(UUID itemId, List<UUID> holdingsRecordIds) {
    JsonObject compositeBoundWith = new JsonObject();
    if (itemId != null) {
      compositeBoundWith.put("itemId", itemId);
    }
    compositeBoundWith.put("boundWithContents", new JsonArray());
    for (UUID id : holdingsRecordIds) {
      JsonObject boundWithContent = new JsonObject();
      boundWithContent.put("holdingsRecordId", id.toString());
      compositeBoundWith.getJsonArray("boundWithContents").add(boundWithContent);
    }
    return compositeBoundWith;
  }

  private IndividualResource createInstance(String title) {
    return instancesClient.create(createInstanceRequest(UUID.randomUUID(), "TEST",
      title, new JsonArray(), new JsonArray(), UUID_INSTANCE_TYPE, new JsonArray()));
  }

  private IndividualResource createHoldingsRecord(UUID instanceId) {
    HoldingRequestBuilder holdingRequestBuilder = new HoldingRequestBuilder()
      .forInstance(instanceId)
      .withSource(getPreparedHoldingSourceId())
      .withPermanentLocation(MAIN_LIBRARY_LOCATION_ID);
    return createHoldingRecord(holdingRequestBuilder.create());
  }

  private IndividualResource createItem(UUID holdingsRecordId) {
    return itemsClient.create(
      new ItemRequestBuilder()
        .forHolding(holdingsRecordId)
        .withMaterialType(bookMaterialTypeId)
        .withPermanentLoanType(canCirculateLoanTypeId));
  }

  private IndividualResource createBoundWithPart(UUID holdingsRecordId, UUID itemId) {
    return boundWithPartsClient.create(createBoundWithPartJson(holdingsRecordId, itemId));
  }

  private void verifyInitialBoundWithParts(UUID itemId, UUID holdingsRecord2Id) {
    List<JsonObject> getAllPartsForBoundWithItem = boundWithPartsClient.getByQuery("?query=itemId==" + itemId);
    List<JsonObject> part2 = boundWithPartsClient.getByQuery("?query=holdingsRecordId==" + holdingsRecord2Id);
    assertThat(getAllPartsForBoundWithItem.size(), is(2));
    assertThat(part2.toString(), part2.size(), is(1));
  }

  private void verifyUpdatedBoundWithParts(UUID itemId, UUID oldHoldingsRecordId, UUID newHoldingsRecordId) {
    final List<JsonObject> getAllPartsForBoundWithItemAgain =
      boundWithPartsClient.getByQuery("?query=itemId==" + itemId);
    final List<JsonObject> oldPart2Gone =
      boundWithPartsClient.getByQuery("?query=holdingsRecordId==" + oldHoldingsRecordId);
    final List<JsonObject> newPart2 = boundWithPartsClient.getByQuery("?query=holdingsRecordId=="
      + newHoldingsRecordId);

    assertThat(getAllPartsForBoundWithItemAgain.size(), is(2));
    assertThat(oldPart2Gone.size(), is(0));
    assertThat(newPart2.size(), is(1));
  }

  private void verifyBoundWithEventMessages(IndividualResource partOneCreated, IndividualResource partTwoCreated,
      Response partTwoUpdated, UUID instance1Id, UUID instance2Id, UUID instance3Id) {
    boundWithEventMessageChecks.createdMessagePublished(partOneCreated, instance1Id);
    boundWithEventMessageChecks.createdMessagePublished(partTwoCreated, instance2Id);
    // There is a potential bug with the old representation in these message
    // until this is investigated further, that check is removed
    boundWithEventMessageChecks.updatedMessagePublished(partTwoCreated, partTwoUpdated, instance2Id, instance3Id);
  }

  private void createInitialBoundWithAndVerify(UUID itemId, UUID holdingsRecord1Id, UUID holdingsRecord2Id) {
    JsonObject initialBoundWith = createBoundWithCompositeJson(itemId,
      Arrays.asList(holdingsRecord1Id, holdingsRecord2Id));
    Response initialPut = putCompositeBoundWith(initialBoundWith);

    assertThat("Expected 204 - no content on initial boundWith create", initialPut.getStatusCode(), is(204));
    List<JsonObject> initiallyCreatedParts = boundWithPartsClient.getByQuery("?query=itemId==" + itemId);
    assertThat("Expected initial boundWith to contain two parts.", initiallyCreatedParts.size(), is(2));

    //  Uncomment to store in part1 in case of using 'boundWithEventMessageChecks'
    //  IndividualResource part1 =
    new IndividualResource(boundWithPartsClient.getById(
      UUID.fromString(initiallyCreatedParts.get(0).getString("id"))));

    //  Uncomment to store in part2 in case of using 'boundWithEventMessageChecks'
    //  IndividualResource part2 =
    new IndividualResource(boundWithPartsClient.getById(
      UUID.fromString(initiallyCreatedParts.get(1).getString("id"))));
  }

  private void performFirstUpdateAndVerify(UUID itemId, UUID holdingsRecord1Id, UUID holdingsRecord2Id,
      UUID holdingsRecord3Id) {
    JsonObject boundWithFirstUpdate = createBoundWithCompositeJson(itemId,
      Arrays.asList(holdingsRecord1Id, holdingsRecord2Id, holdingsRecord3Id));
    Response firstUpdate = putCompositeBoundWith(boundWithFirstUpdate);
    assertThat("Expected 204 - no content on initial boundWith create", firstUpdate.getStatusCode(), is(204));
    List<JsonObject> partsAfterFirstUpdate = boundWithPartsClient.getByQuery("?query=itemId==" + itemId);
    assertThat("Expected three parts after first update.", partsAfterFirstUpdate.size(), is(3));
  }

  private void performSecondUpdateAndVerify(UUID itemId, UUID holdingsRecord4Id) {
    JsonObject boundWithSecondUpdate = createBoundWithCompositeJson(itemId,
      Collections.singletonList(holdingsRecord4Id));
    Response secondUpdate = putCompositeBoundWith(boundWithSecondUpdate);
    assertThat("Expected 204 - no content on initial boundWith create", secondUpdate.getStatusCode(), is(204));
    List<JsonObject> partsAfterSecondUpdate = boundWithPartsClient.getByQuery("?query=itemId==" + itemId);
    assertThat("Expected two parts after second update.", partsAfterSecondUpdate.size(), is(2));
  }

  private void createInitialBoundWithAndVerifyParts(UUID itemId, UUID holdingsRecord2Id, int expectedParts) {
    JsonObject initialSample = createBoundWithCompositeJson(itemId, Collections.singletonList(holdingsRecord2Id));
    Response responseOnInitial = putCompositeBoundWith(initialSample);
    assertThat("Expected 204 - no content on initial request", responseOnInitial.getStatusCode(), is(204));
    List<JsonObject> partsAfterInitialRequest = boundWithPartsClient.getByQuery("?query=itemId==" + itemId);
    assertThat("Expected two parts (including the main holdings record) after initial request.",
      partsAfterInitialRequest.size(), is(expectedParts));
  }

  private void verifyNoPartsRemaining(UUID itemId, String message) {
    List<JsonObject> partsAfterUpdate = boundWithPartsClient.getByQuery("?query=itemId==" + itemId);
    assertThat(message, partsAfterUpdate.size(), is(0));
  }

  private void updateWithOnlyMainHoldingsAndVerify(UUID itemId, UUID holdingsRecord1Id) {
    JsonObject onlyMainHoldingsRecordInListOfContents = createBoundWithCompositeJson(itemId,
      Collections.singletonList(holdingsRecord1Id));
    Response responseOnOnlyMainHoldingsInListOfContents
      = putCompositeBoundWith(onlyMainHoldingsRecordInListOfContents);
    logger.info("Response on request with only the main holdings in: {}",
      responseOnOnlyMainHoldingsInListOfContents.getBody());
    assertThat("Expected 204 - no content on request with only the main holdings ID in",
      responseOnOnlyMainHoldingsInListOfContents.getStatusCode(), is(204));
    List<JsonObject> partsAfterUpdate = boundWithPartsClient.getByQuery("?query=itemId==" + itemId);
    assertThat("Expected no parts left after update that only provided the main holdings ID in contents",
      partsAfterUpdate.size(), is(0));
  }

  private void verifyCannotCreateBoundWithForNonExistentItem(UUID randomId, UUID holdingsRecord1Id,
      UUID holdingsRecord2Id) {
    JsonObject initialBoundWith = createBoundWithCompositeJson(randomId,
      Arrays.asList(holdingsRecord1Id, holdingsRecord2Id));
    Response response = putCompositeBoundWith(initialBoundWith);
    assertThat(response, hasValidationError("Item not found.", "itemId", randomId.toString()));
    List<JsonObject> boundWithParts = boundWithPartsClient.getByQuery("?query=itemId==" + randomId);
    assertThat("Expected no parts created for the given item.", boundWithParts.size(), is(0));
  }

  private void verifyCannotCreateBoundWithForNonExistentHoldings(UUID itemId, UUID holdingsRecord1Id,
      UUID randomId) {
    JsonObject secondBoundWithAttempt = createBoundWithCompositeJson(itemId,
      Arrays.asList(holdingsRecord1Id, randomId, holdingsRecord1Id));
    Response response = putCompositeBoundWith(secondBoundWithAttempt);
    assertThat(response, hasValidationError("Holdings record not found.", "holdingsRecordId", randomId.toString()));
    List<JsonObject> boundWithParts = boundWithPartsClient.getByQuery("?query=itemId==" + itemId);
    assertThat("Expected no parts found for the given item.", boundWithParts.size(), is(0));
  }
}
