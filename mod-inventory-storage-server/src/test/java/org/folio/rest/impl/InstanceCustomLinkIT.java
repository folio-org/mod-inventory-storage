package org.folio.rest.impl;

import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.HttpStatus.HTTP_NO_CONTENT;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.folio.services.instance.InstanceCustomLinkService.INSTANCE_CUSTOM_LINK_TABLE;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.folio.rest.jaxrs.model.InstanceCustomLink;
import org.folio.rest.jaxrs.model.InstanceCustomLinks;
import org.folio.rest.jaxrs.model.Metadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class InstanceCustomLinkIT extends BaseReferenceDataIntegrationTest<InstanceCustomLink, InstanceCustomLinks> {

  @Override
  protected String referenceTable() {
    return INSTANCE_CUSTOM_LINK_TABLE;
  }

  @Override
  protected String resourceUrl() {
    return "/instance-custom-links";
  }

  @Override
  protected Class<InstanceCustomLink> targetClass() {
    return InstanceCustomLink.class;
  }

  @Override
  protected Class<InstanceCustomLinks> collectionClass() {
    return InstanceCustomLinks.class;
  }

  @Override
  protected InstanceCustomLink sampleRecord() {
    return new InstanceCustomLink()
      .withName("sample")
      .withSource(InstanceCustomLink.Source.LOCAL)
      .withBaseUrl("http://localhost")
      .withLinkText("Sample OPAC");
  }

  @Override
  protected Function<InstanceCustomLinks, List<InstanceCustomLink>> collectionRecordsExtractor() {
    return InstanceCustomLinks::getInstanceCustomLinks;
  }

  @Override
  protected List<Function<InstanceCustomLink, Object>> recordFieldExtractors() {
    return List.of(
      InstanceCustomLink::getBaseUrl,
      InstanceCustomLink::getLinkText,
      InstanceCustomLink::getName,
      InstanceCustomLink::getQueryString,
      InstanceCustomLink::getShow,
      InstanceCustomLink::getSource
    );
  }

  @Override
  protected Function<InstanceCustomLink, String> idExtractor() {
    return InstanceCustomLink::getId;
  }

  @Override
  protected Function<InstanceCustomLink, Metadata> metadataExtractor() {
    return InstanceCustomLink::getMetadata;
  }

  @Override
  protected UnaryOperator<InstanceCustomLink> recordModifyingFunction() {
    return instanceCustomLink -> instanceCustomLink.withName(instanceCustomLink.getName() + "-modified");
  }

  @Override
  protected List<String> queries() {
    return List.of(
      "name==sample",
      "source==LOCAL"
    );
  }
  
  @Test
  void createWithValidQueryString(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var req = new JsonObject()
      .put("name", "name")
      .put("baseUrl", "https://base.host")
      .put("linkText", "link text")
      .put("source", "local")
      .put("queryString", "{{UUID}}/{{HRID}}/{{indexTitle}}");
    doPost(client, resourceUrl(), req)
      .onComplete(verifyStatus(ctx, HTTP_CREATED))
      .onComplete(ctx.succeeding(response -> ctx.completeNow()));
  }
  
  @Test
  void cannotCreateWithMissingTokensInQueryString(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var req = new JsonObject()
      .put("name", "name")
      .put("baseUrl", "https://base.host")
      .put("linkText", "link text")
      .put("source", "local")
      .put("queryString", "no-valid-tokens");
    doPost(client, resourceUrl(), req)
      .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(response -> ctx.completeNow()));
  }

  @Test
  void cannotCreateWithInvalidBaseUrl(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var req = new JsonObject()
      .put("name", "name")
      .put("baseUrl", "ftp://base.host")
      .put("linkText", "link text")
      .put("source", "local");
    doPost(client, resourceUrl(), req)
      .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(response -> ctx.completeNow()));
  }

  @Test
  void cannotCreateBeyondLinkCountLimit(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    for (var i = 1; i <= 10; i++) {
      var req = new JsonObject()
        .put("name", "name " + i)
        .put("baseUrl", "https://base.host/" + i)
        .put("linkText", "link text " + i)
        .put("source", "local");
      doPost(client, resourceUrl(), req)
        .onComplete(verifyStatus(ctx, HTTP_CREATED));
    }
    var overLimit = new JsonObject()
      .put("name", "name 11")
      .put("baseUrl", "https://base.host/11")
      .put("linkText", "link text 11")
      .put("source", "local");
    doPost(client, resourceUrl(), overLimit)
      .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(response2 -> ctx.completeNow()));
  }

  @ParameterizedTest
  @MethodSource("duplicateFieldValueCreates")
  void cannotReuseDuplicateFieldValueOnCreate(JsonObject first, JsonObject second, Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    doPost(client, resourceUrl(), first)
      .onComplete(ctx.succeeding(response1 ->
        doPost(client, resourceUrl(), second)
          .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
          .onComplete(ctx.succeeding(response2 -> ctx.completeNow()))
      ));
  }

  @Test
  void updateWithValidQueryString(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var req1 = new JsonObject()
      .put("name", "name 1")
      .put("baseUrl", "https://base1.host")
      .put("linkText", "link text 1")
      .put("source", "local")
      .put("queryString", "/{{UUID}}");
    var req2 = new JsonObject()
      .put("name", "name 1")
      .put("baseUrl", "https://base1.host")
      .put("linkText", "link text 1")
      .put("source", "local")
      .put("queryString", "/{{HRID}}");
    doPost(client, resourceUrl(), req1)
      .onComplete(ctx.succeeding(response1 -> {
        var id = response1.jsonBody().getString("id");
        req2.put("id", id);
        doPut(client, resourceUrlById(id), req2)
          .onComplete(verifyStatus(ctx, HTTP_NO_CONTENT))
          .onComplete(ctx.succeeding(response -> ctx.completeNow()));
      }));
  }

  @Test
  void cannotUpdateWithMissingTokensInQueryString(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var req1 = new JsonObject()
      .put("name", "name 1")
      .put("baseUrl", "https://base1.host")
      .put("linkText", "link text 1")
      .put("source", "local")
      .put("queryString", "/{{UUID}}");
    var req2 = new JsonObject()
      .put("name", "name 1")
      .put("baseUrl", "https://base1.host")
      .put("linkText", "link text 1")
      .put("source", "local")
      .put("queryString", "/{{not-a-token}}");
    doPost(client, resourceUrl(), req1)
      .onComplete(ctx.succeeding(response1 -> {
        var id = response1.jsonBody().getString("id");
        req2.put("id", id);
        doPut(client, resourceUrlById(id), req2)
          .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
          .onComplete(ctx.succeeding(response3 -> ctx.completeNow()));
      }));
  }

  @Test
  void cannotUpdateWithInvalidBaseUrl(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var req1 = new JsonObject()
      .put("name", "name 1")
      .put("baseUrl", "https://base1.host")
      .put("linkText", "link text 1")
      .put("source", "local");
    var req2 = new JsonObject()
      .put("name", "name 1")
      .put("baseUrl", "uri:urn:base")
      .put("linkText", "link text 1")
      .put("source", "local");
    doPost(client, resourceUrl(), req1)
      .onComplete(ctx.succeeding(response1 -> {
        var id = response1.jsonBody().getString("id");
        req2.put("id", id);
        doPut(client, resourceUrlById(id), req2)
          .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
          .onComplete(ctx.succeeding(response3 -> ctx.completeNow()));
      }));
  }

  @ParameterizedTest
  @MethodSource("duplicateFieldValueUpdates")
  void cannotReuseDuplicateFieldValueOnUpdate(JsonObject first, JsonObject second, JsonObject update,
      Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    doPost(client, resourceUrl(), first)
      .onComplete(ctx.succeeding(response1 ->
        doPost(client, resourceUrl(), second)
          .onComplete(ctx.succeeding(response2 -> {
            var id2 = response2.jsonBody().getString("id");
            update.put("id", id2);
            doPut(client, resourceUrlById(id2), update)
              .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
              .onComplete(ctx.succeeding(response3 -> ctx.completeNow()));
          }))
      ));
  }

  @SuppressWarnings("checkstyle:MethodLength")
  private static Stream<Arguments> duplicateFieldValueCreates() {
    return Stream.of(
      arguments(
        new JsonObject()
          .put("name", "duplicate name")
          .put("baseUrl", "https://base1.host")
          .put("linkText", "link text 1")
          .put("source", "local"),
        new JsonObject()
          .put("name", "duplicate name")
          .put("baseUrl", "https://base2.host")
          .put("linkText", "link text 2")
          .put("source", "local")
      ),
      arguments(
        new JsonObject()
          .put("name", "name 1")
          .put("baseUrl", "https://base1.host")
          .put("linkText", "duplicate text")
          .put("source", "local"),
        new JsonObject()
          .put("name", "name 2")
          .put("baseUrl", "https://base2.host")
          .put("linkText", "duplicate text")
          .put("source", "local")
      ),
      arguments(
        new JsonObject()
          .put("name", "name 1")
          .put("baseUrl", "https://duplicate")
          .put("linkText", "link text 1")
          .put("source", "local"),
        new JsonObject()
          .put("name", "name 2")
          .put("baseUrl", "https://duplicate")
          .put("linkText", "link text 2")
          .put("source", "local")
      )
    );
  }

  @SuppressWarnings("checkstyle:MethodLength")
  private static Stream<Arguments> duplicateFieldValueUpdates() {
    return Stream.of(
      arguments(
        new JsonObject()
          .put("name", "duplicate name")
          .put("baseUrl", "https://base1.host")
          .put("linkText", "link text 1")
          .put("source", "local"),
        new JsonObject()
          .put("name", "name 2")
          .put("baseUrl", "https://base2.host")
          .put("linkText", "link text 2")
          .put("source", "local"),
        new JsonObject()
          .put("name", "duplicate name")
          .put("baseUrl", "https://base2.host")
          .put("linkText", "link text 2")
          .put("source", "local")
      ),
      arguments(
        new JsonObject()
          .put("name", "name 1")
          .put("baseUrl", "https://base1.host")
          .put("linkText", "duplicate text")
          .put("source", "local"),
        new JsonObject()
          .put("name", "name 2")
          .put("baseUrl", "https://base2.host")
          .put("linkText", "link text 2")
          .put("source", "local"),
        new JsonObject()
          .put("name", "name 2")
          .put("baseUrl", "https://base2.host")
          .put("linkText", "duplicate text")
          .put("source", "local")
      ),
      arguments(
        new JsonObject()
          .put("name", "name 1")
          .put("baseUrl", "https://duplicate")
          .put("linkText", "link text 1")
          .put("source", "local"),
        new JsonObject()
          .put("name", "name 2")
          .put("baseUrl", "https://base2.host")
          .put("linkText", "link text 2")
          .put("source", "local"),
        new JsonObject()
          .put("name", "name 2")
          .put("baseUrl", "https://duplicate")
          .put("linkText", "link text 2")
          .put("source", "local")
      )
    );
  }
}
