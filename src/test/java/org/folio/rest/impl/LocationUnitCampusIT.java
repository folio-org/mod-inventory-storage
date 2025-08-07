package org.folio.rest.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.HttpStatus.HTTP_BAD_REQUEST;
import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.HttpStatus.HTTP_NOT_FOUND;
import static org.folio.HttpStatus.HTTP_OK;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.folio.rest.impl.LocationUnitApi.CAMPUS_TABLE;
import static org.folio.services.locationunit.InstitutionService.INSTITUTION_TABLE;
import static org.folio.services.locationunit.LibraryService.LIBRARY_TABLE;
import static org.folio.utility.RestUtility.TENANT_ID;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Loccamp;
import org.folio.rest.jaxrs.model.Loccamps;
import org.folio.rest.jaxrs.model.Locinst;
import org.folio.rest.jaxrs.model.Loclib;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
class LocationUnitCampusIT extends BaseReferenceDataIntegrationTest<Loccamp, Loccamps> {

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
    return new Loccamp().withName("test-campus").withCode("code").withInstitutionId(institutionId);
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
    return loccamp -> loccamp.withName("name-updated");
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

    postgresClient.delete(LIBRARY_TABLE, (CQLWrapper) null)
      .compose(rows -> postgresClient.delete(CAMPUS_TABLE, (CQLWrapper) null))
      .compose(rows -> postgresClient.delete(INSTITUTION_TABLE, (CQLWrapper) null))
      .onFailure(ctx::failNow)
      .onComplete(event -> ctx.completeNow());
  }

  @Test
  void getCollection_shouldReturnRecordCollectionBasedOnIncludeShadowQueryParam(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);

    var nonShadowCampus = sampleRecord();
    var shadowCampus = sampleRecord()
      .withIsShadow(true)
      .withName("test-shadow-campus")
      .withCode("shadow");
    var campusByQueryParam = Map.of(false, nonShadowCampus, true, shadowCampus);

    Future.all(
        postgresClient.save(referenceTable(), nonShadowCampus),
        postgresClient.save(referenceTable(), shadowCampus)
      )
      .compose(s -> {
        List<Future<TestResponse>> futures = new ArrayList<>();
        for (boolean param : campusByQueryParam.keySet()) {
          var responseFuture = doGet(client, resourceUrl() + "?includeShadow=" + param)
            .onComplete(verifyStatus(ctx, HTTP_OK))
            .andThen(ctx.succeeding(response -> ctx.verify(() -> {
              var collectionUnits = response.bodyAsClass(Loccamps.class);
              assertThat(collectionUnits)
                .as("verify collection for query param and value: includeShadow=" + param)
                .isNotNull()
                .hasFieldOrPropertyWithValue("totalRecords", 1)
                .extracting(Loccamps::getLoccamps).asInstanceOf(InstanceOfAssertFactories.COLLECTION)
                .hasSize(1);

              var collectionRecord = collectionUnits.getLoccamps().getFirst();
              verifyRecordFields(collectionRecord, campusByQueryParam.get(param),
                List.of(Loccamp::getName, Loccamp::getIsShadow),
                "verify collection's record for query param: ?includeShadow=" + param);
            })));
          futures.add(responseFuture);
        }
        return Future.all(futures);
      })
      .onFailure(ctx::failNow)
      .onSuccess(event -> ctx.completeNow());
  }

  @Test
  void post_shouldReturn422_whenNameIsDuplicate(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var campus = sampleRecord();
    doPost(client, resourceUrl(), pojo2JsonObject(campus))
      .onComplete(verifyStatus(ctx, HTTP_CREATED))
      .onComplete(ctx.succeeding(response1 -> ctx.verify(() -> {
        doPost(client, resourceUrl(), pojo2JsonObject(campus))
          .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
          .onComplete(ctx.succeeding(response2 -> ctx.verify(() -> {
            var actual = response2.bodyAsClass(Errors.class);
            assertThat(actual.getErrors()).isNotEmpty();
            ctx.completeNow();
          })));
      })));
  }

  @Test
  void post_shouldReturn422_whenInstitutionIdIsNull(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var campus = sampleRecord().withInstitutionId(null);
    doPost(client, resourceUrl(), pojo2JsonObject(campus))
      .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(response -> ctx.verify(ctx::completeNow)));
  }

  @Test
  void post_shouldReturn400_whenIdIsDuplicate(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var campus = sampleRecord();
    var campusId = UUID.randomUUID().toString();
    campus.setId(campusId);
    doPost(client, resourceUrl(), pojo2JsonObject(campus))
      .onComplete(verifyStatus(ctx, HTTP_CREATED))
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        campus.setId(campusId);
        doPost(client, resourceUrl(), pojo2JsonObject(campus))
          .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
          .onComplete(ctx.succeeding(response2 -> ctx.verify(() -> {
            var actual = response2.bodyAsClass(Errors.class);
            assertThat(actual.getErrors()).isNotEmpty();
            ctx.completeNow();
          })));
      })));
  }

  @Test
  void post_shouldReturn422_whenCodeIsNull(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var campus = sampleRecord().withCode(null);
    doPost(client, resourceUrl(), pojo2JsonObject(campus))
      .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(response -> ctx.verify(ctx::completeNow)));
  }

  @Test
  void get_shouldReturn400_whenBadQuery(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    doGet(client, resourceUrl() + "?query=invalidCQL")
      .onComplete(verifyStatus(ctx, HTTP_BAD_REQUEST))
      .onComplete(ctx.succeeding(response -> ctx.verify(ctx::completeNow)));
  }

  @Test
  void get_shouldReturn404_whenInvalidId(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    doGet(client, resourceUrl() + "/" + UUID.randomUUID())
      .onComplete(verifyStatus(ctx, HTTP_NOT_FOUND))
      .onComplete(ctx.succeeding(response -> ctx.verify(ctx::completeNow)));
  }

  @Test
  void put_shouldReturn422_whenIdIsNotMatchWithPayload(Vertx vertx,
                                                       VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var invalidId = UUID.randomUUID().toString();
    var campus = sampleRecord().withId(UUID.randomUUID().toString());

    doPut(client, resourceUrlById(invalidId),
      pojo2JsonObject(campus)).onComplete(verifyStatus(ctx, HTTP_BAD_REQUEST))
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        var actual = response.bodyAsClass(Errors.class);
        var message = "Illegal operation: Campus ID cannot be changed";

        assertThat(actual.getErrors()).hasSize(1).extracting(Error::getMessage)
          .containsExactly(message);

        ctx.completeNow();
      })));
  }

  @Test
  void delete_shouldReturn400_whenCampusHasFks(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);
    var campus = new Loccamp().withName("campus").withCode("cc");
    var library = new Loclib().withName("library").withCode("lc");

    postgresClient.save(CAMPUS_TABLE, campus.withInstitutionId(institutionId))
      .compose(campusId -> postgresClient.save(LIBRARY_TABLE, library.withCampusId(campusId))
        .compose(libraryId -> doDelete(client, resourceUrl() + "/" + campusId)))
      .onComplete(verifyStatus(ctx, HTTP_BAD_REQUEST))
      .onComplete(ctx.succeeding(response -> ctx.completeNow()));
  }
}
