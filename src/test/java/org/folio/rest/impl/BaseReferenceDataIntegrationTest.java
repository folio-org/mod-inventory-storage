package org.folio.rest.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.HttpStatus.HTTP_NO_CONTENT;
import static org.folio.HttpStatus.HTTP_OK;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxTestContext;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

abstract class BaseReferenceDataIntegrationTest<T, C> extends BaseIntegrationTest {

  protected abstract String referenceTable();

  protected abstract String resourceUrl();

  protected abstract Class<T> targetClass();

  protected abstract Class<C> collectionClass();

  protected abstract T sampleRecord();

  protected abstract Function<C, List<T>> collectionRecordsExtractor();

  protected abstract List<Function<T, Object>> recordFieldExtractors();

  protected abstract Function<T, String> idExtractor();

  protected abstract Function<T, Metadata> metadataExtractor();

  protected abstract UnaryOperator<T> recordModifyingFunction();

  protected abstract List<String> queries();

  protected String resourceUrlById(String id) {
    return resourceUrl() + "/" + id;
  }

  protected void verifyRecordHasSameId(T actual, String expectedId, String description) {
    assertThat(actual)
      .as(description)
      .isNotNull()
      .extracting(idExtractor())
      .isEqualTo(expectedId);
  }

  protected void verifyRecordFields(T actual, T expected, List<Function<T, Object>> fieldExtractors,
                                    String description) {
    for (Function<T, Object> method : fieldExtractors) {
      assertThat(actual)
        .as(description)
        .extracting(method)
        .isEqualTo(method.apply(expected));
    }
  }

  protected Metadata getMetadata(T createdRecord) {
    return metadataExtractor().apply(createdRecord);
  }

  protected String getRecordId(T recordObj) {
    return idExtractor().apply(recordObj);
  }

  protected String getRecordId(TestResponse response) {
    return getRecordId(response.bodyAsClass(targetClass()));
  }

  @AfterEach
  void tearDown(Vertx vertx, VertxTestContext ctx) {
    var postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);
    postgresClient.delete(referenceTable(), (CQLWrapper) null)
      .onComplete(event -> ctx.completeNow());
  }

  @Test
  void getCollection_shouldReturn200AndEmptyCollection(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    doGet(client, resourceUrl())
      .onComplete(verifyStatus(ctx, HTTP_OK))
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        var collection = response.bodyAsClass(collectionClass());

        assertThat(collection)
          .isNotNull()
          .hasFieldOrPropertyWithValue("totalRecords", 0)
          .extracting(collectionRecordsExtractor()).asInstanceOf(InstanceOfAssertFactories.COLLECTION)
          .isEmpty();
        ctx.completeNow();
      })));
  }

  @Test
  void getCollection_shouldReturn200AndRecordCollectionBasedOnQuery(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);

    var newRecord = sampleRecord();

    postgresClient.save(referenceTable(), newRecord)
      .compose(s -> {
        List<Future<TestResponse>> futures = new ArrayList<>();
        for (String query : queries()) {
          var testResponseFuture = doGet(client, resourceUrl() + "?query=" + query + "&limit=500")
            .onComplete(verifyStatus(ctx, HTTP_OK))
            .andThen(ctx.succeeding(response -> ctx.verify(() -> {
              var collection = response.bodyAsClass(collectionClass());
              assertThat(collection)
                .as("verify collection for query: %s", query)
                .isNotNull()
                .hasFieldOrPropertyWithValue("totalRecords", 1)
                .extracting(collectionRecordsExtractor()).asInstanceOf(InstanceOfAssertFactories.COLLECTION)
                .hasSize(1);

              var collectionRecord = collectionRecordsExtractor().apply(collection).getFirst();

              verifyRecordFields(collectionRecord, newRecord, recordFieldExtractors(),
                String.format("verify collection's record for query: %s", query));
            })));
          futures.add(testResponseFuture);
        }
        return Future.all(futures);
      })
      .onFailure(ctx::failNow)
      .onSuccess(event -> ctx.completeNow());
  }

  @Test
  void get_shouldReturn200AndRecordById(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);

    var newRecord = sampleRecord();

    postgresClient.save(referenceTable(), newRecord)
      .compose(id -> doGet(client, resourceUrlById(id))
        .onComplete(verifyStatus(ctx, HTTP_OK))
        .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
          var fetchedRecord = response.bodyAsClass(targetClass());
          verifyRecordHasSameId(fetchedRecord, id, "verify record by id");

          verifyRecordFields(fetchedRecord, newRecord, recordFieldExtractors(),
            "verify record by id has same values as saved record");
          ctx.completeNow();
        }))));
  }

  @Test
  void post_shouldReturn201AndCreatedRecord(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);

    var newRecord = sampleRecord();

    doPost(client, resourceUrl(), pojo2JsonObject(newRecord))
      .onComplete(verifyStatus(ctx, HTTP_CREATED))
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        var createdRecord = response.bodyAsClass(targetClass());

        assertNotNull(createdRecord);
        assertNotNull(getRecordId(createdRecord));

        assertThat(getMetadata(createdRecord))
          .as("Verify created record metadata")
          .isNotNull()
          .hasNoNullFieldsOrPropertiesExcept("createdByUsername", "updatedByUsername")
          .extracting(Metadata::getCreatedByUserId, Metadata::getUpdatedByUserId)
          .containsExactly(USER_ID, USER_ID);

        for (Function<T, Object> method : recordFieldExtractors()) {
          assertEquals(method.apply(newRecord), method.apply(createdRecord));
        }
      })))
      .compose(testResponse -> postgresClient.getById(referenceTable(), getRecordId(testResponse), targetClass())
        .onComplete(ctx.succeeding(dbRecord -> ctx.verify(() -> {
          var recordFromResponse = testResponse.bodyAsClass(targetClass());
          verifyRecordHasSameId(dbRecord, getRecordId(recordFromResponse), "Verify created record exists in database");

          verifyRecordFields(dbRecord, recordFromResponse,
            recordFieldExtractors(), "Verify created record in database has same values as in response");
          ctx.completeNow();
        }))));
  }

  @Test
  void put_shouldReturn204AndRecordIsUpdated(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);

    var newRecord = sampleRecord();

    postgresClient.save(referenceTable(), newRecord)
      .compose(id -> {
        var updatedRecord = recordModifyingFunction().apply(newRecord);
        return doPut(client, resourceUrlById(id), pojo2JsonObject(updatedRecord))
          .onComplete(verifyStatus(ctx, HTTP_NO_CONTENT))
          .compose(r -> postgresClient.getById(referenceTable(), id, targetClass())
            .onComplete(ctx.succeeding(dbRecord -> ctx.verify(() -> {
              verifyRecordHasSameId(dbRecord, id, "Verify updated record exists in database");

              verifyRecordFields(dbRecord, updatedRecord, recordFieldExtractors(),
                "Verify update record in database has same values as in request");
              ctx.completeNow();
            }))));
      });
  }

  @Test
  void delete_shouldReturn204AndRecordIsDeleted(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);

    var newRecord = sampleRecord();

    postgresClient.save(referenceTable(), newRecord)
      .compose(id -> doDelete(client, resourceUrlById(id))
        .onComplete(verifyStatus(ctx, HTTP_NO_CONTENT))
        .compose(response -> postgresClient.getById(referenceTable(), id, targetClass()))
        .onComplete(ctx.succeeding(dbRecord -> ctx.verify(() -> {
          assertThat(dbRecord)
            .as("Verify deleted record doesn't exists in database")
            .isNull();

          ctx.completeNow();
        }))));
  }
}
