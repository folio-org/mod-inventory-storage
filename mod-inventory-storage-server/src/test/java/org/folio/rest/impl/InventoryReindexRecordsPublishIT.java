package org.folio.rest.impl;

import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.persist.InstanceRepository.INSTANCE_TABLE;
import static org.folio.rest.api.TestBaseWithInventoryUtil.createInstanceRequest;
import static org.folio.rest.impl.BoundWithPartApi.BOUND_WITH_TABLE;
import static org.folio.rest.support.AwaitConfiguration.awaitAtMost;
import static org.folio.utility.ModuleUtility.vertxUrl;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.assertj.core.api.Assertions;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.RecordIdsRange;
import org.folio.rest.jaxrs.model.ReindexRecordsRequest;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.builders.ItemRequestBuilder;
import org.folio.rest.support.messages.matchers.EventMessageMatchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class InventoryReindexRecordsPublishIT extends BaseIntegrationTest {

  private static final String RECORD1_ID = "0b96a642-5e7f-452d-9cae-9cee66c9a892";
  private static final String RECORD2_ID = "0e67c5b4-8585-49c7-bc8a-e5c7c5fc3f34";
  private static final String ITEM_TABLE = "item";
  private static final String HOLDING_TABLE = "holdings_record";

  private static final EventMessageMatchers EVENT_MESSAGE_MATCHERS
    = new EventMessageMatchers(TENANT_ID, vertxUrl(""));

  @MethodSource("reindexTypesProvider")
  @ParameterizedTest
  void post_shouldReturn201_whenPublishingRecordsForReindex(String table,
                                                            ReindexRecordsRequest.RecordType recordType,
                                                            List<Object> records,
                                                            Vertx vertx,
                                                            VertxTestContext ctx) {
    var postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);
    var client = vertx.createHttpClient();
    var rangeId = UUID.randomUUID().toString();
    var publishRequestBody = new ReindexRecordsRequest()
      .withId(rangeId)
      .withRecordType(recordType)
      .withRecordIdsRange(
        new RecordIdsRange().withFrom(RECORD1_ID).withTo(RECORD2_ID));
    postgresClient.save(table, RECORD1_ID, records.get(0))
      .compose(r -> postgresClient.save(table, RECORD2_ID, records.get(1)))
      .compose(r -> doPost(client, "/inventory-reindex-records/publish", pojo2JsonObject(publishRequestBody)))
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> assertEquals(HTTP_CREATED.toInt(), response.status()))))
      .onComplete(ctx.succeeding(response -> ctx.completeNow()));

    awaitAtMost().until(() -> KAFKA_CONSUMER.getMessagesForReindexRecord(rangeId),
      hasSize(1));
    assertThat(KAFKA_CONSUMER.getMessagesForReindexRecord(rangeId),
      EVENT_MESSAGE_MATCHERS.hasReindexEventMessageFor());
  }

  @Test
  @SneakyThrows
  void post_shouldReturn201_whenPublishingInstancesForReindex(Vertx vertx,
                                                              VertxTestContext ctx) {
    mockUserTenantsForNonConsortiumMember();
    var client = vertx.createHttpClient();

    var testData = prepareInstanceReindexTestData();
    var publishRequestBody = createPublishRequestBody(testData);

    saveTestDataAndTriggerPublish(vertx, client, testData, publishRequestBody)
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> assertEquals(HTTP_CREATED.toInt(), response.status()))))
      .onComplete(ctx.succeeding(response -> ctx.completeNow()));

    verifyKafkaEventForInstanceReindex(testData);
  }

  private InstanceReindexTestData prepareInstanceReindexTestData() {
    var mainInstanceId = UUID.randomUUID();
    var holdingsId = UUID.randomUUID();
    var itemId = UUID.randomUUID();
    var anotherInstanceId = UUID.randomUUID();
    var consortiumInstanceId = UUID.randomUUID();
    var rangeId = UUID.randomUUID().toString();

    var mainInstance = createInstanceRequest(mainInstanceId, "TEST", "mm", new JsonArray(), new JsonArray(),
      null, new JsonArray());
    var holding = new JsonObject()
      .put("id", holdingsId)
      .put("instanceId", mainInstanceId);
    var item = new ItemRequestBuilder()
      .withId(itemId)
      .forHolding(holdingsId)
      .create();
    var boundWith = new JsonObject().put("itemId", itemId).put("holdingsRecordId", holdingsId);
    var anotherInstance = createInstanceRequest(anotherInstanceId, "TEST", "am", new JsonArray(), new JsonArray(),
      null, new JsonArray());
    var consortiumInstance = createInstanceRequest(consortiumInstanceId, "CONSORTIUM-TEST", "cm", new JsonArray(),
      new JsonArray(), null, new JsonArray());

    return new InstanceReindexTestData(mainInstanceId, holdingsId, itemId, anotherInstanceId, consortiumInstanceId,
      rangeId, mainInstance, holding, item, boundWith, anotherInstance, consortiumInstance);
  }

  private ReindexRecordsRequest createPublishRequestBody(InstanceReindexTestData testData) {
    var instanceIds = Stream.of(testData.mainInstanceId, testData.consortiumInstanceId, testData.anotherInstanceId)
      .map(UUID::toString).sorted().toList();
    return new ReindexRecordsRequest()
      .withId(testData.rangeId)
      .withRecordType(ReindexRecordsRequest.RecordType.INSTANCE)
      .withRecordIdsRange(
        new RecordIdsRange().withFrom(instanceIds.get(0)).withTo(instanceIds.get(2)));
  }

  private Future<TestResponse> saveTestDataAndTriggerPublish(Vertx vertx,
                                                             io.vertx.core.http.HttpClient client,
                                                             InstanceReindexTestData testData,
                                                             ReindexRecordsRequest request) {
    var postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);
    return postgresClient.save(INSTANCE_TABLE, testData.mainInstanceId.toString(), testData.mainInstance)
      .compose(r -> postgresClient.save(HOLDING_TABLE, testData.holdingsId.toString(), testData.holding))
      .compose(r -> postgresClient.save(ITEM_TABLE, testData.itemId.toString(), testData.item))
      .compose(r -> postgresClient.save(
        INSTANCE_TABLE, testData.anotherInstanceId.toString(), testData.anotherInstance))
      .compose(r -> postgresClient.save(
        INSTANCE_TABLE, testData.consortiumInstanceId.toString(), testData.consortiumInstance))
      .compose(r -> postgresClient.save(BOUND_WITH_TABLE, testData.boundWith))
      .compose(r -> doPost(client, "/inventory-reindex-records/publish", pojo2JsonObject(request)));
  }

  private void verifyKafkaEventForInstanceReindex(InstanceReindexTestData testData) {
    awaitAtMost().until(() -> KAFKA_CONSUMER.getMessagesForReindexRecord(testData.rangeId),
      hasSize(1));

    testData.mainInstance.put("isBoundWith", true);
    testData.anotherInstance.put("isBoundWith", false);

    var kafkaMessages = KAFKA_CONSUMER.getMessagesForReindexRecord(testData.rangeId);

    assertThat(kafkaMessages,
      EVENT_MESSAGE_MATCHERS.hasReindexEventMessageFor());
    var event = kafkaMessages.stream().toList().getFirst().getBody();
    var records = event.getJsonArray("records").stream()
      .map(JsonObject::mapFrom)
      .toList();
    Assertions.assertThat(event.getString("recordType"))
      .isEqualTo(ReindexRecordsRequest.RecordType.INSTANCE.value());
    Assertions.assertThat(records).contains(testData.mainInstance, testData.anotherInstance);
  }

  private static Stream<Arguments> reindexTypesProvider() {
    return Stream.of(
      arguments(
        ITEM_TABLE,
        ReindexRecordsRequest.RecordType.ITEM,
        List.of(new Item().withId(RECORD1_ID), new Item().withId(RECORD2_ID))),
      arguments(
        HOLDING_TABLE,
        ReindexRecordsRequest.RecordType.HOLDINGS,
        List.of(new HoldingsRecord().withId(RECORD1_ID), new HoldingsRecord().withId(RECORD2_ID)))
    );
  }

  private record InstanceReindexTestData(UUID mainInstanceId, UUID holdingsId, UUID itemId,
                                         UUID anotherInstanceId, UUID consortiumInstanceId, String rangeId,
                                         JsonObject mainInstance, JsonObject holding, JsonObject item,
                                         JsonObject boundWith, JsonObject anotherInstance,
                                         JsonObject consortiumInstance) {
  }
}
