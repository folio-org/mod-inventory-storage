package org.folio.rest.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.folio.rest.impl.LocationUnitApi.CAMPUS_TABLE;
import static org.folio.rest.impl.LocationUnitApi.INSTITUTION_TABLE;
import static org.folio.services.location.LocationService.LOCATION_TABLE;
import static org.folio.services.locationunit.LibraryService.LIBRARY_TABLE;
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
    var primaryServicePoint = UUID.randomUUID();
    return new Location()
      .withName("test-location")
      .withCode("code")
      .withCampusId(campusId)
      .withLibraryId(libraryId)
      .withInstitutionId(institutionId)
      .withPrimaryServicePoint(primaryServicePoint)
      .withServicePointIds(List.of(primaryServicePoint));
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
    return List.of("name==test-location", "code=code", "campusId==" + campusId, "libraryId==" + libraryId);
  }

  @BeforeEach
  void beforeEach(Vertx vertx, VertxTestContext ctx) {
    var postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);
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
  void put_shouldReturn422_whenServicePointsNotSet(Vertx vertx, VertxTestContext ctx) {
    HttpClient client = vertx.createHttpClient();
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
}
