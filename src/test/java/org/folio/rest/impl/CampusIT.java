package org.folio.rest.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.HttpStatus.HTTP_BAD_REQUEST;
import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.HttpStatus.HTTP_NOT_FOUND;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.folio.rest.impl.LocationUnitApi.CAMPUS_TABLE;
import static org.folio.rest.impl.LocationUnitApi.INSTITUTION_TABLE;
import static org.folio.utility.RestUtility.TENANT_ID;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Loccamp;
import org.folio.rest.jaxrs.model.Loccamps;
import org.folio.rest.jaxrs.model.Locinst;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
class CampusIT extends BaseReferenceDataIntegrationTest<Loccamp, Loccamps> {

  private String institutionId;

  @Override
  protected String referenceTable() {
    return CAMPUS_TABLE;
  }

  @Override
  protected String resourceUrl() {
    return "/location-units/campuses";
  }

  @Override
  protected Class<Loccamp> targetClass() {
    return Loccamp.class;
  }

  @Override
  protected Class<Loccamps> collectionClass() {
    return Loccamps.class;
  }

  @Override
  protected Loccamp sampleRecord() {
    return new Loccamp()
      .withName("test-campus")
      .withCode("code")
      .withInstitutionId(institutionId);
  }

  @Override
  protected Function<Loccamps, List<Loccamp>> collectionRecordsExtractor() {
    return Loccamps::getLoccamps;
  }

  @Override
  protected List<Function<Loccamp, Object>> recordFieldExtractors() {
    return List.of(Loccamp::getName);
  }

  @Override
  protected Function<Loccamp, String> idExtractor() {
    return Loccamp::getId;
  }

  @Override
  protected Function<Loccamp, Metadata> metadataExtractor() {
    return Loccamp::getMetadata;
  }

  @Override
  protected UnaryOperator<Loccamp> recordModifyingFunction() {
    return classificationType -> classificationType.withName("name-updated");
  }

  @Override
  protected List<String> queries() {
    return List.of("name==test-campus", "code=code", "institutionId==" + institutionId);
  }

  @BeforeEach
  void beforeEach(Vertx vertx, VertxTestContext ctx) {
    var postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);
    var institution = new Locinst().withName("institution").withCode("ic");
    postgresClient.save(INSTITUTION_TABLE, institution)
      .onFailure(ctx::failNow)
      .onSuccess(id -> {
        institutionId = id;
        ctx.completeNow();
      });
  }

  @AfterEach
  void afterEach(Vertx vertx, VertxTestContext ctx) {
    var postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);
    postgresClient.delete(referenceTable(), (CQLWrapper) null)
      .compose(rows -> postgresClient.delete(INSTITUTION_TABLE, (CQLWrapper) null))
      .onFailure(ctx::failNow)
      .onComplete(event -> ctx.completeNow());
  }

  @Test
  void post_shouldReturn422_whenObjectIsDuplicate(Vertx vertx,
                                                  VertxTestContext ctx) {
    HttpClient client = vertx.createHttpClient();
    String campusId = UUID.randomUUID().toString();
    var campus = sampleRecord().withId(campusId);

    // Create first campus
    doPost(client, resourceUrl(), pojo2JsonObject(campus))
      .onComplete(verifyStatus(ctx, HTTP_CREATED))
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {

        // Trying to create same campus for the second time
        doPost(client, resourceUrl(), pojo2JsonObject(campus))
          .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
          .onComplete(ctx.succeeding(duplicateResponse -> ctx.verify(() -> {
            var actual = duplicateResponse.bodyAsClass(Errors.class);
            assertThat(actual.getErrors())
              .hasSize(1)
              .extracting(Error::getMessage)
              .containsExactly("id value already exists in table loccampus: " + campusId);
            ctx.completeNow();
          })));
      })));
  }

  @Test
  void put_shouldReturn422_whenIdDoesNotMatchWithPayload(Vertx vertx,
                                                         VertxTestContext ctx) {
    HttpClient client = vertx.createHttpClient();
    var invalidId = UUID.randomUUID().toString();
    var campus = sampleRecord().withId(UUID.randomUUID().toString());

    doPut(client, resourceUrlById(invalidId), pojo2JsonObject(campus))
      .onComplete(verifyStatus(ctx, HTTP_BAD_REQUEST))
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        var actual = response.bodyAsClass(Errors.class);
        assertThat(actual.getErrors())
          .hasSize(1)
          .extracting(Error::getMessage)
          .containsExactly("Illegal operation: Campus ID cannot be changed");
        ctx.completeNow();
      })));
  }

  @Test
  void post_shouldReturn422_whenInstitutionDoesNotExist(Vertx vertx,
                                                        VertxTestContext ctx) {
    HttpClient client = vertx.createHttpClient();
    var invalidInstitutionId = UUID.randomUUID().toString();
    var campus = sampleRecord().withId(UUID.randomUUID().toString())
      .withInstitutionId(invalidInstitutionId);

    doPost(client, resourceUrl(), pojo2JsonObject(campus))
      .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        var actual = response.bodyAsClass(Errors.class);
        assertThat(actual.getErrors())
          .hasSize(1)
          .extracting(Error::getMessage)
          .containsExactly("Cannot set loccampus.institutionid = "
            + invalidInstitutionId + " because it does not exist in locinstitution.id.");
        ctx.completeNow();
      })));
  }

  @Test
  void put_shouldReturn404_whenInstitutionDoesNotExist(Vertx vertx,
                                                       VertxTestContext ctx) {
    HttpClient client = vertx.createHttpClient();
    var invalidInstitutionId = UUID.randomUUID().toString();
    var campus = sampleRecord().withId(UUID.randomUUID().toString())
      .withInstitutionId(invalidInstitutionId);

    doPut(client, resourceUrlById(campus.getId()), pojo2JsonObject(campus))
      .onComplete(verifyStatus(ctx, HTTP_NOT_FOUND))
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        var actual = response.body().toString();
        assertThat(actual)
          .contains("Not found");
        ctx.completeNow();
      })));
  }
}
