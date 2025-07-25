package org.folio.rest.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.HttpStatus.HTTP_OK;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.folio.rest.impl.LocationUnitApi.CAMPUS_TABLE;
import static org.folio.services.location.LocationService.LOCATION_TABLE;
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
import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.jaxrs.model.Locations;
import org.folio.rest.jaxrs.model.Loccamp;
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
class LocationIT extends BaseReferenceDataIntegrationTest<Location, Locations> {

  private String institutionId;
  private String campusId;
  private String libraryId;
  private UUID primaryServicePointId;

  @Override
  protected String referenceTable() {
    return LOCATION_TABLE;
  }

  @Override
  protected String resourceUrl() {
    return "/locations";
  }

  @Override
  protected Class<Location> targetClass() {
    return Location.class;
  }

  @Override
  protected Class<Locations> collectionClass() {
    return Locations.class;
  }

  @Override
  protected Location sampleRecord() {
    return new Location()
      .withName("test-location")
      .withCode("code")
      .withCampusId(campusId)
      .withLibraryId(libraryId)
      .withInstitutionId(institutionId)
      .withPrimaryServicePoint(primaryServicePointId)
      .withServicePointIds(List.of(primaryServicePointId));
  }

  @Override
  protected Function<Locations, List<Location>> collectionRecordsExtractor() {
    return Locations::getLocations;
  }

  @Override
  protected List<Function<Location, Object>> recordFieldExtractors() {
    return List.of(Location::getName);
  }

  @Override
  protected Function<Location, String> idExtractor() {
    return Location::getId;
  }

  @Override
  protected Function<Location, Metadata> metadataExtractor() {
    return Location::getMetadata;
  }

  @Override
  protected UnaryOperator<Location> recordModifyingFunction() {
    return classificationType -> classificationType.withName("name-updated");
  }

  @Override
  protected List<String> queries() {
    return List.of("name==test-location", "code=code", "campusId==" + campusId, "libraryId==" + libraryId,
      "primaryServicePoint==" + primaryServicePointId);
  }

  @BeforeEach
  void beforeEach(Vertx vertx, VertxTestContext ctx) {
    var postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);
    primaryServicePointId = UUID.randomUUID();
    var institution = new Locinst().withName("institution").withCode("ic");
    var campus = new Loccamp().withName("campus").withCode("cc");
    var library = new Loclib().withName("library").withCode("lc");
    postgresClient.save(INSTITUTION_TABLE, institution)
      .compose(id -> {
        institutionId = id;
        return postgresClient.save(CAMPUS_TABLE, campus.withInstitutionId(id));
      })
      .compose(id -> {
        campusId = id;
        return postgresClient.save(LIBRARY_TABLE, library.withCampusId(id));
      })
      .onFailure(ctx::failNow)
      .onSuccess(id -> {
        libraryId = id;
        ctx.completeNow();
      });
  }

  @AfterEach
  void afterEach(Vertx vertx, VertxTestContext ctx) {
    var postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);
    postgresClient.delete(referenceTable(), (CQLWrapper) null)
      .compose(rows -> postgresClient.delete(LIBRARY_TABLE, (CQLWrapper) null))
      .compose(rows -> postgresClient.delete(CAMPUS_TABLE, (CQLWrapper) null))
      .compose(rows -> postgresClient.delete(INSTITUTION_TABLE, (CQLWrapper) null))
      .onFailure(ctx::failNow)
      .onComplete(event -> ctx.completeNow());
  }

  @Test
  void getCollection_shouldReturnRecordCollectionBasedOnShadowLocationsQueryParam(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);

    var nonShadowLocation = sampleRecord();
    var shadowLocation = sampleRecord()
      .withIsShadow(true)
      .withName("test-shadow-location")
      .withCode("shadow");
    var locationsByQueryParam = Map.of(false, nonShadowLocation, true, shadowLocation);

    Future.all(
      postgresClient.save(referenceTable(), nonShadowLocation),
      postgresClient.save(referenceTable(), shadowLocation)
    )
      .compose(s -> {
        List<Future<TestResponse>> futures = new ArrayList<>();
        for (boolean param : locationsByQueryParam.keySet()) {
          var responseFuture = doGet(client, resourceUrl() + "?includeShadowLocations=" + param)
            .onComplete(verifyStatus(ctx, HTTP_OK))
            .andThen(ctx.succeeding(response -> ctx.verify(() -> {
              var locationsCollection = response.bodyAsClass(Locations.class);
              assertThat(locationsCollection)
                .as("verify collection for query param and value: includeShadowLocations=true")
                .isNotNull()
                .hasFieldOrPropertyWithValue("totalRecords", 1)
                .extracting(Locations::getLocations).asInstanceOf(InstanceOfAssertFactories.COLLECTION)
                .hasSize(1);

              var collectionRecord = locationsCollection.getLocations().getFirst();
              verifyRecordFields(collectionRecord, locationsByQueryParam.get(param),
                List.of(Location::getName, Location::getIsActive),
                "verify collection's record for query param: ?includeShadowLocations=true");
            })));
          futures.add(responseFuture);
        }
        return Future.all(futures);
      })
      .onFailure(ctx::failNow)
      .onSuccess(event -> ctx.completeNow());
  }

  @Test
  void put_shouldReturn422_whenServicePointsNotSet(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var invalidRecord = sampleRecord().withServicePointIds(null).withId(UUID.randomUUID().toString());

    doPut(client, resourceUrlById(invalidRecord.getId()), pojo2JsonObject(invalidRecord))
      .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        var actual = response.bodyAsClass(Errors.class);
        assertThat(actual.getErrors())
          .hasSize(2)
          .extracting(Error::getMessage)
          .containsExactlyInAnyOrder("A location must have at least one Service Point assigned.",
            "A Location's Primary Service point must be included as a Service Point.");
        ctx.completeNow();
      })));
  }

  @Test
  void put_shouldReturn422_whenIdChanged(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var sampleRecord = sampleRecord();

    doPost(client, resourceUrl(), pojo2JsonObject(sampleRecord))
      .onComplete(verifyStatus(ctx, HTTP_CREATED))
      .compose(response -> doPut(client, resourceUrlById(response.jsonBody().getString("id")),
        pojo2JsonObject(sampleRecord.withId(UUID.randomUUID().toString()))))
      .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        var actual = response.bodyAsClass(Errors.class);
        assertThat(actual.getErrors())
          .hasSize(1)
          .extracting(Error::getMessage)
          .containsExactlyInAnyOrder("Illegal operation: id cannot be changed");
        ctx.completeNow();
      })));
  }

  @Test
  void post_shouldReturn422_whenUnitsNotSet(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var invalidRecord = sampleRecord().withId(UUID.randomUUID().toString())
      .withInstitutionId(null).withCampusId(null).withLibraryId(null);

    doPost(client, resourceUrl(), pojo2JsonObject(invalidRecord))
      .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        var actual = response.bodyAsClass(Errors.class);
        assertThat(actual.getErrors())
          .hasSize(3)
          .extracting(Error::getMessage)
          .containsOnly("must not be null");
        ctx.completeNow();
      })));
  }

  @Test
  void post_shouldReturn422_whenCodeNotSet(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var invalidRecord = sampleRecord().withId(UUID.randomUUID().toString())
      .withCode(null);

    doPost(client, resourceUrl(), pojo2JsonObject(invalidRecord))
      .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        var actual = response.bodyAsClass(Errors.class);
        assertThat(actual.getErrors())
          .hasSize(1)
          .extracting(Error::getMessage)
          .containsOnly("must not be null");
        ctx.completeNow();
      })));
  }

  @Test
  void post_shouldReturn422_whenSameName(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var sampleRecord = sampleRecord();

    doPost(client, resourceUrl(), pojo2JsonObject(sampleRecord))
      .onComplete(verifyStatus(ctx, HTTP_CREATED))
      .compose(response -> doPost(client, resourceUrl(), pojo2JsonObject(sampleRecord)))
      .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        var actual = response.bodyAsClass(Errors.class);
        assertThat(actual.getErrors())
          .hasSize(1)
          .extracting(Error::getMessage)
          .containsOnly(
            "lower(f_unaccent(jsonb ->> 'name'::text)) value already exists in table location: test-location");
        ctx.completeNow();
      })));
  }

  @Test
  void post_shouldReturn422_whenSameCode(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var sampleRecord = sampleRecord();

    doPost(client, resourceUrl(), pojo2JsonObject(sampleRecord))
      .onComplete(verifyStatus(ctx, HTTP_CREATED))
      .compose(response ->
        doPost(client, resourceUrl(), pojo2JsonObject(sampleRecord.withName("another-name"))))
      .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        var actual = response.bodyAsClass(Errors.class);
        assertThat(actual.getErrors())
          .hasSize(1)
          .extracting(Error::getMessage)
          .containsOnly("lower(f_unaccent(jsonb ->> 'code'::text)) value already exists in table location: code");
        ctx.completeNow();
      })));
  }

  @Test
  void post_shouldReturn422_whenSameId(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var sampleRecord = sampleRecord();

    doPost(client, resourceUrl(), pojo2JsonObject(sampleRecord))
      .onComplete(verifyStatus(ctx, HTTP_CREATED))
      .compose(response ->
        doPost(client, resourceUrl(), pojo2JsonObject(sampleRecord.withId(response.jsonBody().getString("id")))))
      .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        var actual = response.bodyAsClass(Errors.class);
        assertThat(actual.getErrors())
          .hasSize(1)
          .extracting(Error::getMessage)
          .containsOnly("id value already exists in table location: " + sampleRecord.getId());
        ctx.completeNow();
      })));
  }
}
