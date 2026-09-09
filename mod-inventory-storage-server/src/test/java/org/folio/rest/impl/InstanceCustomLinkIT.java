package org.folio.rest.impl;

import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.HttpStatus.HTTP_NO_CONTENT;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.folio.services.instance.InstanceCustomLinkService.INSTANCE_CUSTOM_LINK_TABLE;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.folio.rest.jaxrs.model.InstanceCustomLink;
import org.folio.rest.jaxrs.model.InstanceCustomLinks;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@ExtendWith(VertxExtension.class)
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
      .withLinkText("Sample OPAC")
      .withLink("http://localhost");
  }

  @Override
  protected Function<InstanceCustomLinks, List<InstanceCustomLink>> collectionRecordsExtractor() {
    return InstanceCustomLinks::getInstanceCustomLinks;
  }

  @Override
  protected List<Function<InstanceCustomLink, Object>> recordFieldExtractors() {
    return List.of(
      InstanceCustomLink::getLink,
      InstanceCustomLink::getLinkText,
      InstanceCustomLink::getName,
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
  
  @BeforeEach
  void beforeEach(Vertx vertx, VertxTestContext ctx) {
    deleteAllInstanceCustomLinkData(vertx, ctx);
  }

  @Test
  void createWithValidLinkWithTokens(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var req = new JsonObject()
      .put("name", "name")
      .put("linkText", "link text")
      .put("link", "https://base.host/{{UUID}}/{{HRID}}/{{indexTitle}}")
      .put("source", "local");
    doPost(client, resourceUrl(), req)
      .onComplete(verifyStatus(ctx, HTTP_CREATED))
      .onComplete(ctx.succeeding(response -> ctx.completeNow()));
  }

  @Test
  void createWithValidLinkWithoutTokens(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var req = new JsonObject()
      .put("name", "name")
      .put("linkText", "link text")
      .put("link", "https://base.host/just-a-link")
      .put("source", "local");
    doPost(client, resourceUrl(), req)
      .onComplete(verifyStatus(ctx, HTTP_CREATED))
      .onComplete(ctx.succeeding(response -> ctx.completeNow()));
  }

  @Test
  void cannotCreateWithInvalidTokensInLink(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var req = new JsonObject()
      .put("name", "name")
      .put("linkText", "link text")
      .put("link", "https://base.host/{{token-but-invalid}}")
      .put("source", "local");
    doPost(client, resourceUrl(), req)
      .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(response -> ctx.completeNow()));
  }

  @Test
  void cannotCreateWithInvalidLinkProtocol(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var req = new JsonObject()
      .put("name", "name")
      .put("linkText", "link text")
      .put("link", "ftp://base.host")
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
        .put("linkText", "link text " + i)
        .put("link", "https://base.host/" + i)
        .put("source", "local");
      doPost(client, resourceUrl(), req)
        .onComplete(verifyStatus(ctx, HTTP_CREATED));
    }
    var overLimit = new JsonObject()
      .put("name", "name 11")
      .put("linkText", "link text 11")
      .put("link", "https://base.host/11")
      .put("source", "local");
    doPost(client, resourceUrl(), overLimit)
      .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(response2 -> ctx.completeNow()));
  }

  @ParameterizedTest
  @MethodSource("duplicateFieldValueCreates")
  void cannotReuseDuplicateFieldValueOnCreate(JsonObject first, JsonObject second, String duplicatedField,
      Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    doPost(client, resourceUrl(), first)
      .onComplete(ctx.succeeding(response1 ->
        doPost(client, resourceUrl(), second)
          .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
          .onComplete(event -> ctx.verify(() -> {
            assertDuplicateError(event.result(), duplicatedField);
            ctx.completeNow();
          }))
      ));
  }

  @Test
  void updateWithValidLink(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var req1 = new JsonObject()
      .put("name", "name 1")
      .put("linkText", "link text 1")
      .put("link", "https://base1.host/{{UUID}}")
      .put("source", "local");
    var req2 = new JsonObject()
      .put("name", "name 1")
      .put("linkText", "link text 1")
      .put("link", "https://base1.host/{{HRID}}")
      .put("source", "local");
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
  void cannotUpdateWithMissingTokensInLink(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var req1 = new JsonObject()
      .put("name", "name 1")
      .put("linkText", "link text 1")
      .put("link", "https://base1.host/{{UUID}}")
      .put("source", "local");
    var req2 = new JsonObject()
      .put("name", "name 1")
      .put("linkText", "link text 1")
      .put("link", "https://base1.host/{{not-a-token}}")
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

  @Test
  void cannotUpdateWithInvalidLink(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var req1 = new JsonObject()
      .put("name", "name 1")
      .put("linkText", "link text 1")
      .put("link", "https://base1.host")
      .put("source", "local");
    var req2 = new JsonObject()
      .put("name", "name 1")
      .put("linkText", "link text 1")
      .put("link", "uri:urn:base")
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
      String duplicatedField, Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    doPost(client, resourceUrl(), first)
      .onComplete(ctx.succeeding(response1 ->
        doPost(client, resourceUrl(), second)
          .onComplete(ctx.succeeding(response2 -> {
            var id2 = response2.jsonBody().getString("id");
            update.put("id", id2);
            doPut(client, resourceUrlById(id2), update)
              .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
              .onComplete(event -> ctx.verify(() -> {
                assertDuplicateError(event.result(), duplicatedField);
                ctx.completeNow();
              }));
          }))
      ));
  }

  private void assertDuplicateError(TestResponse response, String fieldName) {
    var errors = response.jsonBody().getJsonArray("errors");
    assertThat(errors.size(), is(1));

    var error = errors.getJsonObject(0);
    var errorParameters = error.getJsonArray("parameters");
    assertThat(errorParameters.size(), is(1));

    var parameter = errorParameters.getJsonObject(0);
    assertThat(parameter.getString("key"), is(fieldName));
  }

  private static void deleteAllInstanceCustomLinkData(Vertx vertx, VertxTestContext ctx) {
    var postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);

    postgresClient.delete(INSTANCE_CUSTOM_LINK_TABLE, (CQLWrapper) null)
      .onFailure(ctx::failNow)
      .onComplete(event -> ctx.completeNow());
  }

  @SuppressWarnings("checkstyle:MethodLength")
  private static Stream<Arguments> duplicateFieldValueCreates() {
    return Stream.of(
      arguments(
        new JsonObject()
          .put("name", "duplicate name")
          .put("linkText", "link text 1")
          .put("link", "https://base1.host")
          .put("source", "local"),
        new JsonObject()
          .put("name", "duplicate name")
          .put("linkText", "link text 2")
          .put("link", "https://base2.host")
          .put("source", "local"),
        "name"
      ),
      arguments(
        new JsonObject()
          .put("name", "name 1")
          .put("linkText", "duplicate text")
          .put("link", "https://base1.host")
          .put("source", "local"),
        new JsonObject()
          .put("name", "name 2")
          .put("linkText", "duplicate text")
          .put("link", "https://base2.host")
          .put("source", "local"),
        "linkText"
      ),
      arguments(
        new JsonObject()
          .put("name", "name 1")
          .put("linkText", "link text 1")
          .put("link", "https://duplicate")
          .put("source", "local"),
        new JsonObject()
          .put("name", "name 2")
          .put("linkText", "link text 2")
          .put("link", "https://duplicate")
          .put("source", "local"),
        "link"
      )
    );
  }

  @SuppressWarnings("checkstyle:MethodLength")
  private static Stream<Arguments> duplicateFieldValueUpdates() {
    return Stream.of(
      arguments(
        new JsonObject()
          .put("name", "duplicate name")
          .put("linkText", "link text 1")
          .put("link", "https://base1.host")
          .put("source", "local"),
        new JsonObject()
          .put("name", "name 2")
          .put("linkText", "link text 2")
          .put("link", "https://base2.host")
          .put("source", "local"),
        new JsonObject()
          .put("name", "duplicate name")
          .put("linkText", "link text 2")
          .put("link", "https://base2.host")
          .put("source", "local"),
        "name"
      ),
      arguments(
        new JsonObject()
          .put("name", "name 1")
          .put("linkText", "duplicate text")
          .put("link", "https://base1.host")
          .put("source", "local"),
        new JsonObject()
          .put("name", "name 2")
          .put("linkText", "link text 2")
          .put("link", "https://base2.host")
          .put("source", "local"),
        new JsonObject()
          .put("name", "name 2")
          .put("linkText", "duplicate text")
          .put("link", "https://base2.host")
          .put("source", "local"),
        "linkText"
      ),
      arguments(
        new JsonObject()
          .put("name", "name 1")
          .put("linkText", "link text 1")
          .put("link", "https://duplicate")
          .put("source", "local"),
        new JsonObject()
          .put("name", "name 2")
          .put("linkText", "link text 2")
          .put("link", "https://base2.host")
          .put("source", "local"),
        new JsonObject()
          .put("name", "name 2")
          .put("linkText", "link text 2")
          .put("link", "https://duplicate")
          .put("source", "local"),
        "link"
      )
    );
  }
}
