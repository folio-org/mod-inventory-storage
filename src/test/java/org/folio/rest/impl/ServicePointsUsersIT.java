package org.folio.rest.impl;

import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.folio.rest.impl.ServicePointsIT.createHoldShelfExpiryPeriod;
import static org.folio.rest.impl.ServicePointsUserApi.SERVICE_POINT_USER_TABLE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.jaxrs.model.ServicePointsUser;
import org.folio.rest.jaxrs.model.ServicePointsUsers;
import org.junit.jupiter.api.Test;

class ServicePointsUsersIT extends BaseReferenceDataIntegrationTest<ServicePointsUser, ServicePointsUsers> {

  @Override
  protected String referenceTable() {
    return SERVICE_POINT_USER_TABLE;
  }

  @Override
  protected String resourceUrl() {
    return "/service-points-users";
  }

  @Override
  protected Class<ServicePointsUser> targetClass() {
    return ServicePointsUser.class;
  }

  @Override
  protected Class<ServicePointsUsers> collectionClass() {
    return ServicePointsUsers.class;
  }

  @Override
  protected ServicePointsUser sampleRecord() {
    return new ServicePointsUser()
      .withId("b29e771b-6f8b-46f1-9d72-f5c56ed2baad")
      .withUserId("29fef886-5822-4d8d-881b-c10be9b94602");
  }

  @Override
  protected Function<ServicePointsUsers, List<ServicePointsUser>> collectionRecordsExtractor() {
    return ServicePointsUsers::getServicePointsUsers;
  }

  @Override
  protected List<Function<ServicePointsUser, Object>> recordFieldExtractors() {
    return List.of(
      ServicePointsUser::getUserId
    );
  }

  @Override
  protected Function<ServicePointsUser, String> idExtractor() {
    return ServicePointsUser::getId;
  }

  @Override
  protected Function<ServicePointsUser, Metadata> metadataExtractor() {
    return ServicePointsUser::getMetadata;
  }

  @Override
  protected UnaryOperator<ServicePointsUser> recordModifyingFunction() {
    return servicePointsUser -> servicePointsUser.withUserId("29fef886-5822-4d8d-881b-c10be9b94603");
  }

  @Override
  protected List<String> queries() {
    return List.of("userId==29fef886-5822-4d8d-881b-c10be9b94602");
  }

  @Test
  void cannotCreateSpuWithNonExistantDefaultSp(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var spu = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("userId", UUID.randomUUID().toString())
      .put("defaultServicePointId", UUID.randomUUID().toString());
    doPost(client, resourceUrl(), spu)
      .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(response -> ctx.completeNow()));
  }

  @Test
  void canCreateSpuWithExistingDefaultSp(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var spId = UUID.randomUUID();
    var sp = new JsonObject()
      .put("id", spId.toString())
      .put("name", "Circ Desk 1")
      .put("code", "cd1")
      .put("pickupLocation", true)
      .put("discoveryDisplayName", "Circulation Desk -- Hallway")
      .put("holdShelfExpiryPeriod", createHoldShelfExpiryPeriod());
    doPost(client, "/service-points", sp)
      .compose(resp -> {
        var spu = new JsonObject()
          .put("id", UUID.randomUUID().toString())
          .put("userId", UUID.randomUUID().toString())
          .put("defaultServicePointId", spId.toString());
        return doPost(client, resourceUrl(), spu);
      })
      .onComplete(verifyStatus(ctx, HTTP_CREATED))
      .onComplete(ctx.succeeding(response -> ctx.completeNow()));
  }

  @Test
  void canGetSpus(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var spu1 = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("userId", UUID.randomUUID().toString());
    var spu2 = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("userId", UUID.randomUUID().toString());
    doPost(client, resourceUrl(), spu1)
      .compose(r -> doPost(client, resourceUrl(), spu2))
      .compose(r -> doGet(client, resourceUrl()))
      .onComplete(ctx.succeeding(response -> {
        ctx.verify(() -> {
          assertThat(response.jsonBody().getInteger("totalRecords"), is(2));
          ctx.completeNow();
        });
      }));
  }

  @Test
  void canDeleteAllSpus(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var spu1 = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("userId", UUID.randomUUID().toString());
    var spu2 = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("userId", UUID.randomUUID().toString());
    doPost(client, resourceUrl(), spu1)
      .compose(r -> doPost(client, resourceUrl(), spu2))
      .compose(r -> doDelete(client, resourceUrl()))
      .compose(r -> doGet(client, resourceUrl() + "?"))
      .onComplete(ctx.succeeding(response -> {
        ctx.verify(() -> {
          assertThat(response.jsonBody().getInteger("totalRecords"), is(0));
          ctx.completeNow();
        });
      }));
  }

  @Test
  void canQuerySpus(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var spId1 = UUID.randomUUID();
    var spId2 = UUID.randomUUID();
    var spId3 = UUID.randomUUID();
    var sp1 = new JsonObject()
      .put("id", spId1.toString())
      .put("name", "Circ Desk 2")
      .put("code", "cd2")
      .put("discoveryDisplayName", "Circulation Desk -- Hallway1")
      .put("pickupLocation", true)
      .put("holdShelfExpiryPeriod", createHoldShelfExpiryPeriod());
    var sp2 = new JsonObject()
      .put("id", spId2.toString())
      .put("name", "Circ Desk 3")
      .put("code", "cd3")
      .put("discoveryDisplayName", "Circulation Desk -- Stairs")
      .put("pickupLocation", true)
      .put("holdShelfExpiryPeriod", createHoldShelfExpiryPeriod());
    var sp3 = new JsonObject()
      .put("id", spId3.toString())
      .put("name", "Circ Desk 4")
      .put("code", "cd4")
      .put("discoveryDisplayName", "Circulation Desk -- Basement")
      .put("pickupLocation", true)
      .put("holdShelfExpiryPeriod", createHoldShelfExpiryPeriod());

    var expectedSpuIdRef = new AtomicReference<String>();
    doPost(client, "/service-points", sp1)
      .compose(r -> doPost(client, "/service-points", sp2))
      .compose(r -> doPost(client, "/service-points", sp3))
      .compose(r -> {
        var spuPayload = new JsonObject()
          .put("id", UUID.randomUUID().toString())
          .put("userId", UUID.randomUUID().toString())
          .put("servicePointsIds", List.of(spId1.toString(), spId2.toString()))
          .put("defaultServicePointId", spId1.toString());
        return doPost(client, resourceUrl(), spuPayload);
      })
      .compose(r -> {
        var spuId = UUID.randomUUID().toString();
        expectedSpuIdRef.set(spuId);
        var spuPayload = new JsonObject()
          .put("id", spuId)
          .put("userId", UUID.randomUUID().toString())
          .put("servicePointsIds", List.of(spId2.toString(), spId3.toString()))
          .put("defaultServicePointId", spId2.toString());
        return doPost(client, resourceUrl(), spuPayload);
      })
      .compose(r -> doGet(client, resourceUrl() + "?query=servicePointsIds=" + spId3))
      .onComplete(ctx.succeeding(response -> {
        ctx.verify(() -> {
          assertThat(response.jsonBody().getInteger("totalRecords"), is(1));
          String returnedId = response.jsonBody().getJsonArray("servicePointsUsers")
            .getJsonObject(0).getString("id");
          assertThat(returnedId, is(expectedSpuIdRef.get()));
          ctx.completeNow();
        });
      }));
  }

  @Test
  void cannotCreateServicePointUserIfUserAlreadyExists(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    String firstId = UUID.randomUUID().toString();
    String secondId = UUID.randomUUID().toString();
    String userId = UUID.randomUUID().toString();
    var firstSpu = new JsonObject()
      .put("id", firstId)
      .put("userId", userId);
    doPost(client, resourceUrl(), firstSpu)
      .compose(r -> {
        var secondSpu = new JsonObject()
          .put("id", secondId)
          .put("userId", userId);
        return doPost(client, resourceUrl(), secondSpu);
      })
      .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(response -> ctx.completeNow()));
  }
}
