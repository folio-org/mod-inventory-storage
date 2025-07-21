package org.folio.rest.impl;

import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.folio.rest.impl.HoldingsTypeApi.HOLDINGS_TYPE_TABLE;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.folio.rest.jaxrs.model.HoldingsType;
import org.folio.rest.jaxrs.model.HoldingsTypes;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.persist.PostgresClient;
import org.junit.jupiter.api.Test;

class HoldingsTypesIT extends BaseReferenceDataIntegrationTest<HoldingsType, HoldingsTypes> {

  @Override
  protected String referenceTable() {
    return HOLDINGS_TYPE_TABLE;
  }

  @Override
  protected String resourceUrl() {
    return "/holdings-types";
  }

  @Override
  protected Class<HoldingsType> targetClass() {
    return HoldingsType.class;
  }

  @Override
  protected Class<HoldingsTypes> collectionClass() {
    return HoldingsTypes.class;
  }

  @Override
  protected HoldingsType sampleRecord() {
    return new HoldingsType().withName("test-type").withSource("test-source");
  }

  @Override
  protected Function<HoldingsTypes, List<HoldingsType>> collectionRecordsExtractor() {
    return HoldingsTypes::getHoldingsTypes;
  }

  @Override
  protected List<Function<HoldingsType, Object>> recordFieldExtractors() {
    return List.of(HoldingsType::getName, HoldingsType::getSource);
  }

  @Override
  protected Function<HoldingsType, String> idExtractor() {
    return HoldingsType::getId;
  }

  @Override
  protected Function<HoldingsType, Metadata> metadataExtractor() {
    return HoldingsType::getMetadata;
  }

  @Override
  protected UnaryOperator<HoldingsType> recordModifyingFunction() {
    return holdingsType -> holdingsType.withName("name-updated").withSource("source-updated");
  }

  @Override
  protected List<String> queries() {
    return List.of("name==test-type", "source=test-source");
  }

  @Test
  void post_cannotCreateRecordWithDuplicateName(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);

    var newRecord = sampleRecord();

    postgresClient.save(referenceTable(), newRecord)
      .compose(id -> {
        var updatedRecord = sampleRecord().withName(newRecord.getName().toUpperCase());
        return doPost(client, resourceUrl(), pojo2JsonObject(updatedRecord))
          .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
          .onComplete(event -> ctx.verify(() -> {
            assertNameDuplicateError(event.result());
            ctx.completeNow();
          }));
      });
  }

  @Test
  void put_cannotUpdateRecordWithDuplicateName(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);

    var newRecord1 = sampleRecord();
    var newRecord2 = sampleRecord().withName(newRecord1.getName() + "2");

    postgresClient.save(referenceTable(), newRecord1)
      .compose(id1 -> postgresClient.save(referenceTable(), newRecord2))
      .compose(id2 -> {
        var updatedRecord = sampleRecord().withName(newRecord1.getName().toUpperCase());
        return doPut(client, resourceUrlById(id2), pojo2JsonObject(updatedRecord))
          .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
          .onComplete(event -> ctx.verify(() -> {
            assertNameDuplicateError(event.result());
            ctx.completeNow();
          }));
      });
  }

  private void assertNameDuplicateError(TestResponse response) {
    var errors = response.jsonBody().getJsonArray("errors");
    assertThat(errors.size(), is(1));

    var error = errors.getJsonObject(0);
    assertThat(error.getString("code"), is("name.duplicate"));
    assertThat(error.getString("message"), is("Cannot create/update entity; name is not unique"));

    var errorParameters = error.getJsonArray("parameters");
    assertThat(errorParameters.size(), is(1));

    var parameter = errorParameters.getJsonObject(0);
    assertThat(parameter.getString("key"), is("fieldLabel"));
    assertThat(parameter.getString("value"), is("name"));
  }
}
