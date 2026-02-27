package org.folio.rest.impl;

import static org.folio.HttpStatus.HTTP_OK;
import static org.folio.persist.InstanceRepository.INSTANCE_TABLE;
import static org.folio.rest.support.AwaitConfiguration.awaitAtMost;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.folio.utility.S3Utility.REINDEX_BUCKET;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.RecordIdsRange;
import org.folio.rest.jaxrs.model.ReindexRecordsRequest;
import org.folio.rest.jaxrs.model.ReindexRecordsRequest.RecordType;
import org.folio.rest.persist.PostgresClient;
import org.folio.services.s3storage.FolioS3ClientFactory;
import org.folio.services.s3storage.FolioS3ClientFactory.S3ConfigType;
import org.junit.jupiter.api.Test;

class InventoryReindexRecordsExportIT extends BaseIntegrationTest {

  private static final String ITEM_TABLE = "item";
  private static final String HOLDING_TABLE = "holdings_record";

  @Test
  void post_shouldReturn200_whenExportingItems(Vertx vertx, VertxTestContext ctx) {
    var postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);

    // Hierarchy: instance → holding → items
    var instanceId = UUID.randomUUID().toString();
    var holdingId = UUID.randomUUID().toString();
    var item1Id = UUID.randomUUID().toString();
    var item2Id = UUID.randomUUID().toString();
    var rangeId = UUID.randomUUID().toString();

    var instance = new Instance().withId(instanceId);
    var holding = new HoldingsRecord().withId(holdingId).withInstanceId(instanceId);
    var item1 = new Item().withId(item1Id).withHoldingsRecordId(holdingId);
    var item2 = new Item().withId(item2Id).withHoldingsRecordId(holdingId);
    var request = buildRequest(rangeId, RecordType.ITEM, item1Id, item2Id);

    var saveFuture = postgresClient.save(INSTANCE_TABLE, instanceId, instance)
      .compose(r -> postgresClient.save(HOLDING_TABLE, holdingId, holding))
      .compose(r -> postgresClient.save(ITEM_TABLE, item1Id, item1))
      .compose(r -> postgresClient.save(ITEM_TABLE, item2Id, item2));

    triggerExport(vertx, ctx, saveFuture, request);
    verifyExportEvent(rangeId, RecordType.ITEM, List.of(item1Id, item2Id));
  }

  @Test
  void post_shouldReturn200_whenExportingHoldings(Vertx vertx, VertxTestContext ctx) {
    var postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);

    // Hierarchy: instance → holdings
    var instanceId = UUID.randomUUID().toString();
    var holding1Id = UUID.randomUUID().toString();
    var holding2Id = UUID.randomUUID().toString();
    var rangeId = UUID.randomUUID().toString();

    var instance = new Instance().withId(instanceId);
    var holding1 = new HoldingsRecord().withId(holding1Id).withInstanceId(instanceId);
    var holding2 = new HoldingsRecord().withId(holding2Id).withInstanceId(instanceId);
    var request = buildRequest(rangeId, RecordType.HOLDINGS, holding1Id, holding2Id);

    var saveFuture = postgresClient.save(INSTANCE_TABLE, instanceId, instance)
      .compose(r -> postgresClient.save(HOLDING_TABLE, holding1Id, holding1))
      .compose(r -> postgresClient.save(HOLDING_TABLE, holding2Id, holding2));

    triggerExport(vertx, ctx, saveFuture, request);
    verifyExportEvent(rangeId, RecordType.HOLDINGS, List.of(holding1Id, holding2Id));
  }

  @Test
  void post_shouldReturn200_whenExportingInstances(Vertx vertx, VertxTestContext ctx) {
    mockUserTenantsForNonConsortiumMember();
    var postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);

    var instanceId = UUID.randomUUID().toString();
    var rangeId = UUID.randomUUID().toString();

    var instance = new Instance().withId(instanceId).withSource("FOLIO");
    var request = buildRequest(rangeId, RecordType.INSTANCE, instanceId, instanceId);

    var saveFuture = postgresClient.save(INSTANCE_TABLE, instanceId, instance);

    triggerExport(vertx, ctx, saveFuture, request);
    verifyExportEvent(rangeId, RecordType.INSTANCE, List.of(instanceId));
  }

  /**
   * Builds a {@link ReindexRecordsRequest} with a sorted range ({@code from ≤ to}).
   * UUID string order is equivalent to UUID numeric order, so {@link String#compareTo} is safe.
   */
  private static ReindexRecordsRequest buildRequest(String rangeId, RecordType type,
                                                    String id1, String id2) {
    var from = id1.compareTo(id2) <= 0 ? id1 : id2;
    var to = id1.compareTo(id2) <= 0 ? id2 : id1;
    return new ReindexRecordsRequest()
      .withId(rangeId)
      .withTraceId(rangeId)
      .withRecordType(type)
      .withRecordIdsRange(new RecordIdsRange().withFrom(from).withTo(to));
  }

  /**
   * Chains {@code saveFuture} with the export POST and asserts HTTP 200.
   */
  private void triggerExport(Vertx vertx, VertxTestContext ctx,
                             Future<?> saveFuture, ReindexRecordsRequest request) {
    var client = vertx.createHttpClient();
    saveFuture
      .compose(r -> doPost(client, "/inventory-reindex-records/export", pojo2JsonObject(request)))
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> assertEquals(HTTP_OK.toInt(), response.status()))))
      .onComplete(ctx.succeeding(response -> ctx.completeNow()));
  }

  /**
   * Awaits the Kafka file-ready event and verifies event fields + S3 NDJSON content.
   */
  private void verifyExportEvent(String rangeId, RecordType recordType, List<String> expectedIds) {
    awaitAtMost().until(() -> KAFKA_CONSUMER.getMessagesForReindexFileReady(rangeId), hasSize(1));

    var event = KAFKA_CONSUMER.getMessagesForReindexFileReady(rangeId).stream().toList().getFirst().getBody();
    assertEquals(recordType.value(), event.getString("recordType"));
    assertEquals(REINDEX_BUCKET, event.getString("bucket"));
    assertEquals(rangeId, event.getString("rangeId"));

    var objectKey = event.getString("objectKey");
    assertNotNull(objectKey);
    verifyS3NdjsonContainsIds(objectKey, expectedIds);
  }

  /**
   * Reads the NDJSON file at {@code objectKey} from the reindex S3 bucket and asserts
   * that every id in {@code expectedIds} appears as the {@code "id"} field in at least one line.
   */
  private static void verifyS3NdjsonContainsIds(String objectKey, List<String> expectedIds) {
    var s3Client = FolioS3ClientFactory.getFolioS3Client(S3ConfigType.REINDEX);
    try (var reader = new BufferedReader(
      new InputStreamReader(s3Client.read(objectKey), StandardCharsets.UTF_8))) {

      var ids = reader.lines()
        .map(JsonObject::new)
        .map(json -> json.getString("id"))
        .collect(Collectors.toSet());

      for (String expectedId : expectedIds) {
        assertTrue(ids.contains(expectedId),
          "Expected id %s not found in S3 NDJSON at %s".formatted(expectedId, objectKey));
      }
    } catch (Exception e) {
      throw new AssertionError("Failed to read S3 object: " + objectKey, e);
    }
  }
}
