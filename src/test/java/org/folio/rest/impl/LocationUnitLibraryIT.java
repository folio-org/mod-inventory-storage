package org.folio.rest.impl;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
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

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.folio.rest.impl.LocationUnitApi.CAMPUS_TABLE;
import static org.folio.rest.impl.LocationUnitApi.INSTITUTION_TABLE;
import static org.folio.services.locationunit.LibraryService.LIBRARY_TABLE;
import static org.folio.utility.RestUtility.TENANT_ID;

@ExtendWith(VertxExtension.class)
class LocationUnitLibraryIT extends BaseReferenceDataIntegrationTest<Loclib, Loclibs> {

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
      .compose(id -> postgresClient.save(CAMPUS_TABLE, campus.withInstitutionId(id)))
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
      .compose(rows -> postgresClient.delete(INSTITUTION_TABLE, (CQLWrapper) null))
      .onFailure(ctx::failNow)
      .onComplete(event -> ctx.completeNow());
  }

  @Test
  void put_shouldReturn422_whenIdIsNotMatchWithPayload(Vertx vertx, VertxTestContext ctx) {
    HttpClient client = vertx.createHttpClient();
    var invalidId = UUID.randomUUID().toString();
    var library = sampleRecord().withId(UUID.randomUUID().toString());

    doPut(client, resourceUrlById(invalidId), pojo2JsonObject(library))
      .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        var actual = response.bodyAsClass(Errors.class);
        assertThat(actual.getErrors())
          .hasSize(1)
          .extracting(Error::getMessage)
          .containsExactly("Illegal operation: id cannot be changed");
        ctx.completeNow();
      })));
  }
}
