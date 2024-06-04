package org.folio.rest.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.HttpStatus.HTTP_BAD_REQUEST;
import static org.folio.HttpStatus.HTTP_NOT_FOUND;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.folio.services.locationunit.InstitutionService.INSTITUTION_TABLE;
import static org.folio.utility.RestUtility.TENANT_ID;

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
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Locinst;
import org.folio.rest.jaxrs.model.Locinsts;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

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
  void delete_shouldReturn404_notConfigured(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();

    doDelete(client, "").onComplete(verifyStatus(ctx, HTTP_NOT_FOUND))
      .onComplete(ctx.succeeding(response -> ctx.verify(ctx::completeNow)));
  }
}
