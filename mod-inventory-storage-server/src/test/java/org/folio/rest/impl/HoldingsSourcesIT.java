package org.folio.rest.impl;

import static org.folio.HttpStatus.HTTP_BAD_REQUEST;
import static org.folio.HttpStatus.HTTP_NOT_FOUND;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.folio.rest.impl.HoldingsRecordsSourceApi.HOLDINGS_RECORDS_SOURCE_TABLE;
import static org.folio.rest.jaxrs.model.HoldingsRecordsSource.Source.LOCAL;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.folio.rest.jaxrs.model.HoldingsRecordsSource;
import org.folio.rest.jaxrs.model.HoldingsRecordsSources;
import org.folio.rest.jaxrs.model.Metadata;
import org.junit.jupiter.api.Test;

class HoldingsSourcesIT extends BaseReferenceDataIntegrationTest<HoldingsRecordsSource, HoldingsRecordsSources> {

  @Override
  protected String referenceTable() {
    return HOLDINGS_RECORDS_SOURCE_TABLE;
  }

  @Override
  protected String resourceUrl() {
    return "/holdings-sources";
  }

  @Override
  protected Class<HoldingsRecordsSource> targetClass() {
    return HoldingsRecordsSource.class;
  }

  @Override
  protected Class<HoldingsRecordsSources> collectionClass() {
    return HoldingsRecordsSources.class;
  }

  @Override
  protected HoldingsRecordsSource sampleRecord() {
    return new HoldingsRecordsSource()
      .withName("Sample-Holdings-Source")
      .withSource(LOCAL);
  }

  @Override
  protected Function<HoldingsRecordsSources, List<HoldingsRecordsSource>> collectionRecordsExtractor() {
    return HoldingsRecordsSources::getHoldingsRecordsSources;
  }

  @Override
  protected List<Function<HoldingsRecordsSource, Object>> recordFieldExtractors() {
    return List.of(
      HoldingsRecordsSource::getName,
      HoldingsRecordsSource::getSource
    );
  }

  @Override
  protected Function<HoldingsRecordsSource, String> idExtractor() {
    return HoldingsRecordsSource::getId;
  }

  @Override
  protected Function<HoldingsRecordsSource, Metadata> metadataExtractor() {
    return HoldingsRecordsSource::getMetadata;
  }

  @Override
  protected UnaryOperator<HoldingsRecordsSource> recordModifyingFunction() {
    return holdingsSource -> holdingsSource.withName(holdingsSource.getName() + "-Modified");
  }

  @Override
  protected List<String> queries() {
    return List.of(
      "name==Sample-Holdings-Source",
      "source==LOCAL"
    );
  }

  @Test
  void cannotCreateHoldingsSourceWithDuplicateId(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var sourceId = UUID.randomUUID().toString();
    var req1 = new JsonObject()
      .put("id", sourceId)
      .put("name", "source with id");
    doPost(client, resourceUrl(), req1)
      .onComplete(ctx.succeeding(response1 -> {
        var req2 = new JsonObject()
          .put("id", sourceId)
          .put("name", "new source with duplicate id");
        doPost(client, resourceUrl(), req2)
          .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
          .onComplete(ctx.succeeding(response2 -> ctx.completeNow()));
      }));
  }

  @Test
  void cannotCreateHoldingsSourceWithDuplicateName(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    UUID sourceId = UUID.randomUUID();
    var req1 = new JsonObject()
      .put("id", sourceId.toString())
      .put("name", "original source name");
    doPost(client, resourceUrl(), req1)
      .onComplete(ctx.succeeding(response1 -> {
        var source = response1.jsonBody();
        ctx.verify(() -> {
          assertThat(source.getString("id"), is(sourceId.toString()));
          assertThat(source.getString("name"), is("original source name"));
        });
        var req2 = new JsonObject().put("name", "original source name");
        doPost(client, resourceUrl(), req2)
          .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
          .onComplete(ctx.succeeding(response2 -> ctx.completeNow()));
      }));
  }

  @Test
  void cannotCreateHoldingsSourceWithIdThatIsNotUuid(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var req = new JsonObject()
      .put("id", "1234567")
      .put("name", "source with invalid id");
    doPost(client, resourceUrl(), req)
      .onComplete(ctx.succeeding(response -> {
        ctx.verify(() -> {
          assertThat(response.status(), is(422));
          JsonArray errors = response.jsonBody().getJsonArray("errors");
          assertThat(errors.size(), is(1));
          JsonObject firstError = errors.getJsonObject(0);
          assertThat(firstError.getString("message"), containsString("must match"));
          assertThat(firstError.getJsonArray("parameters").getJsonObject(0).getString("key"), is("id"));
        });
        ctx.completeNow();
      }));
  }

  @Test
  void canQueryForMultipleHoldingsSources(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var req1 = new JsonObject().put("name", "multisource 1");
    var req2 = new JsonObject().put("name", "multisource 2");
    doPost(client, resourceUrl(), req1)
      .compose(resp -> doPost(client, resourceUrl(), req2))
      .compose(resp -> doGet(client, resourceUrl() + "?query=name==\"multisource*\""))
      .onComplete(ctx.succeeding(response -> {
        var array = response.jsonBody().getJsonArray("holdingsRecordsSources");
        ctx.verify(() -> assertThat(array.size(), is(2)));
        ctx.completeNow();
      }));
  }

  @Test
  void cannotReplaceNonexistentHoldingsSource(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    doPut(client, resourceUrlById(UUID.randomUUID().toString()), new JsonObject().put("name", "updated source name"))
      .onComplete(verifyStatus(ctx, HTTP_NOT_FOUND))
      .onComplete(ctx.succeeding(response -> ctx.completeNow()));
  }

  @Test
  void canNotRemoveSpecialHoldingsSources(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var req = new JsonObject()
      .put("name", "source with folio source")
      .put("source", "folio");
    doPost(client, resourceUrl(), req)
      .onComplete(ctx.succeeding(response -> {
        var id = response.jsonBody().getString("id");
        doDelete(client, resourceUrlById(id))
          .onComplete(verifyStatus(ctx, HTTP_BAD_REQUEST))
          .onComplete(ctx.succeeding(delResp -> ctx.completeNow()));
      }));
  }
}
