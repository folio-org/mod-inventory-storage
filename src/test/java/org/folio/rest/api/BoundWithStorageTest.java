package org.folio.rest.api;

import static org.folio.rest.support.messages.BoundWithEventMessageChecks.boundWithCreatedMessagePublished;
import static org.folio.rest.support.messages.BoundWithEventMessageChecks.boundWithUpdatedMessagePublished;
import static org.folio.utility.ModuleUtility.getClient;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.UUID;

import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.Response;
import org.folio.rest.support.builders.HoldingRequestBuilder;
import org.folio.rest.support.builders.ItemRequestBuilder;
import org.folio.rest.support.http.ResourceClient;
import org.folio.rest.support.kafka.FakeKafkaConsumer;
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
    FakeKafkaConsumer.clearAllEvents();
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

    boundWithCreatedMessagePublished(firstPart.getJson(), mainInstance.getId().toString());
    boundWithCreatedMessagePublished(secondPart.getJson(), anotherInstance.getId().toString());
    boundWithCreatedMessagePublished(thirdPart.getJson(), aThirdInstance.getId().toString());
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
    FakeKafkaConsumer.clearAllEvents();
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

    boundWithCreatedMessagePublished(partOneCreated.getJson(), instance1.getId().toString());
    boundWithCreatedMessagePublished(partTwoCreated.getJson(), instance2.getId().toString());

    // There is a potential bug with the old representation in these message
    // until this is investigated further, that check is removed
    boundWithUpdatedMessagePublished(partTwoCreated.getJson(), partTwoUpdated.getJson(),
      instance2.getId().toString(), instance3.getId().toString());
  }

  private JsonObject createBoundWithPartJson(UUID holdingsRecordId, UUID itemId) {
    JsonObject boundWithPart = new JsonObject();
    boundWithPart.put("holdingsRecordId", holdingsRecordId);
    boundWithPart.put("itemId", itemId);
    return boundWithPart;
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
}
