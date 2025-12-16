package org.folio.rest.impl;

import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.HttpStatus.HTTP_NOT_FOUND;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.jaxrs.model.Mtype;
import org.folio.rest.jaxrs.model.Mtypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
class MaterialTypesIT extends BaseReferenceDataIntegrationTest<Mtype, Mtypes> {

  @Override
  protected String referenceTable() {
    return "material_type";
  }

  @Override
  protected String resourceUrl() {
    return "/material-types";
  }

  @Override
  protected Class<Mtype> targetClass() {
    return Mtype.class;
  }

  @Override
  protected Class<Mtypes> collectionClass() {
    return Mtypes.class;
  }

  @Override
  protected Mtype sampleRecord() {
    return new Mtype()
      .withName("Sample-Material-Type");
  }

  @Override
  protected Function<Mtypes, List<Mtype>> collectionRecordsExtractor() {
    return Mtypes::getMtypes;
  }

  @Override
  protected List<Function<Mtype, Object>> recordFieldExtractors() {
    return List.of(
      Mtype::getName
    );
  }

  @Override
  protected Function<Mtype, String> idExtractor() {
    return Mtype::getId;
  }

  @Override
  protected Function<Mtype, Metadata> metadataExtractor() {
    return Mtype::getMetadata;
  }

  @Override
  protected UnaryOperator<Mtype> recordModifyingFunction() {
    return materialType -> materialType.withName(materialType.getName() + "-Updated");
  }

  @Override
  protected List<String> queries() {
    return List.of("name==Sample-Material-Type");
  }

  @Test
  void cannotCreateMaterialTypeWithSameName(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();

    var materialType = new JsonObject()
      .put("name", "Can circulate");

    doPost(client, resourceUrl(), materialType)
      .onComplete(verifyStatus(ctx, HTTP_CREATED))
      .compose(response -> doPost(client, resourceUrl(), materialType))
      .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(response -> ctx.completeNow()));
  }

  @Test
  void cannotCreateMaterialTypeWithSameId(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();

    var materialType = new JsonObject()
      .put("name", "Can circulate");

    doPost(client, resourceUrl(), materialType)
      .onComplete(verifyStatus(ctx, HTTP_CREATED))
      .compose(response -> {
        var materialTypeId = response.jsonBody().getString("id");
        var anotherMaterialType = new JsonObject()
          .put("name", "Overnight")
          .put("id", materialTypeId);

        return doPost(client, resourceUrl(), anotherMaterialType);
      })
      .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(response -> ctx.completeNow()));
  }

  @Test
  void cannotProvideAdditionalPropertiesInMaterialType(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();

    var materialType = new JsonObject()
      .put("name", "Can Circulate")
      .put("additional", "foo");

    doPost(client, resourceUrl(), materialType)
      .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(response -> ctx.completeNow()));
  }

  @Test
  void cannotUpdateMaterialTypeThatDoesNotExist(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();

    var materialType = new JsonObject()
      .put("name", "Reading room");

    doPut(client, resourceUrlById(UUID.randomUUID().toString()), materialType)
      .onComplete(verifyStatus(ctx, HTTP_NOT_FOUND))
      .onComplete(ctx.succeeding(response -> ctx.completeNow()));
  }

  @Test
  void cannotGetMaterialTypeThatDoesNotExist(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();

    doGet(client, resourceUrlById(UUID.randomUUID().toString()))
      .onComplete(verifyStatus(ctx, HTTP_NOT_FOUND))
      .onComplete(ctx.succeeding(response -> ctx.completeNow()));
  }

  @Test
  void cannotDeleteMaterialTypeThatCannotBeFound(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();

    doDelete(client, resourceUrlById(UUID.randomUUID().toString()))
      .onComplete(verifyStatus(ctx, HTTP_NOT_FOUND))
      .onComplete(ctx.succeeding(response -> ctx.completeNow()));
  }
}
