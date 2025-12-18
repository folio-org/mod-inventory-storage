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
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.LocationCampus;
import org.folio.rest.jaxrs.model.LocationCampuses;
import org.folio.rest.jaxrs.model.LocationInstitution;
import org.folio.rest.jaxrs.model.LocationLibrary;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@ExtendWith(VertxExtension.class)
class LocationUnitCampusIT extends BaseReferenceDataIntegrationTest<LocationCampus, LocationCampuses> {

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
  protected Class<LocationCampus> targetClass() {
    return LocationCampus.class;
  }

  @Override
  protected Class<LocationCampuses> collectionClass() {
    return LocationCampuses.class;
  }

  @Override
  protected LocationCampus sampleRecord() {
    return new LocationCampus().withName("test-campus").withCode("code").withInstitutionId(institutionId);
  }

  @Override
  protected Function<LocationCampuses, List<LocationCampus>> collectionRecordsExtractor() {
    return LocationCampuses::getLoccamps;
  }

  @Override
  protected List<Function<LocationCampus, Object>> recordFieldExtractors() {
    return List.of(LocationCampus::getName);
  }

  @Override
  protected Function<LocationCampus, String> idExtractor() {
    return LocationCampus::getId;
  }

  @Override
  protected Function<LocationCampus, Metadata> metadataExtractor() {
    return LocationCampus::getMetadata;
  }

  @Override
  protected UnaryOperator<LocationCampus> recordModifyingFunction() {
    return loccamp -> loccamp.withName("name-updated");
  }

  @Override
  protected List<String> queries() {
    return List.of("name==test-campus", "code=code", "institutionId==" + institutionId);
  }

  @BeforeEach
  void beforeEach(Vertx vertx, VertxTestContext ctx) {
    var postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);
    var institution = new LocationInstitution().withName("institution").withCode("ic");
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

  @MethodSource("queryStringAndParam")
  @ParameterizedTest
  void getCollection_shouldReturnRecordCollectionBasedOnQueryStringAndParam(String queryStringAndParam, int total,
                                                                            List<String> codes, Vertx vertx,
                                                                            VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);

    var nonShadowCampus1 = sampleRecord().withName("test-campus1").withCode("code1");
    var nonShadowCampus2 = sampleRecord().withName("test-campus2").withCode("code2");
    var shadowCampus = sampleRecord()
      .withIsShadow(true)
      .withName("test-shadow-campus")
      .withCode("shadow");

    Future.all(
        postgresClient.save(referenceTable(), nonShadowCampus1),
        postgresClient.save(referenceTable(), nonShadowCampus2),
        postgresClient.save(referenceTable(), shadowCampus)
      )
      .compose(s ->
        doGet(client, resourceUrl() + queryStringAndParam)
          .onComplete(verifyStatus(ctx, HTTP_OK))
          .andThen(ctx.succeeding(response -> ctx.verify(() ->
            verifyCampusCollection(response, queryStringAndParam, total, codes)))))
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
    var campus = new LocationCampus().withName("campus").withCode("cc");
    var library = new LocationLibrary().withName("library").withCode("lc");

    postgresClient.save(CAMPUS_TABLE, campus.withInstitutionId(institutionId))
      .compose(campusId -> postgresClient.save(LIBRARY_TABLE, library.withCampusId(campusId))
        .compose(libraryId -> doDelete(client, resourceUrl() + "/" + campusId)))
      .onComplete(verifyStatus(ctx, HTTP_BAD_REQUEST))
      .onComplete(ctx.succeeding(response -> ctx.completeNow()));
  }

  private void verifyCampusCollection(TestResponse response, String queryStringAndParam,
                                      int total, List<String> codes) {
    var collectionUnits = response.bodyAsClass(LocationCampuses.class);
    assertThat(collectionUnits)
      .as("verify collection for query param: " + queryStringAndParam)
      .isNotNull()
      .hasFieldOrPropertyWithValue("totalRecords", total)
      .extracting(LocationCampuses::getLoccamps).asInstanceOf(InstanceOfAssertFactories.COLLECTION)
      .hasSize(total);

    assertThat(collectionUnits.getLoccamps())
      .hasSize(total)
      .extracting(LocationCampus::getCode)
      .containsAll(codes);
  }

  private static Stream<Arguments> queryStringAndParam() {
    return Stream.of(
      arguments("", 2, List.of("code1", "code2")),
      arguments("?query=code=code1", 1, List.of("code1")),
      arguments("?includeShadow=false", 2, List.of("code1", "code2")),
      arguments("?includeShadow=true", 3, List.of("code1", "code2", "shadow")),
      arguments("?query=code=shadow", 0, List.of()),
      arguments("?includeShadow=true&query=code=shadow", 1, List.of("shadow")),
      arguments("?includeShadow=true&query=code1=code1", 0, List.of())
    );
  }
}
