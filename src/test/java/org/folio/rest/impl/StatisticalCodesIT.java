package org.folio.rest.impl;

import static org.folio.HttpStatus.HTTP_BAD_REQUEST;
import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.folio.persist.InstanceRepository.INSTANCE_TABLE;
import static org.folio.rest.api.entities.Instance.STATISTICAL_CODE_IDS_KEY;
import static org.folio.rest.impl.InstanceTypeApi.INSTANCE_TYPE_TABLE;
import static org.folio.rest.impl.StatisticalCodeApi.STATISTICAL_CODE_TABLE;
import static org.folio.rest.impl.StatisticalCodeTypeApi.STATISTICAL_CODE_TYPE_TABLE;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.assertj.core.api.Assertions;
import org.folio.rest.api.entities.Instance;
import org.folio.rest.jaxrs.model.InstanceType;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.jaxrs.model.StatisticalCode;
import org.folio.rest.jaxrs.model.StatisticalCodeType;
import org.folio.rest.jaxrs.model.StatisticalCodes;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StatisticalCodesIT extends BaseReferenceDataIntegrationTest<StatisticalCode, StatisticalCodes> {

  private String statisticalCodeTypeId;

  @Override
  protected String referenceTable() {
    return STATISTICAL_CODE_TABLE;
  }

  @Override
  protected String resourceUrl() {
    return "/statistical-codes";
  }

  @Override
  protected Class<StatisticalCode> targetClass() {
    return StatisticalCode.class;
  }

  @Override
  protected Class<StatisticalCodes> collectionClass() {
    return StatisticalCodes.class;
  }

  @Override
  protected StatisticalCode sampleRecord() {
    return new StatisticalCode()
      .withName("test-type")
      .withCode("test-code")
      .withStatisticalCodeTypeId(statisticalCodeTypeId)
      .withSource("test-source");
  }

  @Override
  protected Function<StatisticalCodes, List<StatisticalCode>> collectionRecordsExtractor() {
    return StatisticalCodes::getStatisticalCodes;
  }

  @Override
  protected List<Function<StatisticalCode, Object>> recordFieldExtractors() {
    return List.of(StatisticalCode::getName, StatisticalCode::getSource);
  }

  @Override
  protected Function<StatisticalCode, String> idExtractor() {
    return StatisticalCode::getId;
  }

  @Override
  protected Function<StatisticalCode, Metadata> metadataExtractor() {
    return StatisticalCode::getMetadata;
  }

  @Override
  protected UnaryOperator<StatisticalCode> recordModifyingFunction() {
    return codeType -> codeType.withName("name-updated").withSource("source-updated");
  }

  @Override
  protected List<String> queries() {
    return List.of("name==test-type", "source=test-source", "statisticalCodeTypeId==" + statisticalCodeTypeId);
  }

  @BeforeEach
  void beforeEach(Vertx vertx, VertxTestContext ctx) {
    var postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);
    var statisticalCodeType = new StatisticalCodeType().withName("code-type").withSource("sct");
    postgresClient.save(STATISTICAL_CODE_TYPE_TABLE, statisticalCodeType)
      .onFailure(ctx::failNow)
      .onSuccess(id -> {
        statisticalCodeTypeId = id;
        ctx.completeNow();
      });
  }

  @AfterEach
  void afterEach(Vertx vertx, VertxTestContext ctx) {
    var postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);

    postgresClient.delete(INSTANCE_TABLE, (CQLWrapper) null)
      .compose(rows -> postgresClient.delete(STATISTICAL_CODE_TABLE, (CQLWrapper) null))
      .compose(rows -> postgresClient.delete(STATISTICAL_CODE_TYPE_TABLE, (CQLWrapper) null))
      .onFailure(ctx::failNow)
      .onComplete(event -> ctx.completeNow());
  }

  @Test
  void delete_cannotDeleteRecordThatAssociatedWithInstance(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);

    var newRecord = sampleRecord();
    var instanceType = new InstanceType().withName("code-type").withCode("it").withSource("sct");

    postgresClient.save(referenceTable(), newRecord)
      .compose(statisticalCodeId -> postgresClient.save(INSTANCE_TYPE_TABLE, instanceType)
        .compose(instanceTypeId -> doPost(client, "/instance-storage/instances",
          new Instance("test-instance", "folio", instanceTypeId)
            .put(STATISTICAL_CODE_IDS_KEY, List.of(statisticalCodeId))
            .getJson())
          .onComplete(verifyStatus(ctx, HTTP_CREATED))
          .compose(v -> doDelete(client, resourceUrlById(statisticalCodeId))
            .onComplete(verifyStatus(ctx, HTTP_BAD_REQUEST))
            .compose(response -> postgresClient.getById(referenceTable(), statisticalCodeId, targetClass()))
            .onComplete(ctx.succeeding(dbRecord -> ctx.verify(() -> {
              Assertions.assertThat(dbRecord)
                .as("Verify record still exists in database")
                .isNotNull();
              ctx.completeNow();
            })))))
      );
  }

  @Test
  void post_cannotCreateRecordWhenNameIsSameButInUpperCase(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);

    var newRecord = sampleRecord();

    postgresClient.save(referenceTable(), newRecord)
      .compose(id -> {
        var updatedRecord = sampleRecord().withName(newRecord.getName().toUpperCase()).withCode("new-code");
        return doPost(client, resourceUrl(), pojo2JsonObject(updatedRecord))
          .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
          .onComplete(event -> ctx.verify(() -> {
            assertThat(event.result().jsonBody().toString(), containsString(
              "value already exists in table statistical_code: %s".formatted(newRecord.getName())));
            ctx.completeNow();
          }));
      });
  }
}
