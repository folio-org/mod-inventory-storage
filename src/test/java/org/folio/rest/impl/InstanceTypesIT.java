package org.folio.rest.impl;

import static org.folio.HttpStatus.HTTP_BAD_REQUEST;
import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.rest.impl.InstanceTypeApi.INSTANCE_TYPE_TABLE;
import static org.folio.utility.RestUtility.TENANT_ID;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.assertj.core.api.Assertions;
import org.folio.rest.api.entities.Instance;
import org.folio.rest.jaxrs.model.InstanceType;
import org.folio.rest.jaxrs.model.InstanceTypes;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.persist.PostgresClient;
import org.junit.jupiter.api.Test;

class InstanceTypesIT extends BaseReferenceDataIntegrationTest<InstanceType, InstanceTypes> {

  @Override
  protected String referenceTable() {
    return INSTANCE_TYPE_TABLE;
  }

  @Override
  protected String resourceUrl() {
    return "/instance-types";
  }

  @Override
  protected Class<InstanceType> targetClass() {
    return InstanceType.class;
  }

  @Override
  protected Class<InstanceTypes> collectionClass() {
    return InstanceTypes.class;
  }

  @Override
  protected InstanceType sampleRecord() {
    return new InstanceType().withName("test-type").withCode("tp-code").withSource("test-source");
  }

  @Override
  protected Function<InstanceTypes, List<InstanceType>> collectionRecordsExtractor() {
    return InstanceTypes::getInstanceTypes;
  }

  @Override
  protected List<Function<InstanceType, Object>> recordFieldExtractors() {
    return List.of(InstanceType::getName, InstanceType::getSource);
  }

  @Override
  protected Function<InstanceType, String> idExtractor() {
    return InstanceType::getId;
  }

  @Override
  protected Function<InstanceType, Metadata> metadataExtractor() {
    return InstanceType::getMetadata;
  }

  @Override
  protected UnaryOperator<InstanceType> recordModifyingFunction() {
    return instanceType -> instanceType.withName("name-updated").withSource("source-updated");
  }

  @Override
  protected List<String> queries() {
    return List.of("name==test-type", "source=test-source");
  }

  @Test
  void delete_cannotDeleteRecordThatAssociatedWithInstance(Vertx vertx, VertxTestContext ctx) {
    HttpClient client = vertx.createHttpClient();

    var postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);

    var newRecord = sampleRecord();

    postgresClient.save(referenceTable(), newRecord)
      .compose(id -> doPost(client, "/instance-storage/instances",
        new Instance("test-instance", "folio", id).getJson())
        .onComplete(verifyStatus(ctx, HTTP_CREATED))
        .compose(v -> doDelete(client, resourceUrlById(id))
          .onComplete(verifyStatus(ctx, HTTP_BAD_REQUEST))
          .compose(response -> postgresClient.getById(referenceTable(), id, targetClass()))
          .onComplete(ctx.succeeding(dbRecord -> ctx.verify(() -> {
            Assertions.assertThat(dbRecord)
              .as("Verify record still exists in database")
              .isNotNull();
            ctx.completeNow();
          })))));
  }
}
