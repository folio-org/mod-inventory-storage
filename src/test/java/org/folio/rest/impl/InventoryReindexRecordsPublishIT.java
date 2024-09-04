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

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.assertj.core.api.Assertions;
import org.folio.rest.jaxrs.model.Holding;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.PublishReindexRecords;
import org.folio.rest.jaxrs.model.RecordIdsRange;
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
                                                            PublishReindexRecords.RecordType recordType,
                                                            List<Object> records,
                                                            Vertx vertx,
                                                            VertxTestContext ctx) {
    var postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);
    var client = vertx.createHttpClient();
    var rangeId = UUID.randomUUID().toString();
    var publishRequestBody = new PublishReindexRecords()
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
    //prepare data for instances, holdings, items and boundWith
    mockUserTenantsForNonConsortiumMember();
    var client = vertx.createHttpClient();
    var mainInstanceId = UUID.randomUUID();
    var holdingsId = UUID.randomUUID();
    var itemId = UUID.randomUUID();
    var anotherInstanceId = UUID.randomUUID();
    var consortiumInstanceId = UUID.randomUUID();

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

    //prepare request
    var rangeId = UUID.randomUUID().toString();
    //sort created instance ids to have all three falling into request range
    var instanceIds = Stream.of(mainInstanceId, consortiumInstanceId, anotherInstanceId)
      .map(UUID::toString).sorted().toList();
    var publishRequestBody = new PublishReindexRecords()
      .withId(rangeId)
      .withRecordType(PublishReindexRecords.RecordType.INSTANCE)
      .withRecordIdsRange(
        new RecordIdsRange().withFrom(instanceIds.get(0)).withTo(instanceIds.get(2)));

    var postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);
    //save entities sequentially
    postgresClient.save(INSTANCE_TABLE, mainInstanceId.toString(), mainInstance)
      //holdings, item, boundWith entities are all needed to fill the isBoundWith flag in kafka event
      .compose(r -> postgresClient.save(HOLDING_TABLE, holdingsId.toString(), holding))
      .compose(r -> postgresClient.save(ITEM_TABLE, itemId.toString(), item))
      .compose(r -> postgresClient.save(INSTANCE_TABLE, anotherInstanceId.toString(), anotherInstance))
      //create instance with consortium source to verify it's ignored
      .compose(r -> postgresClient.save(INSTANCE_TABLE, consortiumInstanceId.toString(), consortiumInstance))
      .compose(r -> postgresClient.save(BOUND_WITH_TABLE, boundWith))
      //trigger records publishing
      .compose(r -> doPost(client, "/inventory-reindex-records/publish", pojo2JsonObject(publishRequestBody)))
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> assertEquals(HTTP_CREATED.toInt(), response.status()))))
      .onComplete(ctx.succeeding(response -> ctx.completeNow()));

    //verify kafka event
    awaitAtMost().until(() -> KAFKA_CONSUMER.getMessagesForReindexRecord(rangeId),
      hasSize(1));

    mainInstance.put("isBoundWith", true);
    anotherInstance.put("isBoundWith", false);

    var kafkaMessages = KAFKA_CONSUMER.getMessagesForReindexRecord(rangeId);

    assertThat(kafkaMessages,
      EVENT_MESSAGE_MATCHERS.hasReindexEventMessageFor());
    var event = kafkaMessages.stream().toList().get(0).getBody();
    var records = event.getJsonArray("records").stream()
      .map(o -> new JsonObject((String) o))
      .toList();
    Assertions.assertThat(event.getString("recordType")).isEqualTo(PublishReindexRecords.RecordType.INSTANCE.value());
    Assertions.assertThat(records).contains(mainInstance, anotherInstance);
  }

  private static Stream<Arguments> reindexTypesProvider() {
    return Stream.of(
      arguments(
        ITEM_TABLE,
        PublishReindexRecords.RecordType.ITEM,
        List.of(new Item().withId(RECORD1_ID), new Item().withId(RECORD2_ID))),
      arguments(
        HOLDING_TABLE,
        PublishReindexRecords.RecordType.HOLDING,
        List.of(new Holding().withId(RECORD1_ID), new Holding().withId(RECORD2_ID)))
    );
  }
}
