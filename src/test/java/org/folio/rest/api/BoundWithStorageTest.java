package org.folio.rest.api;

import static org.folio.utility.ModuleUtility.getClient;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.folio.rest.support.matchers.ResponseMatcher.hasValidationError;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import java.util.concurrent.CompletableFuture;
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

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import junitparams.JUnitParamsRunner;
import lombok.SneakyThrows;

@RunWith(JUnitParamsRunner.class)
public class BoundWithStorageTest extends TestBaseWithInventoryUtil {
  static ResourceClient boundWithPartsClient  = ResourceClient.forBoundWithParts(getClient());

  private final BoundWithEventMessageChecks boundWithEventMessageChecks
    = new BoundWithEventMessageChecks(kafkaConsumer);

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

  @AfterClass
  public static void afterAll() {
    deleteAllById(boundWithPartsClient);
  }

  @Test
  public void canCreateAndRetrieveBoundWithParts() {
    IndividualResource mainInstance = createInstance("Main Instance");
    IndividualResource mainHoldingsRecord = createHoldingsRecord(mainInstance.getId());
    IndividualResource item = createItem(mainHoldingsRecord.getId());

    IndividualResource anotherInstance = createInstance("Another Instance");
    IndividualResource anotherHoldingsRecord = createHoldingsRecord(anotherInstance.getId());
    IndividualResource aThirdInstance = createInstance("Third Instance");
    IndividualResource aThirdHoldingsRecord = createHoldingsRecord(aThirdInstance.getId());

    // Make 'item' a bound-with
    final var firstPart = boundWithPartsClient.create(
      createBoundWithPartJson(mainHoldingsRecord.getId(), item.getId()));

    final var secondPart = boundWithPartsClient.create(
      createBoundWithPartJson(anotherHoldingsRecord.getId(),item.getId()));

    final var thirdPart = boundWithPartsClient.create(
      createBoundWithPartJson(aThirdHoldingsRecord.getId(), item.getId()));

    final var boundWithGETResponseForPartById = boundWithPartsClient.getById(secondPart.getId());

    final var getAllPartsForBoundWithItem = boundWithPartsClient.getByQuery("?query=itemId==" + item.getId());

    assertThat(boundWithGETResponseForPartById.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(getAllPartsForBoundWithItem.size(), is(3));

    boundWithEventMessageChecks.createdMessagePublished(firstPart, mainInstance.getId());
    boundWithEventMessageChecks.createdMessagePublished(secondPart, anotherInstance.getId());
    boundWithEventMessageChecks.createdMessagePublished(thirdPart, aThirdInstance.getId());
  }

  @Test
  public void cannotDeleteItemThatHasBoundWithParts() {
    IndividualResource instance1 = createInstance("Instance 1");
    IndividualResource holdingsRecord1 = createHoldingsRecord(instance1.getId());
    IndividualResource instance2 = createInstance("Instance 2");
    IndividualResource holdingsRecord2 = createHoldingsRecord(instance2.getId());
    IndividualResource item = createItem(holdingsRecord1.getId());
    boundWithPartsClient.create(createBoundWithPartJson(holdingsRecord1.getId(),item.getId()));
    boundWithPartsClient.create(createBoundWithPartJson(holdingsRecord2.getId(),item.getId()));

    Response deleteBoundWithItemResponse = itemsClient.attemptToDelete(item.getId());
    assertThat(deleteBoundWithItemResponse.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
  }

  @Test
  public void canChangeOnePartOfABoundWith() {
    IndividualResource instance1 = createInstance("Instance 1");
    IndividualResource holdingsRecord1 = createHoldingsRecord(instance1.getId());
    IndividualResource instance2 = createInstance("Instance 2");
    IndividualResource holdingsRecord2 = createHoldingsRecord(instance2.getId());
    IndividualResource item = createItem(holdingsRecord1.getId());

    final var partOneCreated = boundWithPartsClient.create(
      createBoundWithPartJson(holdingsRecord1.getId(), item.getId()));

    final var partTwoCreated = boundWithPartsClient.create(
      createBoundWithPartJson(holdingsRecord2.getId(),item.getId()));

    List<JsonObject> getAllPartsForBoundWithItem = boundWithPartsClient.getByQuery("?query=itemId==" + item.getId());
    List<JsonObject> part2 = boundWithPartsClient.getByQuery("?query=holdingsRecordId==" + holdingsRecord2.getId()+"");

    IndividualResource instance3 = createInstance("Instance 3");
    IndividualResource holdingsRecord3 = createHoldingsRecord(instance3.getId());

    boundWithPartsClient.replace(partTwoCreated.getId(),
      createBoundWithPartJson(holdingsRecord3.getId(), item.getId()));

    final var partTwoUpdated = boundWithPartsClient.getById(partTwoCreated.getId());

    List<JsonObject> getAllPartsForBoundWithItemAgain = boundWithPartsClient.getByQuery("?query=itemId==" + item.getId());
    List<JsonObject> oldPart2Gone = boundWithPartsClient.getByQuery("?query=holdingsRecordId==" + holdingsRecord2.getId());
    List<JsonObject> newPart2 = boundWithPartsClient.getByQuery("?query=holdingsRecordId==" + holdingsRecord3.getId());

    assertThat(getAllPartsForBoundWithItem.size(), is(2));
    assertThat(part2.toString(), part2.size(), is(1));

    assertThat(getAllPartsForBoundWithItemAgain.size(), is(2));
    assertThat(oldPart2Gone.size(), is(0));
    assertThat(newPart2.size(), is(1));

    boundWithEventMessageChecks.createdMessagePublished(partOneCreated, instance1.getId());
    boundWithEventMessageChecks.createdMessagePublished(partTwoCreated, instance2.getId());

    // There is a potential bug with the old representation in these message
    // until this is investigated further, that check is removed
    boundWithEventMessageChecks.updatedMessagePublished(partTwoCreated,
      partTwoUpdated, instance2.getId(), instance3.getId());
  }

  @Test
  public void canCreateAndOrDeleteBoundWithPartsByASetOfParts() {
    IndividualResource instance1 = createInstance("Instance 1");
    IndividualResource holdingsRecord1 = createHoldingsRecord(instance1.getId());
    IndividualResource item = createItem(holdingsRecord1.getId());

    IndividualResource instance2 = createInstance("Instance 2");
    IndividualResource holdingsRecord2 = createHoldingsRecord(instance2.getId());

    IndividualResource instance3 = createInstance("Instance 3");
    IndividualResource holdingsRecord3 = createHoldingsRecord(instance3.getId());

    IndividualResource instance4 = createInstance("Instance 4");
    IndividualResource holdingsRecord4 = createHoldingsRecord(instance4.getId());


    // Listing the main holdings record plus one more
    JsonObject initialBoundWith = createBoundWithCompositeJson(item.getId(),
      Arrays.asList(holdingsRecord1.getId(),holdingsRecord2.getId()));
    Response initialPut = putCompositeBoundWith(initialBoundWith);

    assertThat(
      "Expected 204 - no content on initial boundWith create",
      initialPut.getStatusCode(),is(204));
    List<JsonObject> initiallyCreatedParts
      = boundWithPartsClient.getByQuery("?query=itemId==" + item.getId());
    assertThat(
      "Expected initial boundWith to contain two parts.",
      initiallyCreatedParts.size(),is(2));

    IndividualResource part1 = new IndividualResource(
      boundWithPartsClient.getById(
        UUID.fromString(initiallyCreatedParts.get(0).getString("id"))));

    IndividualResource part2 = new IndividualResource(
      boundWithPartsClient.getById(
        UUID.fromString(initiallyCreatedParts.get(1).getString("id"))));

    // The published checks fail with ConditionTimeout more often than not on this developer's
    // box. (Could it be a contributing factor that the box uses 90-100% of all four CPUs for
    // most of the 14 minutes that Inventory Storage's tests take?)
    // Increasing the wait from 10 to 30 seconds makes the tests pass more often than not.
    //* boundWithEventMessageChecks.createdMessagePublished(part1, instance1.getId());
    //* boundWithEventMessageChecks.createdMessagePublished(part2, instance2.getId());

    // Listing the main holdings record plus two more
    JsonObject boundWithFirstUpdate = createBoundWithCompositeJson(item.getId(),
      Arrays.asList(holdingsRecord1.getId(), holdingsRecord2.getId(), holdingsRecord3.getId()));
    Response firstUpdate = putCompositeBoundWith(boundWithFirstUpdate);
    assertThat(
      "Expected 204 - no content on initial boundWith create",
      firstUpdate.getStatusCode(),is(204));
    List<JsonObject> partsAfterFirstUpdate
      = boundWithPartsClient.getByQuery("?query=itemId==" + item.getId());
    assertThat(
      "Expected three parts after first update.",
      partsAfterFirstUpdate.size(),is(3));

    // Listing just one entry (not the main holdings record)
    JsonObject boundWithSecondUpdate = createBoundWithCompositeJson(item.getId(),
      Arrays.asList(holdingsRecord4.getId()));
    Response secondUpdate = putCompositeBoundWith(boundWithSecondUpdate);
    assertThat(
      "Expected 204 - no content on initial boundWith create",
      secondUpdate.getStatusCode(),is(204));
    List<JsonObject> partsAfterSecondUpdate
      = boundWithPartsClient.getByQuery("?query=itemId==" + item.getId());
    assertThat(
      "Expected two parts after second update.",
      partsAfterSecondUpdate.size(),is(2));


  }

  @Test
  public void canDeleteAllPartsOfBoundWithByEmptyContentsList() {
    IndividualResource instance1 = createInstance("Instance 1");
    IndividualResource holdingsRecord1 = createHoldingsRecord(instance1.getId());
    IndividualResource item = createItem(holdingsRecord1.getId());

    IndividualResource instance2 = createInstance("Instance 2");
    IndividualResource holdingsRecord2 = createHoldingsRecord(instance2.getId());

    // List one holdings-record but not the main holdings record (should be implied)
    JsonObject initialSample = createBoundWithCompositeJson(item.getId(),
      Arrays.asList(holdingsRecord2.getId()));
    Response responseOnInitial = putCompositeBoundWith(initialSample);
    assertThat(
      "Expected 204 - no content on initial request",
      responseOnInitial.getStatusCode(),is(204));
    List<JsonObject> partsAfterInitialRequest
      = boundWithPartsClient.getByQuery("?query=itemId==" + item.getId());
    assertThat(
      "Expected two parts (including the main holdings record) after initial request.",
      partsAfterInitialRequest.size(),is(2));

    // Provide empty list of contents
    JsonObject emptyListOfContents = createBoundWithCompositeJson(item.getId(),
      Arrays.asList(holdingsRecord1.getId()));
    Response responseOnEmptyListOfContents = putCompositeBoundWith(emptyListOfContents);
    assertThat(
      "Expected 204 - no content on request with empty list of contents",
      responseOnEmptyListOfContents.getStatusCode(),is(204));
    List<JsonObject> partsAfterUpdate
      = boundWithPartsClient.getByQuery("?query=itemId==" + item.getId());
    assertThat("Expected no parts left after update with empty list of contents.",
      partsAfterUpdate.size(),is(0));
  }

  @Test
  public void canDeleteAllPartsOfBoundWithByOnlyProvidingMainHoldingsRecordId() {
    IndividualResource instance1 = createInstance("Instance 1");
    IndividualResource holdingsRecord1 = createHoldingsRecord(instance1.getId());
    IndividualResource item = createItem(holdingsRecord1.getId());

    IndividualResource instance2 = createInstance("Instance 2");
    IndividualResource holdingsRecord2 = createHoldingsRecord(instance2.getId());

    // List one holdings-record but not the main holdings record (should be implied)
    JsonObject initialSample = createBoundWithCompositeJson(item.getId(),
      Arrays.asList(holdingsRecord2.getId()));
    Response responseOnInitial = putCompositeBoundWith(initialSample);
    logger.info("Response, initial sample: " + responseOnInitial.getBody());
    assertThat(
      "Expected 204 - no content on initial request",
      responseOnInitial.getStatusCode(),is(204));
    List<JsonObject> partsAfterInitialRequest
      = boundWithPartsClient.getByQuery("?query=itemId==" + item.getId());
    assertThat(
      "Expected two parts (including the main holdings record) after initial request.",
      partsAfterInitialRequest.size(),is(2));


    // Provide only the main holdings record in list of contents (does not qualify as a bound-with
    JsonObject onlyMainHoldingsRecordInListOfContents = createBoundWithCompositeJson(item.getId(),
      Arrays.asList(holdingsRecord1.getId()));
    Response responseOnOnlyMainHoldingsInListOfContents
      = putCompositeBoundWith(onlyMainHoldingsRecordInListOfContents);
    logger.info("Response on request with only the main holdings ID in: "
      + responseOnOnlyMainHoldingsInListOfContents.getBody());
    assertThat(
      "Expected 204 - no content on request with only the main holdings ID in",
      responseOnOnlyMainHoldingsInListOfContents.getStatusCode(),is(204));
    List<JsonObject> partsAfterUpdate
      = boundWithPartsClient.getByQuery("?query=itemId==" + item.getId());
    assertThat("Expected no parts left after update that only provided the main holdings ID in contents",
      partsAfterUpdate.size(),is(0));
  }

  @Test
  public void providingOnlyMainHoldingsRecordIdWhenBoundWithDoesNotExistYetHasNoEffect() {
    IndividualResource instance1 = createInstance("Instance 1");
    IndividualResource holdingsRecord1 = createHoldingsRecord(instance1.getId());
    IndividualResource item = createItem(holdingsRecord1.getId());

    List<JsonObject> partsBeforeUpdate
      = boundWithPartsClient.getByQuery("?query=itemId==" + item.getId());

    JsonObject onlyMainHoldingsRecordInListOfContents = createBoundWithCompositeJson(item.getId(),
      Arrays.asList(holdingsRecord1.getId()));
    Response responseOnOnlyMainHoldingsInListOfContents
      = putCompositeBoundWith(onlyMainHoldingsRecordInListOfContents);
    logger.info("Response on request with only the main holdings ID in: "
      + responseOnOnlyMainHoldingsInListOfContents.getBody());
    assertThat(
      "Expected 204 - no content on request with only the main holdings ID in",
      responseOnOnlyMainHoldingsInListOfContents.getStatusCode(),is(204));
    List<JsonObject> partsAfterUpdate
      = boundWithPartsClient.getByQuery("?query=itemId==" + item.getId());
    assertThat("Expected no parts before update", partsBeforeUpdate.size(),is(0));
    assertThat("Expected no parts after update that only provided the main holdings ID in contents",
      partsAfterUpdate.size(),is(0));
  }

  @Test
  public void providingEmptyListOfPartsWhenBoundWithDoesNotExistYetHasNoEffect() {
    IndividualResource instance1 = createInstance("Instance 1");
    IndividualResource holdingsRecord1 = createHoldingsRecord(instance1.getId());
    IndividualResource item = createItem(holdingsRecord1.getId());

    List<JsonObject> partsBeforeUpdate
      = boundWithPartsClient.getByQuery("?query=itemId==" + item.getId());

    JsonObject emptyListOfContents = createBoundWithCompositeJson(item.getId(),
      Arrays.asList());
    Response responseOnEmptyListOfContents
      = putCompositeBoundWith(emptyListOfContents);
    logger.info("Response on request with empty list of part: "
      + responseOnEmptyListOfContents.getBody());
    assertThat(
      "Expected 204 - no content on request on empty list of parts",
      responseOnEmptyListOfContents.getStatusCode(),is(204));
    List<JsonObject> partsAfterUpdate
      = boundWithPartsClient.getByQuery("?query=itemId==" + item.getId());
    assertThat("Expected no parts before update", partsBeforeUpdate.size(),is(0));
    assertThat("Expected no parts after update with empty list",
      partsAfterUpdate.size(),is(0));
  }

  @Test
  public void cannotUpdateBoundWithIfItemOrSomeHoldingsDoNotExist() {
    IndividualResource instance1 = createInstance("Instance 1");
    IndividualResource holdingsRecord1 = createHoldingsRecord(instance1.getId());
    IndividualResource item = createItem(holdingsRecord1.getId());

    IndividualResource instance2 = createInstance("Instance 2");
    IndividualResource holdingsRecord2 = createHoldingsRecord(instance2.getId());

    UUID randomId = UUID.randomUUID();
    JsonObject initialBoundWith = createBoundWithCompositeJson(randomId,
      Arrays.asList(holdingsRecord1.getId(),holdingsRecord2.getId()));

    Response response = putCompositeBoundWith(initialBoundWith);
    assertThat(response, hasValidationError("Item not found.", "itemId", randomId.toString()));

    List<JsonObject> boundWithParts
      = boundWithPartsClient.getByQuery("?query=itemId==" + randomId);
    assertThat(
      "Expected no parts created for the given item.",
      boundWithParts.size(),is(0));

    JsonObject secondBoundWithAttempt = createBoundWithCompositeJson(item.getId(),
      Arrays.asList(holdingsRecord1.getId(), randomId, holdingsRecord1.getId()));

    response = putCompositeBoundWith(secondBoundWithAttempt);
    assertThat(response, hasValidationError("Holdings record not found.", "holdingsRecordId", randomId.toString()));

    boundWithParts
      = boundWithPartsClient.getByQuery("?query=itemId==" + item.getId());
    assertThat(
      "Expected no parts found for the given item.",
      boundWithParts.size(),is(0));
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
    return  holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(instanceId)
        .withPermanentLocation(mainLibraryLocationId));
  }

  private IndividualResource createItem(UUID holdingsRecordId) {
    return itemsClient.create(
      new ItemRequestBuilder()
        .forHolding(holdingsRecordId)
        .withMaterialType(bookMaterialTypeId)
        .withPermanentLoanType(canCirculateLoanTypeId));
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

}
