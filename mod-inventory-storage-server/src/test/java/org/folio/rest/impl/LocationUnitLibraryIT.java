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
import io.vertx.core.http.HttpClient;
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
import org.folio.rest.jaxrs.model.Loccamp;
import org.folio.rest.jaxrs.model.Locinst;
import org.folio.rest.jaxrs.model.Loclib;
import org.folio.rest.jaxrs.model.Loclibs;
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
class LocationUnitLibraryIT
  extends BaseReferenceDataIntegrationTest<Loclib, Loclibs> {

  private String campusId;

  @Override
  protected String referenceTable() {
    return LIBRARY_TABLE;
  }

  @Override
  protected String resourceUrl() {
    return "/location-units/libraries";
  }

  @Override
  protected Class<Loclib> targetClass() {
    return Loclib.class;
  }

  @Override
  protected Class<Loclibs> collectionClass() {
    return Loclibs.class;
  }

  @Override
  protected Loclib sampleRecord() {
    return new Loclib()
      .withName("test-library")
      .withCode("code")
      .withCampusId(campusId);
  }

  @Override
  protected Function<Loclibs, List<Loclib>> collectionRecordsExtractor() {
    return Loclibs::getLoclibs;
  }

  @Override
  protected List<Function<Loclib, Object>> recordFieldExtractors() {
    return List.of(Loclib::getName);
  }

  @Override
  protected Function<Loclib, String> idExtractor() {
    return Loclib::getId;
  }

  @Override
  protected Function<Loclib, Metadata> metadataExtractor() {
    return Loclib::getMetadata;
  }

  @Override
  protected UnaryOperator<Loclib> recordModifyingFunction() {
    return classificationType -> classificationType.withName("name-updated");
  }

  @Override
  protected List<String> queries() {
    return List.of("name==test-library", "code=code", "campusId==" + campusId);
  }

  @BeforeEach
  void beforeEach(Vertx vertx, VertxTestContext ctx) {
    var postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);
    var institution = new Locinst().withName("institution").withCode("ic");
    var campus = new Loccamp().withName("campus").withCode("cc");
    postgresClient.save(INSTITUTION_TABLE, institution)
      .compose(
        id -> postgresClient.save(CAMPUS_TABLE, campus.withInstitutionId(id)))
      .onFailure(ctx::failNow)
      .onSuccess(id -> {
        campusId = id;
        ctx.completeNow();
      });
  }

  @AfterEach
  void afterEach(Vertx vertx, VertxTestContext ctx) {
    var postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);
    postgresClient.delete(referenceTable(), (CQLWrapper) null)
      .compose(rows -> postgresClient.delete(CAMPUS_TABLE, (CQLWrapper) null))
      .compose(
        rows -> postgresClient.delete(INSTITUTION_TABLE, (CQLWrapper) null))
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

    var nonShadowLibrary1 = sampleRecord().withName("test-library1").withCode("code1");
    var nonShadowLibrary2 = sampleRecord().withName("test-library2").withCode("code2");
    var shadowLibrary = sampleRecord()
      .withIsShadow(true)
      .withName("test-shadow-library")
      .withCode("shadow");

    Future.all(
        postgresClient.save(referenceTable(), nonShadowLibrary1),
        postgresClient.save(referenceTable(), nonShadowLibrary2),
        postgresClient.save(referenceTable(), shadowLibrary)
      )
      .compose(s ->
        doGet(client, resourceUrl() + queryStringAndParam)
          .onComplete(verifyStatus(ctx, HTTP_OK))
          .andThen(ctx.succeeding(response -> ctx.verify(() ->
            verifyLibraryCollection(response, queryStringAndParam, total, codes)))))
      .onFailure(ctx::failNow)
      .onSuccess(event -> ctx.completeNow());
  }

  @Test
  void post_shouldReturn422_whenObjectIsDuplicate(Vertx vertx,
                                                  VertxTestContext ctx) {
    HttpClient client = vertx.createHttpClient();
    String libraryId = UUID.randomUUID().toString();
    var library = sampleRecord().withId(libraryId);

    // Create first library
    doPost(client, resourceUrl(), pojo2JsonObject(library))
      .onComplete(verifyStatus(ctx, HTTP_CREATED))
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {

        // Trying to create same library in second time
        doPost(client, resourceUrl(), pojo2JsonObject(library))
          .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
          .onComplete(ctx.succeeding(duplicateResponse -> ctx.verify(() -> {
            var actual = duplicateResponse.bodyAsClass(Errors.class);
            assertThat(actual.getErrors())
              .hasSize(1)
              .extracting(Error::getMessage)
              .containsExactly("id value already exists in table loclibrary: " + libraryId);
            ctx.completeNow();
          })));
      })));
  }

  @Test
  void post_shouldReturn422_whenNameIsDuplicate(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var library = sampleRecord();
    doPost(client, resourceUrl(), pojo2JsonObject(library))
      .onComplete(verifyStatus(ctx, HTTP_CREATED))
      .onComplete(ctx.succeeding(response1 -> ctx.verify(() -> {
        doPost(client, resourceUrl(), pojo2JsonObject(library))
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
    var library = sampleRecord().withCode(null);
    doPost(client, resourceUrl(), pojo2JsonObject(library))
      .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(response -> ctx.verify(ctx::completeNow)));
  }

  @Test
  void post_shouldReturn422_whenCampusIdIsNotExists(Vertx vertx,
                                                    VertxTestContext ctx) {
    HttpClient client = vertx.createHttpClient();
    var invalidCampusId = UUID.randomUUID().toString();
    var library = sampleRecord().withId(UUID.randomUUID().toString())
      .withCampusId(invalidCampusId);

    doPost(client, resourceUrl(), pojo2JsonObject(library))
      .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        var actual = response.bodyAsClass(Errors.class);
        assertThat(actual.getErrors())
          .hasSize(1)
          .extracting(Error::getMessage)
          .containsExactly("Cannot set loclibrary.campusid = "
            + invalidCampusId + " because it does not exist in loccampus.id.");
        ctx.completeNow();
      })));
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
    HttpClient client = vertx.createHttpClient();
    var invalidId = UUID.randomUUID().toString();
    var library = sampleRecord().withId(UUID.randomUUID().toString());

    doPut(client, resourceUrlById(invalidId), pojo2JsonObject(library))
      .onComplete(verifyStatus(ctx, HTTP_BAD_REQUEST))
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        var actual = response.bodyAsClass(Errors.class);
        assertThat(actual.getErrors())
          .hasSize(1)
          .extracting(Error::getMessage)
          .containsExactly("Illegal operation: Library ID cannot be changed");
        ctx.completeNow();
      })));
  }

  @Test
  void put_shouldReturn404_whenCampusIdIsNotExists(Vertx vertx,
                                                   VertxTestContext ctx) {
    HttpClient client = vertx.createHttpClient();
    var invalidId = UUID.randomUUID().toString();
    var library = sampleRecord().withId(UUID.randomUUID().toString())
      .withCampusId(invalidId);

    doPut(client, resourceUrlById(library.getId()), pojo2JsonObject(library))
      .onComplete(verifyStatus(ctx, HTTP_NOT_FOUND))
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        var actual = response.body().toString();
        assertThat(actual)
          .contains("Not found");
        ctx.completeNow();
      })));
  }

  private void verifyLibraryCollection(TestResponse response, String queryStringAndParam, 
                                        int total, List<String> codes) {
    var collectionUnits = response.bodyAsClass(Loclibs.class);
    assertThat(collectionUnits)
      .as("verify collection for query and param: " + queryStringAndParam)
      .isNotNull()
      .hasFieldOrPropertyWithValue("totalRecords", total)
      .extracting(Loclibs::getLoclibs).asInstanceOf(InstanceOfAssertFactories.COLLECTION)
      .hasSize(total);

    assertThat(collectionUnits.getLoclibs())
      .hasSize(total)
      .extracting(Loclib::getCode)
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
