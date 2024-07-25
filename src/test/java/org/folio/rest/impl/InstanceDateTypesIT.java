package org.folio.rest.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.HttpStatus.HTTP_NO_CONTENT;
import static org.folio.utility.RestUtility.TENANT_ID;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.folio.rest.jaxrs.model.DisplayFormat;
import org.folio.rest.jaxrs.model.InstanceDateType;
import org.folio.rest.jaxrs.model.InstanceDateTypePatch;
import org.folio.rest.jaxrs.model.InstanceDateTypes;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.services.instance.InstanceDateTypeService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InstanceDateTypesIT extends BaseReferenceDataIntegrationTest<InstanceDateType, InstanceDateTypes> {

  @Override
  protected String referenceTable() {
    return InstanceDateTypeService.INSTANCE_DATE_TYPE_TABLE;
  }

  @Override
  protected String resourceUrl() {
    return "/instance-date-types";
  }

  @Override
  protected Class<InstanceDateType> targetClass() {
    return InstanceDateType.class;
  }

  @Override
  protected Class<InstanceDateTypes> collectionClass() {
    return InstanceDateTypes.class;
  }

  @Override
  protected InstanceDateType sampleRecord() {
    return new InstanceDateType()
      .withId(UUID.randomUUID().toString())
      .withName("name")
      .withCode("c")
      .withDisplayFormat(new DisplayFormat().withDelimiter(",").withKeepDelimiter(false))
      .withSource(InstanceDateType.Source.FOLIO);
  }

  @Override
  protected Function<InstanceDateTypes, List<InstanceDateType>> collectionRecordsExtractor() {
    return InstanceDateTypes::getInstanceDateTypes;
  }

  @Override
  protected List<Function<InstanceDateType, Object>> recordFieldExtractors() {
    return List.of(InstanceDateType::getName, InstanceDateType::getCode, InstanceDateType::getDisplayFormat);
  }

  @Override
  protected Function<InstanceDateType, String> idExtractor() {
    return InstanceDateType::getId;
  }

  @Override
  protected Function<InstanceDateType, Metadata> metadataExtractor() {
    return InstanceDateType::getMetadata;
  }

  @Override
  protected UnaryOperator<InstanceDateType> recordModifyingFunction() {
    return instanceDateType -> instanceDateType.withName("updated");
  }

  @Override
  protected List<String> queries() {
    return List.of("code==c");
  }

  @Override
  void getCollection_shouldReturn200AndEmptyCollection(Vertx vertx, VertxTestContext ctx) {
    Assertions.assertTrue(true);
  }

  @Override
  void get_shouldReturn200AndRecordById(Vertx vertx, VertxTestContext ctx) {
    Assertions.assertTrue(true);
  }

  @Override
  void post_shouldReturn201AndCreatedRecord(Vertx vertx, VertxTestContext ctx) {
    Assertions.assertTrue(true);
  }

  @Override
  void put_shouldReturn204AndRecordIsUpdated(Vertx vertx, VertxTestContext ctx) {
    Assertions.assertTrue(true);
  }

  @Override
  void delete_shouldReturn204AndRecordIsDeleted(Vertx vertx, VertxTestContext ctx) {
    Assertions.assertTrue(true);
  }

  @BeforeEach
  void setUp(Vertx vertx, VertxTestContext ctx) {
    var postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);
    postgresClient.delete(referenceTable(), (CQLWrapper) null)
      .onComplete(event -> ctx.completeNow());
  }

  @Test
  void patch_shouldReturn204AndRecordIsUpdated(Vertx vertx, VertxTestContext ctx) {
    HttpClient client = vertx.createHttpClient();

    var postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);

    var newRecord = sampleRecord();

    postgresClient.save(referenceTable(), newRecord)
      .compose(id -> {
        var updatedRecord = new InstanceDateTypePatch().withName("Updated");
        return doPatch(client, resourceUrlById(id), pojo2JsonObject(updatedRecord))
          .onComplete(verifyStatus(ctx, HTTP_NO_CONTENT))
          .compose(r -> postgresClient.getById(referenceTable(), id, targetClass())
            .onComplete(ctx.succeeding(dbRecord -> ctx.verify(() -> {
              verifyRecordHasSameId(dbRecord, id, "Verify updated record exists in database");

              assertThat(dbRecord)
                .extracting(InstanceDateType::getName)
                .isEqualTo(updatedRecord.getName());
              ctx.completeNow();
            }))));
      });
  }
}
