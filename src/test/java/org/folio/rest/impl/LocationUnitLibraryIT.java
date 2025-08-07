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
import io.vertx.core.http.HttpClient;
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

  @Test
  void getCollection_shouldReturnRecordCollectionBasedOnIncludeShadowQueryParam(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);

    var nonShadowLibrary = sampleRecord();
    var shadowLibrary = sampleRecord()
      .withIsShadow(true)
      .withName("test-shadow-library")
      .withCode("shadow");
    var libraryByQueryParam = Map.of(false, nonShadowLibrary, true, shadowLibrary);

    Future.all(
        postgresClient.save(referenceTable(), nonShadowLibrary),
        postgresClient.save(referenceTable(), shadowLibrary)
      )
      .compose(s -> {
        List<Future<TestResponse>> futures = new ArrayList<>();
        for (boolean param : libraryByQueryParam.keySet()) {
          var responseFuture = doGet(client, resourceUrl() + "?includeShadow=" + param)
            .onComplete(verifyStatus(ctx, HTTP_OK))
            .andThen(ctx.succeeding(response -> ctx.verify(() -> {
              var collectionUnits = response.bodyAsClass(Loclibs.class);
              assertThat(collectionUnits)
                .as("verify collection for query param and value: includeShadow=" + param)
                .isNotNull()
                .hasFieldOrPropertyWithValue("totalRecords", 1)
                .extracting(Loclibs::getLoclibs).asInstanceOf(InstanceOfAssertFactories.COLLECTION)
                .hasSize(1);

              var collectionRecord = collectionUnits.getLoclibs().getFirst();
              verifyRecordFields(collectionRecord, libraryByQueryParam.get(param),
                List.of(Loclib::getName, Loclib::getIsShadow),
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
}
