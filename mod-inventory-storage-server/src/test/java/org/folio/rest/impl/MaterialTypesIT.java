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
import org.folio.rest.jaxrs.model.MaterialType;
import org.folio.rest.jaxrs.model.MaterialTypes;
import org.folio.rest.jaxrs.model.Metadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
class MaterialTypesIT extends BaseReferenceDataIntegrationTest<MaterialType, MaterialTypes> {

  @Override
  protected String referenceTable() {
    return "material_type";
  }

  @Override
  protected String resourceUrl() {
    return "/material-types";
  }

  @Override
  protected Class<MaterialType> targetClass() {
    return MaterialType.class;
  }

  @Override
  protected Class<MaterialTypes> collectionClass() {
    return MaterialTypes.class;
  }

  @Override
  protected MaterialType sampleRecord() {
    return new MaterialType()
      .withName("Sample-Material-Type");
  }

  @Override
  protected Function<MaterialTypes, List<MaterialType>> collectionRecordsExtractor() {
    return MaterialTypes::getMtypes;
  }

  @Override
  protected List<Function<MaterialType, Object>> recordFieldExtractors() {
    return List.of(
      MaterialType::getName
    );
  }

  @Override
  protected Function<MaterialType, String> idExtractor() {
    return MaterialType::getId;
  }

  @Override
  protected Function<MaterialType, Metadata> metadataExtractor() {
    return MaterialType::getMetadata;
  }

  @Override
  protected UnaryOperator<MaterialType> recordModifyingFunction() {
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
