package org.folio.rest.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.HttpStatus.HTTP_BAD_REQUEST;
import static org.folio.HttpStatus.HTTP_INTERNAL_SERVER_ERROR;
import static org.folio.HttpStatus.HTTP_NOT_FOUND;
import static org.folio.HttpStatus.HTTP_NO_CONTENT;
import static org.folio.HttpStatus.HTTP_OK;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.folio.rest.impl.LocationUnitApi.CAMPUS_TABLE;
import static org.folio.services.locationunit.InstitutionService.INSTITUTION_TABLE;
import static org.folio.services.locationunit.LibraryService.LIBRARY_TABLE;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Loccamp;
import org.folio.rest.jaxrs.model.Locinst;
import org.folio.rest.jaxrs.model.Locinsts;
import org.folio.rest.jaxrs.model.Loclib;
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
class LocationUnitInstitutionIT
  extends BaseReferenceDataIntegrationTest<Locinst, Locinsts> {

  @Override
  protected String referenceTable() {
    return INSTITUTION_TABLE;
  }

  @Override
  protected String resourceUrl() {
    return "/location-units/institutions";
  }

  @Override
  protected Class<Locinst> targetClass() {
    return Locinst.class;
  }

  @Override
  protected Class<Locinsts> collectionClass() {
    return Locinsts.class;
  }

  @Override
  protected Locinst sampleRecord() {
    return new Locinst().withName("test-institution").withCode("code");
  }

  @Override
  protected Function<Locinsts, List<Locinst>> collectionRecordsExtractor() {
    return Locinsts::getLocinsts;
  }

  @Override
  protected List<Function<Locinst, Object>> recordFieldExtractors() {
    return List.of(Locinst::getName);
  }

  @Override
  protected Function<Locinst, String> idExtractor() {
    return Locinst::getId;
  }

  @Override
  protected Function<Locinst, Metadata> metadataExtractor() {
    return Locinst::getMetadata;
  }

  @Override
  protected UnaryOperator<Locinst> recordModifyingFunction() {
    return classificationType -> classificationType.withName("name-updated");
  }

  @Override
  protected List<String> queries() {
    return List.of("name==test-institution", "code=code");
  }

  @BeforeEach
  void beforeEach(Vertx vertx, VertxTestContext ctx) {
    deleteAllLocationData(vertx, ctx);
  }

  @AfterEach
  void afterEach(Vertx vertx, VertxTestContext ctx) {
    deleteAllLocationData(vertx, ctx);
  }

  private static void deleteAllLocationData(Vertx vertx, VertxTestContext ctx) {
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

    var nonShadowInstitution1 = new Locinst().withName("test-institution1").withCode("code1");
    var nonShadowInstitution2 = new Locinst().withName("test-institution2").withCode("code2");
    var shadowInstitution = sampleRecord()
      .withIsShadow(true)
      .withName("test-shadow-institution")
      .withCode("shadow");

    Future.all(
        postgresClient.save(referenceTable(), nonShadowInstitution1),
        postgresClient.save(referenceTable(), nonShadowInstitution2),
        postgresClient.save(referenceTable(), shadowInstitution)
      )
      .compose(s ->
        doGet(client, resourceUrl() + queryStringAndParam)
          .onComplete(verifyStatus(ctx, HTTP_OK))
          .andThen(ctx.succeeding(response -> ctx.verify(() ->
            verifyInstitutionCollection(response, queryStringAndParam, total, codes)))))
      .onFailure(ctx::failNow)
      .onSuccess(event -> ctx.completeNow());
  }

  @Test
    void post_shouldReturn500_whenObjectIsDuplicate(Vertx vertx,
                                                  VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var id = UUID.randomUUID().toString();
    var institution = sampleRecord().withId(id);
    var message =
      String.format("id value already exists in table locinstitution: %s", id);
    var body = pojo2JsonObject(institution);
    var requestOne = doPost(client, resourceUrl(), body);
    var requestTwo = doPost(client, resourceUrl(), body);

    Future.all(requestOne, requestTwo).map(CompositeFuture::list).map(
        results -> results.stream().filter(Objects::nonNull)
          .peek(System.out::println).map(v -> (TestResponse) v)
          .filter(v -> v.status() == HTTP_UNPROCESSABLE_ENTITY.toInt()).findAny()
          .orElse(null))
      .onComplete(ctx.succeeding(duplicateResponse -> ctx.verify(() -> {
        var actual = duplicateResponse.bodyAsClass(Errors.class);

        assertThat(actual.getErrors()).hasSize(1).extracting(Error::getMessage)
          .containsExactly(message);

        ctx.completeNow();
      })));
  }

  @Test
  void post_shouldReturn422_whenCodeIsBlank(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var id = UUID.randomUUID().toString();
    var institution = sampleRecord().withId(id).withCode(null);

    doPost(client, resourceUrl(), pojo2JsonObject(institution)).onComplete(
        verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(duplicateResponse -> ctx.verify(() -> {
        var actual = duplicateResponse.bodyAsClass(Errors.class);
        var message = "must not be null";

        assertThat(actual.getErrors()).hasSize(1).extracting(Error::getMessage)
          .containsExactly(message);

        ctx.completeNow();
      })));
  }

  @Test
  void post_shouldReturn422_whenNameIsBlank(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var id = UUID.randomUUID().toString();
    var institution = sampleRecord().withId(id).withName(null);

    doPost(client, resourceUrl(), pojo2JsonObject(institution)).onComplete(
        verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(duplicateResponse -> ctx.verify(() -> {
        var actual = duplicateResponse.bodyAsClass(Errors.class);
        var message = "must not be null";

        assertThat(actual.getErrors()).hasSize(1).extracting(Error::getMessage)
          .containsExactly(message);

        ctx.completeNow();
      })));
  }

  @Test
  void put_shouldReturn422_whenIdIsNotMatchWithPayload(Vertx vertx,
                                                       VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var invalidId = UUID.randomUUID().toString();
    var institution = sampleRecord().withId(UUID.randomUUID().toString());

    doPut(client, resourceUrlById(invalidId),
      pojo2JsonObject(institution)).onComplete(verifyStatus(ctx, HTTP_BAD_REQUEST))
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        var actual = response.bodyAsClass(Errors.class);
        var message = "Illegal operation: Institution ID cannot be changed";

        assertThat(actual.getErrors()).hasSize(1).extracting(Error::getMessage)
          .containsExactly(message);

        ctx.completeNow();
      })));
  }

  @Test
  void put_shouldReturn422_whenIdIsNullWithPayload(Vertx vertx,
                                                   VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var institution = sampleRecord().withId(UUID.randomUUID().toString());
    var body = pojo2JsonObject(institution);

    doPut(client, resourceUrlById(null), body).onComplete(
        verifyStatus(ctx, HTTP_BAD_REQUEST))
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        var actual = response.bodyAsClass(Errors.class);
        var message = "Illegal operation: Institution ID cannot be changed";

        assertThat(actual.getErrors()).hasSize(1).extracting(Error::getMessage)
          .containsExactly(message);

        ctx.completeNow();
      })));
  }

  @Test
  void put_shouldReturn422_whenCodeIsBlank(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var id = UUID.randomUUID().toString();
    var institution = sampleRecord().withId(id).withCode(null);

    doPut(client, resourceUrlById(id), pojo2JsonObject(institution)).onComplete(
        verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(duplicateResponse -> ctx.verify(() -> {
        var actual = duplicateResponse.bodyAsClass(Errors.class);
        var message = "must not be null";

        assertThat(actual.getErrors()).hasSize(1).extracting(Error::getMessage)
          .containsExactly(message);

        ctx.completeNow();
      })));
  }

  @Test
  void put_shouldReturn422_whenNameIsBlank(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var id = UUID.randomUUID().toString();
    var institution = sampleRecord().withId(id).withName(null);

    doPut(client, resourceUrlById(id), pojo2JsonObject(institution)).onComplete(
        verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(duplicateResponse -> ctx.verify(() -> {
        var actual = duplicateResponse.bodyAsClass(Errors.class);
        var message = "must not be null";

        assertThat(actual.getErrors()).hasSize(1).extracting(Error::getMessage)
          .containsExactly(message);

        ctx.completeNow();
      })));
  }

  @Test
  void deleteAll_shouldReturn204(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();

    doDelete(client, resourceUrl()).onComplete(verifyStatus(ctx, HTTP_NO_CONTENT))
      .onComplete(ctx.succeeding(response -> ctx.verify(ctx::completeNow)));
  }

  @Test
  void deleteAll_shouldReturn500_whenInstitutionHasFks(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);
    var institution = new Locinst().withName("institution").withCode("ic");
    var campus = new Loccamp().withName("campus").withCode("cc");
    var library = new Loclib().withName("library").withCode("lc");

    postgresClient.save(INSTITUTION_TABLE, institution)
      .compose(id -> postgresClient.save(CAMPUS_TABLE, campus.withInstitutionId(id)))
      .compose(id -> postgresClient.save(LIBRARY_TABLE, library.withCampusId(id)))
      .onFailure(ctx::failNow)
      .onSuccess(result ->
        doDelete(client, resourceUrl()).onComplete(verifyStatus(ctx, HTTP_INTERNAL_SERVER_ERROR))
          .onComplete(ctx.succeeding(response -> ctx.verify(ctx::completeNow)))
      );
  }

  @Test
  void delete_shouldReturn400_whenInstitutionHasFks(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);
    var institution = new Locinst().withName("institution").withCode("ic");
    var campus = new Loccamp().withName("campus").withCode("cc");

    postgresClient.save(INSTITUTION_TABLE, institution)
      .compose(institutionId -> postgresClient.save(CAMPUS_TABLE, campus.withInstitutionId(institutionId))
        .compose(campusId -> doDelete(client, resourceUrl() + "/" + institutionId)))
      .onComplete(verifyStatus(ctx, HTTP_BAD_REQUEST))
      .onComplete(ctx.succeeding(response -> ctx.completeNow()));
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

  private void verifyInstitutionCollection(TestResponse response, String queryStringAndParam,
                                           int total, List<String> codes) {
    var collectionUnits = response.bodyAsClass(Locinsts.class);
    assertThat(collectionUnits)
      .as("verify collection for query and param: " + queryStringAndParam)
      .isNotNull()
      .hasFieldOrPropertyWithValue("totalRecords", total)
      .extracting(Locinsts::getLocinsts).asInstanceOf(InstanceOfAssertFactories.COLLECTION)
      .hasSize(total);

    assertThat(collectionUnits.getLocinsts())
      .hasSize(total)
      .extracting(Locinst::getCode)
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
