package org.folio.rest.impl;

import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.rest.support.AwaitConfiguration.awaitAtMost;
import static org.folio.utility.ModuleUtility.vertxUrl;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import java.util.stream.Stream;
import org.folio.rest.jaxrs.model.Holding;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.PublishReindexRecords;
import org.folio.rest.jaxrs.model.RecordIdsRange;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.messages.matchers.EventMessageMatchers;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class InventoryReindexRecordsPublishIT extends BaseIntegrationTest {

  private static final String RECORD1_ID = "0c45bb50-7c9b-48b0-86eb-178a494e25fe";
  private static final String RECORD2_ID = "10cd3a5a-d36f-4c7a-bc4f-e1ae3cf820c9";
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
    Future.all(
      records.stream()
        .map(record -> postgresClient.save(table, record))
        .toList()
      )
      .onFailure(ctx::failNow)
      .onSuccess(id -> ctx.completeNow());

    HttpClient client = vertx.createHttpClient();
    var publishRequestBody = new PublishReindexRecords()
      .withRecordType(recordType)
      .withRecordIdsRange(
        new RecordIdsRange().withFrom(RECORD1_ID).withTo(RECORD2_ID));

    doPost(client, "/inventory-reindex-records/publish", pojo2JsonObject(publishRequestBody))
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> assertEquals(HTTP_CREATED.toInt(), response.status()))))
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        var jsonItems = Stream.of(RECORD1_ID, RECORD2_ID, RECORD2_ID)
          .map(id -> {
            var jsonItem = new JsonObject();
            jsonItem.put("id", id);
            return jsonItem;
          })
          .toList();
        reindexMessagesPublished(jsonItems);
      })))
      .onComplete(ctx.succeeding(response -> ctx.completeNow()));
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

  private void reindexMessagesPublished(List<JsonObject> records) {
    final var recordIds = records.stream()
      .map(json -> json.getString("id"))
      .toList();

    awaitAtMost().until(() -> KAFKA_CONSUMER.getMessagesForReindexRecords(recordIds),
      hasSize(records.size()));

    records.forEach(instance -> assertThat(KAFKA_CONSUMER.getMessagesForReindexRecords(recordIds),
      EVENT_MESSAGE_MATCHERS.hasReindexEventMessageFor(instance)));
  }
}
