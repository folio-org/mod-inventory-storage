package org.folio.rest.api;

import static org.awaitility.Awaitility.await;
import static org.folio.utility.VertxUtility.getClient;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import junitparams.JUnitParamsRunner;
import lombok.SneakyThrows;
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
    boundWithPartsClient.create(createBoundWithPartJson(mainHoldingsRecord.getId(),item.getId()));
    IndividualResource secondPart = boundWithPartsClient.create(createBoundWithPartJson(anotherHoldingsRecord.getId(),item.getId()));
    boundWithPartsClient.create(createBoundWithPartJson(aThirdHoldingsRecord.getId(),item.getId()));

    Response boundWithGETResponseForPartById = boundWithPartsClient.getById(secondPart.getId());

    List<JsonObject> getAllPartsForBoundWithItem = boundWithPartsClient.getByQuery("?query=itemId==" + item.getId());

    assertThat(boundWithGETResponseForPartById.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(getAllPartsForBoundWithItem.size(), is(3));

    await().atMost(10, TimeUnit.SECONDS)
      .until(() -> FakeKafkaConsumer.getAllPublishedBoundWithIdsCount() == 3);
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
    boundWithPartsClient.create(createBoundWithPartJson(holdingsRecord1.getId(),item.getId()));
    IndividualResource part2Created = boundWithPartsClient.create(createBoundWithPartJson(holdingsRecord2.getId(),item.getId()));

    List<JsonObject> getAllPartsForBoundWithItem = boundWithPartsClient.getByQuery("?query=itemId==" + item.getId());
    List<JsonObject> part2 = boundWithPartsClient.getByQuery("?query=holdingsRecordId==" + holdingsRecord2.getId()+"");

    IndividualResource instance3 = createInstance("Instance 3");
    IndividualResource holdingsRecord3 = createHoldingsRecord(instance3.getId());

    Response updateResponse = boundWithPartsClient.attemptToReplace(
      part2Created.getId(),
      createBoundWithPartJson(holdingsRecord3.getId(), item.getId()));

    List<JsonObject> getAllPartsForBoundWithItemAgain = boundWithPartsClient.getByQuery("?query=itemId==" + item.getId());
    List<JsonObject> oldPart2Gone = boundWithPartsClient.getByQuery("?query=holdingsRecordId==" + holdingsRecord2.getId());
    List<JsonObject> newPart2 = boundWithPartsClient.getByQuery("?query=holdingsRecordId==" + holdingsRecord3.getId());

    assertThat(getAllPartsForBoundWithItem.size(), is(2));
    assertThat(part2.toString(), part2.size(), is(1));

    assertThat(updateResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
    assertThat(getAllPartsForBoundWithItemAgain.size(), is(2));
    assertThat(oldPart2Gone.size(), is(0));
    assertThat(newPart2.size(), is(1));

    await().atMost(10, TimeUnit.SECONDS)
      .until(() -> FakeKafkaConsumer.getAllPublishedBoundWithIdsCount() == 3);
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
