package org.folio.rest.impl;

import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.rest.support.AwaitConfiguration.awaitAtMost;
import static org.folio.utility.ModuleUtility.vertxUrl;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import java.util.UUID;
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
    Future.all(
      records.stream()
        .map(record -> postgresClient.save(table, record))
        .toList()
      )
      .onFailure(ctx::failNow)
      .onSuccess(id -> ctx.completeNow());

    HttpClient client = vertx.createHttpClient();
    var rangeId = UUID.randomUUID().toString();
    var publishRequestBody = new PublishReindexRecords()
      .withId(rangeId)
      .withRecordType(recordType)
      .withRecordIdsRange(
        new RecordIdsRange().withFrom(RECORD1_ID).withTo(RECORD2_ID));

    doPost(client, "/inventory-reindex-records/publish", pojo2JsonObject(publishRequestBody))
      .onComplete(verifyStatus(ctx, HTTP_CREATED))
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        var jsonItem = new JsonObject();
        jsonItem.put("id", rangeId);
        reindexMessagePublished(jsonItem);
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

  private void reindexMessagePublished(JsonObject record) {
    var id = record.getString("id");
    awaitAtMost().until(() -> KAFKA_CONSUMER.getMessagesForReindexRecords(List.of(id)),
      hasSize(1));

    assertThat(KAFKA_CONSUMER.getMessagesForReindexRecords(List.of(id)),
      EVENT_MESSAGE_MATCHERS.hasReindexEventMessageFor(record));
  }
}
