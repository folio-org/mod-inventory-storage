package org.folio.rest.impl;

import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.HttpStatus.HTTP_NOT_FOUND;
import static org.folio.HttpStatus.HTTP_NO_CONTENT;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.folio.rest.impl.ServicePointApi.SERVICE_POINT_CREATE_ERR_MSG_WITHOUT_BEING_PICKUP_LOC;
import static org.folio.rest.impl.ServicePointApi.SERVICE_POINT_CREATE_ERR_MSG_WITHOUT_HOLD_EXPIRY;
import static org.folio.rest.impl.ServicePointApi.SERVICE_POINT_TABLE;
import static org.folio.rest.jaxrs.model.Servicepoint.DefaultCheckInActionForUseAtLocation.KEEP_ON_HOLD_SHELF;
import static org.folio.rest.jaxrs.model.Servicepoint.HoldShelfClosedLibraryDateManagement.MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS;
import static org.folio.rest.support.AwaitConfiguration.awaitAtMost;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;

import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.folio.rest.jaxrs.model.HoldShelfExpiryPeriod;
import org.folio.rest.jaxrs.model.HoldShelfExpiryPeriod.IntervalId;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.jaxrs.model.Servicepoint;
import org.folio.rest.jaxrs.model.Servicepoints;
import org.folio.rest.support.messages.ServicePointEventMessageChecks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class ServicePointsIT extends BaseReferenceDataIntegrationTest<Servicepoint, Servicepoints> {

  private final ServicePointEventMessageChecks servicePointEventMessageChecks =
    new ServicePointEventMessageChecks(KAFKA_CONSUMER);

  @Override
  protected String referenceTable() {
    return SERVICE_POINT_TABLE;
  }

  @Override
  protected String resourceUrl() {
    return "/service-points";
  }

  @Override
  protected Class<Servicepoint> targetClass() {
    return Servicepoint.class;
  }

  @Override
  protected Class<Servicepoints> collectionClass() {
    return Servicepoints.class;
  }

  @Override
  protected Servicepoint sampleRecord() {
    return new Servicepoint()
      .withName("Sample-Service-Point")
      .withDiscoveryDisplayName("Sample-Discovery-Display-Name")
      .withCode("SP001")
      .withDescription("Sample description for service point");
  }

  @Override
  protected Function<Servicepoints, List<Servicepoint>> collectionRecordsExtractor() {
    return Servicepoints::getServicepoints;
  }

  @Override
  protected List<Function<Servicepoint, Object>> recordFieldExtractors() {
    return List.of(
      Servicepoint::getName,
      Servicepoint::getCode,
      Servicepoint::getDescription
    );
  }

  @Override
  protected Function<Servicepoint, String> idExtractor() {
    return Servicepoint::getId;
  }

  @Override
  protected Function<Servicepoint, Metadata> metadataExtractor() {
    return Servicepoint::getMetadata;
  }

  @Override
  protected UnaryOperator<Servicepoint> recordModifyingFunction() {
    return servicePoint -> servicePoint.withName("Updated");
  }

  @Override
  protected List<String> queries() {
    return List.of("name==Sample-Service-Point", "code==SP001");
  }

  @Test
  void cannotCreateServicePointWithoutName(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var servicePoint = new JsonObject()
      .put("code", "cd1")
      .put("discoveryDisplayName", "Circulation Desk -- Hallway")
      .put("pickupLocation", true)
      .put("holdShelfExpiryPeriod", JsonObject.mapFrom(createHoldShelfExpiryPeriod()));

    doPost(client, resourceUrl(), servicePoint)
      .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(response -> ctx.completeNow()));
  }

  @Test
  void cannotCreateServicePointWithoutCode(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var servicePoint = new JsonObject()
      .put("name", "Circ Desk 103")
      .put("discoveryDisplayName", "Circulation Desk -- Hallway")
      .put("pickupLocation", true)
      .put("holdShelfExpiryPeriod", JsonObject.mapFrom(createHoldShelfExpiryPeriod()));

    doPost(client, resourceUrl(), servicePoint)
      .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(response -> ctx.completeNow()));
  }

  @Test
  void cannotCreateServicePointWithoutDdName(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var servicePoint = new JsonObject()
      .put("name", "Circ Desk 1")
      .put("code", "cd1")
      .put("pickupLocation", true)
      .put("holdShelfExpiryPeriod", JsonObject.mapFrom(createHoldShelfExpiryPeriod()));

    doPost(client, resourceUrl(), servicePoint)
      .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(response -> ctx.completeNow()));
  }

  @Test
  void cannotCreateServicePointWithDuplicateName(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var servicePoint = new JsonObject()
      .put("name", "Circ Desk 1")
      .put("code", "cd1")
      .put("discoveryDisplayName", "Circulation Desk -- Hallway")
      .put("pickupLocation", true)
      .put("holdShelfExpiryPeriod", JsonObject.mapFrom(createHoldShelfExpiryPeriod()));

    doPost(client, resourceUrl(), servicePoint)
      .onComplete(verifyStatus(ctx, HTTP_CREATED))
      .compose(response -> {
        var duplicateServicePoint = new JsonObject()
          .put("name", "Circ Desk 1")
          .put("code", "cd2")
          .put("discoveryDisplayName", "Circulation Desk -- Bathroom")
          .put("pickupLocation", true)
          .put("holdShelfExpiryPeriod", JsonObject.mapFrom(createHoldShelfExpiryPeriod()));
        return doPost(client, resourceUrl(), duplicateServicePoint);
      })
      .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(response -> ctx.completeNow()));
  }

  @Test
  void cannotUpdateNonExistentServicePoint(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var servicePoint = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("name", "Circ Desk 2")
      .put("code", "cd2")
      .put("discoveryDisplayName", "Circulation Desk -- Basement")
      .put("pickupLocation", false);

    doPut(client, resourceUrlById(servicePoint.getString("id")), servicePoint)
      .onComplete(verifyStatus(ctx, HTTP_NOT_FOUND))
      .onComplete(ctx.succeeding(response -> ctx.completeNow()));
  }

  @Test
  void cannotDeleteNonExistentServicePoint(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();

    doDelete(client, resourceUrlById(UUID.randomUUID().toString()))
      .onComplete(verifyStatus(ctx, HTTP_NOT_FOUND))
      .onComplete(ctx.succeeding(response -> ctx.completeNow()));
  }

  @Test
  void canCreateServicePointWithHoldShelfExpiryPeriod(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var servicePoint = new JsonObject()
      .put("name", "Circ Desk 11")
      .put("code", "cd11")
      .put("discoveryDisplayName", "Circulation Desk 11 -- Hallway")
      .put("pickupLocation", true)
      .put("holdShelfExpiryPeriod", JsonObject.mapFrom(createHoldShelfExpiryPeriod(3, IntervalId.MINUTES)));

    doPost(client, resourceUrl(), servicePoint)
      .onComplete(verifyStatus(ctx, HTTP_CREATED))
      .onComplete(ctx.succeeding(response -> {
        var responseJson = response.jsonBody();
        assertThat(responseJson.getString("name"), is("Circ Desk 11"));
        assertThat(responseJson.getJsonObject("holdShelfExpiryPeriod").getInteger("duration"), is(3));
        ctx.completeNow();
      }));
  }

  @Test
  void cannotCreateServicePointWithoutPickupLocationButWithHoldShelfExpiryPeriod(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var servicePoint = new JsonObject()
      .put("name", "Circ Desk 101")
      .put("code", "cd101")
      .put("discoveryDisplayName", "Circulation Desk 101 -- Hallway")
      .put("pickupLocation", null)
      .put("holdShelfExpiryPeriod", JsonObject.mapFrom(createHoldShelfExpiryPeriod()));

    doPost(client, resourceUrl(), servicePoint)
      .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(response -> {
        var responseJson = response.jsonBody();
        var errorsArray = responseJson.getJsonArray("errors");
        assertThat(errorsArray.size(), is(1));
        assertThat(errorsArray.getJsonObject(0).getString("message"),
          is(SERVICE_POINT_CREATE_ERR_MSG_WITHOUT_BEING_PICKUP_LOC));
        ctx.completeNow();
      }));
  }

  @Test
  void cannotCreateServicePointWithoutHoldShelfExpiryPeriod(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var servicePoint = new JsonObject()
      .put("name", "Circ Desk 1")
      .put("code", "cd1")
      .put("discoveryDisplayName", "Circulation Desk -- Hallway")
      .put("pickupLocation", true)
      .put("holdShelfExpiryPeriod", null);

    doPost(client, resourceUrl(), servicePoint)
      .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(response -> {
        var responseJson = response.jsonBody();
        var errorsArray = responseJson.getJsonArray("errors");
        assertThat(errorsArray.size(), is(1));
        assertThat(errorsArray.getJsonObject(0).getString("message"),
          is(SERVICE_POINT_CREATE_ERR_MSG_WITHOUT_HOLD_EXPIRY));
        ctx.completeNow();
      }));
  }

  @Test
  void cannotCreateServicePointWithoutBeingPickupLocation(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var servicePoint = new JsonObject()
      .put("name", "Circ Desk 1")
      .put("code", "cd1")
      .put("discoveryDisplayName", "Circulation Desk -- Hallway")
      .put("pickupLocation", false)
      .put("holdShelfExpiryPeriod", JsonObject.mapFrom(createHoldShelfExpiryPeriod()));

    doPost(client, resourceUrl(), servicePoint)
      .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(response -> {
        var responseJson = response.jsonBody();
        var errorsArray = responseJson.getJsonArray("errors");
        assertThat(errorsArray.size(), is(1));
        assertThat(errorsArray.getJsonObject(0).getString("message"),
          is(SERVICE_POINT_CREATE_ERR_MSG_WITHOUT_BEING_PICKUP_LOC));
        ctx.completeNow();
      }));
  }

  @Test
  void cannotCreateServicePointWithoutValidHoldShelfExpiryPeriod(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var servicePoint = new JsonObject()
      .put("name", "Circ Desk 1")
      .put("code", "cd1")
      .put("discoveryDisplayName", "Circulation Desk -- Hallway")
      .put("pickupLocation", true)
      .put("holdShelfExpiryPeriod", new JsonObject()
        .put("duration", 20)
        .put("intervalId", null));

    doPost(client, resourceUrl(), servicePoint)
      .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(response -> ctx.completeNow()));
  }

  @Test
  void canUpdateServicePointWithHoldShelfExpiryPeriodWhenThereWasNoHoldShelfExpiryAndNotBeingPickupLocation(
    Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var id = UUID.randomUUID();

    // Create a service point without holdShelfExpiryPeriod and with pickupLocation false
    var initialServicePoint = new JsonObject()
      .put("id", id.toString())
      .put("name", "Circ Desk 1")
      .put("code", "cd1")
      .put("discoveryDisplayName", "Circulation Desk -- Hallway")
      .put("pickupLocation", false)
      .put("holdShelfExpiryPeriod", null);

    // Update request: add holdShelfExpiryPeriod and set pickupLocation true
    var updateRequest = new JsonObject()
      .put("id", id.toString())
      .put("name", "Circ Desk 2")
      .put("code", "cd2")
      .put("discoveryDisplayName", "Circulation Desk -- Basement")
      .put("pickupLocation", true)
      .put("holdShelfExpiryPeriod",
        new JsonObject(Json.encode(createHoldShelfExpiryPeriod(5, IntervalId.WEEKS))));

    doPost(client, resourceUrl(), initialServicePoint)
      .onComplete(verifyStatus(ctx, HTTP_CREATED))
      .compose(resp -> doPut(client, resourceUrl() + "/" + id, updateRequest))
      .onComplete(verifyStatus(ctx, HTTP_NO_CONTENT))
      .compose(resp -> doGet(client, resourceUrl() + "/" + id))
      .onComplete(ctx.succeeding(getResponse -> {
        JsonObject responseJson = getResponse.jsonBody();
        assertThat(responseJson.getString("id"), is(id.toString()));
        assertThat(responseJson.getString("name"), is("Circ Desk 2"));
        assertThat(responseJson.getString("code"), is("cd2"));
        assertThat(responseJson.getBoolean("pickupLocation"), is(true));

        JsonObject holdShelf = responseJson.getJsonObject("holdShelfExpiryPeriod");
        assertThat(holdShelf.getInteger("duration"), is(5));
        assertThat(holdShelf.getString("intervalId"), is(IntervalId.WEEKS.toString()));
        ctx.completeNow();
      }));
  }

  @Test
  void canUpdateServicePointWithHoldShelfExpiryPeriodAndHoldShelfCloseLibraryDateManagementAnd(
    Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var id = UUID.randomUUID();

    // Create a service point without holdShelfExpiryPeriod and with pickupLocation false
    var initialServicePoint = new JsonObject()
      .put("id", id.toString())
      .put("name", "Circ Desk 1")
      .put("code", "cd1")
      .put("discoveryDisplayName", "Circulation Desk -- Hallway")
      .put("pickupLocation", false)
      .put("holdShelfExpiryPeriod", null);

    // Update request: add holdShelfExpiryPeriod, holdShelfClosedLibraryDateManagement and set pickupLocation true
    var updateRequest = new JsonObject()
      .put("id", id.toString())
      .put("name", "Circ Desk 2")
      .put("code", "cd2")
      .put("discoveryDisplayName", "Circulation Desk -- Basement")
      .put("pickupLocation", true)
      .put("holdShelfClosedLibraryDateManagement", MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS.value())
      .put("holdShelfExpiryPeriod", JsonObject.mapFrom(createHoldShelfExpiryPeriod(5, IntervalId.WEEKS)));

    doPost(client, resourceUrl(), initialServicePoint)
      .onComplete(verifyStatus(ctx, HTTP_CREATED))
      .compose(resp -> doPut(client, resourceUrl() + "/" + id, updateRequest))
      .onComplete(verifyStatus(ctx, HTTP_NO_CONTENT))
      .compose(resp -> doGet(client, resourceUrl() + "/" + id))
      .onComplete(ctx.succeeding(response -> {
        JsonObject responseJson = response.jsonBody();
        assertThat(responseJson.getString("id"), is(id.toString()));
        assertThat(responseJson.getString("code"), is("cd2"));
        assertThat(responseJson.getString("name"), is("Circ Desk 2"));
        assertThat(responseJson.getBoolean("pickupLocation"), is(true));
        assertThat(responseJson.getString("holdShelfClosedLibraryDateManagement"),
          is(MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS.value()));

        JsonObject holdShelfExpiry = responseJson.getJsonObject("holdShelfExpiryPeriod");
        assertThat(holdShelfExpiry.getInteger("duration"), is(5));
        assertThat(holdShelfExpiry.getString("intervalId"), is(IntervalId.WEEKS.toString()));
        ctx.completeNow();
      }));
  }

  @Test
  void canUpdateServicePointWithoutHoldShelfExpiryPeriodAndWithoutBeingPickupLocationWhenNeitherWasTheCase(
    Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var id = UUID.randomUUID();
    var initial = new JsonObject()
      .put("id", id.toString())
      .put("name", "Circ Desk 1")
      .put("code", "cd1")
      .put("discoveryDisplayName", "Circulation Desk -- Hallway")
      .put("pickupLocation", false)
      .put("holdShelfExpiryPeriod", null);
    var update = new JsonObject()
      .put("id", id.toString())
      .put("name", "Circ Desk 2")
      .put("code", "cd2")
      .put("discoveryDisplayName", "Circulation Desk -- Basement")
      .put("pickupLocation", false);

    doPost(client, resourceUrl(), initial)
      .onComplete(verifyStatus(ctx, HTTP_CREATED))
      .compose(resp -> doPut(client, resourceUrl() + "/" + id, update))
      .onComplete(verifyStatus(ctx, HTTP_NO_CONTENT))
      .compose(resp -> doGet(client, resourceUrl() + "/" + id))
      .onComplete(ctx.succeeding(getResp -> {
        var json = getResp.jsonBody();
        assertThat(json.getString("id"), is(id.toString()));
        assertThat(json.getString("code"), is("cd2"));
        assertThat(json.getString("name"), is("Circ Desk 2"));
        assertThat(json.getBoolean("pickupLocation"), is(false));
        ctx.completeNow();
      }));

    awaitAtMost().until(() -> KAFKA_CONSUMER.getMessagesForServicePoint(id.toString()),
      hasSize(2));
  }

  @Test
  void cannotUpdateServicePointWithoutHoldShelfExpiryPeriodWhenItOriginallyExisted(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var id = UUID.randomUUID();
    var initial = new JsonObject()
      .put("id", id.toString())
      .put("name", "Circ Desk 1")
      .put("code", "cd1")
      .put("discoveryDisplayName", "Circulation Desk -- Hallway")
      .put("pickupLocation", true)
      .put("holdShelfExpiryPeriod", JsonObject.mapFrom(createHoldShelfExpiryPeriod()));
    var update = new JsonObject()
      .put("id", id.toString())
      .put("name", "Circ Desk 2")
      .put("code", "cd2")
      .put("discoveryDisplayName", "Circulation Desk -- Basement")
      .put("pickupLocation", true)
      .put("holdShelfExpiryPeriod", null);

    doPost(client, resourceUrl(), initial)
      .onComplete(verifyStatus(ctx, HTTP_CREATED))
      .compose(resp -> doPut(client, resourceUrl() + "/" + id, update))
      .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(resp -> {
        JsonObject json = resp.jsonBody();
        JsonArray errors = json.getJsonArray("errors");
        assertThat(errors.size(), is(1));
        assertThat(errors.getJsonObject(0).getString("message"),
          is(SERVICE_POINT_CREATE_ERR_MSG_WITHOUT_HOLD_EXPIRY));
        servicePointEventMessageChecks.updatedMessageWasNotPublished(initial);
        ctx.completeNow();
      }));
  }

  @Test
  void cannotUpdateServicePointWithoutHoldShelfExpiryPeriodWhenItNeverExisted(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var id = UUID.randomUUID();
    var initial = new JsonObject()
      .put("id", id.toString())
      .put("name", "Circ Desk 102")
      .put("code", "cd102")
      .put("discoveryDisplayName", "Circulation Desk -- Hallway")
      .put("pickupLocation", false)
      .put("holdShelfExpiryPeriod", null);
    var update = new JsonObject()
      .put("id", id.toString())
      .put("name", "Circ Desk 102")
      .put("code", "cd102")
      .put("discoveryDisplayName", "Circulation Desk -- Basement")
      .put("pickupLocation", true);

    doPost(client, resourceUrl(), initial)
      .onComplete(verifyStatus(ctx, HTTP_CREATED))
      .compose(resp -> doPut(client, resourceUrl() + "/" + id, update))
      .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(resp -> {
        JsonObject json = resp.jsonBody();
        JsonArray errors = json.getJsonArray("errors");
        assertThat(errors.size(), is(1));
        assertThat(errors.getJsonObject(0).getString("message"),
          is(SERVICE_POINT_CREATE_ERR_MSG_WITHOUT_HOLD_EXPIRY));
        servicePointEventMessageChecks.updatedMessageWasNotPublished(initial);
        ctx.completeNow();
      }));
  }

  @Test
  void cannotUpdateServicePointWithoutBeingPickupLocationAndHoldShelfExpiryPeriodAlreadyExisted(
    Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var id = UUID.randomUUID();
    var defaultExpiry = createHoldShelfExpiryPeriod();
    var initial = new JsonObject()
      .put("id", id.toString())
      .put("name", "Circ Desk 1")
      .put("code", "cd1")
      .put("discoveryDisplayName", "Circulation Desk -- Hallway")
      .put("pickupLocation", true)
      .put("holdShelfExpiryPeriod", JsonObject.mapFrom(defaultExpiry));
    var update = new JsonObject()
      .put("id", id.toString())
      .put("name", "Circ Desk 2")
      .put("code", "cd2")
      .put("discoveryDisplayName", "Circulation Desk -- Basement")
      .put("pickupLocation", false)
      .put("holdShelfExpiryPeriod", new JsonObject(Json.encode(defaultExpiry)));

    doPost(client, resourceUrl(), initial)
      .onComplete(verifyStatus(ctx, HTTP_CREATED))
      .compose(resp -> doPut(client, resourceUrl() + "/" + id, update))
      .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(resp -> {
        JsonObject json = resp.jsonBody();
        JsonArray errors = json.getJsonArray("errors");
        assertThat(errors.size(), is(1));
        assertThat(errors.getJsonObject(0).getString("message"),
          is(SERVICE_POINT_CREATE_ERR_MSG_WITHOUT_BEING_PICKUP_LOC));
        servicePointEventMessageChecks.updatedMessageWasNotPublished(initial);
        ctx.completeNow();
      }));
  }

  @Test
  void cannotUpdateServicePointWithoutBeingPickupLocationWhileAddingHoldShelfExpiryPeriod(
    Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var id = UUID.randomUUID();
    var defaultExpiry = createHoldShelfExpiryPeriod();
    var initial = new JsonObject()
      .put("id", id.toString())
      .put("name", "Circ Desk 1")
      .put("code", "cd1")
      .put("discoveryDisplayName", "Circulation Desk -- Hallway")
      .put("pickupLocation", false)
      .put("holdShelfExpiryPeriod", null);
    var update = new JsonObject()
      .put("id", id.toString())
      .put("name", "Circ Desk 2")
      .put("code", "cd2")
      .put("discoveryDisplayName", "Circulation Desk -- Basement")
      .put("pickupLocation", false)
      .put("holdShelfExpiryPeriod", new JsonObject(Json.encode(defaultExpiry)));

    doPost(client, resourceUrl(), initial)
      .onComplete(verifyStatus(ctx, HTTP_CREATED))
      .compose(resp -> doPut(client, resourceUrl() + "/" + id, update))
      .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(resp -> {
        JsonObject json = resp.jsonBody();
        JsonArray errors = json.getJsonArray("errors");
        assertThat(errors.size(), is(1));
        assertThat(errors.getJsonObject(0).getString("message"),
          is(SERVICE_POINT_CREATE_ERR_MSG_WITHOUT_BEING_PICKUP_LOC));
        servicePointEventMessageChecks.updatedMessageWasNotPublished(initial);
        ctx.completeNow();
      }));
  }

  @Test
  void canUpdateServicePointWithHoldShelfExpiryPeriodAndBeingPickupLocationWhenBothWasPresent(
    Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var id = UUID.randomUUID();
    var initial = new JsonObject()
      .put("id", id.toString())
      .put("name", "Circ Desk 1")
      .put("code", "cd1")
      .put("discoveryDisplayName", "Circulation Desk -- Hallway")
      .put("pickupLocation", true)
      .put("holdShelfExpiryPeriod", JsonObject.mapFrom(createHoldShelfExpiryPeriod()));
    var update = new JsonObject()
      .put("id", id.toString())
      .put("name", "Circ Desk 2")
      .put("code", "cd2")
      .put("discoveryDisplayName", "Circulation Desk -- Basement")
      .put("pickupLocation", true)
      .put("holdShelfExpiryPeriod",
        new JsonObject(Json.encode(createHoldShelfExpiryPeriod(5, IntervalId.WEEKS))));

    doPost(client, resourceUrl(), initial)
      .onComplete(verifyStatus(ctx, HTTP_CREATED))
      .compose(resp -> doPut(client, resourceUrl() + "/" + id, update))
      .onComplete(verifyStatus(ctx, HTTP_NO_CONTENT))
      .compose(resp -> doGet(client, resourceUrl() + "/" + id))
      .onComplete(ctx.succeeding(getResp -> {
        JsonObject json = getResp.jsonBody();
        assertThat(json.getString("id"), is(id.toString()));
        assertThat(json.getString("name"), is("Circ Desk 2"));
        assertThat(json.getString("code"), is("cd2"));
        assertThat(json.getBoolean("pickupLocation"), is(true));
        JsonObject expiry = json.getJsonObject("holdShelfExpiryPeriod");
        assertThat(expiry.getInteger("duration"), is(5));
        assertThat(expiry.getString("intervalId"), is(IntervalId.WEEKS.toString()));
        ctx.completeNow();
      }));

    awaitAtMost().until(() -> KAFKA_CONSUMER.getMessagesForServicePoint(id.toString()),
      hasSize(2));
  }

  @Test
  void canUpdateServicePointWithoutHoldShelfExpiryPeriodAndBeingPickupLocationWhenBothWasPresent(
    Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var id = UUID.randomUUID();
    var initial = new JsonObject()
      .put("id", id.toString())
      .put("name", "Circ Desk 1")
      .put("code", "cd1")
      .put("discoveryDisplayName", "Circulation Desk -- Hallway")
      .put("pickupLocation", true)
      .put("holdShelfExpiryPeriod", JsonObject.mapFrom(createHoldShelfExpiryPeriod()));
    var update = new JsonObject()
      .put("id", id.toString())
      .put("name", "Circ Desk 2")
      .put("code", "cd2")
      .put("discoveryDisplayName", "Circulation Desk -- Basement")
      .put("pickupLocation", false);

    doPost(client, resourceUrl(), initial)
      .onComplete(verifyStatus(ctx, HTTP_CREATED))
      .compose(resp -> doPut(client, resourceUrl() + "/" + id, update))
      .onComplete(verifyStatus(ctx, HTTP_NO_CONTENT))
      .compose(resp -> doGet(client, resourceUrl() + "/" + id))
      .onComplete(ctx.succeeding(getResp -> {
        JsonObject json = getResp.jsonBody();
        assertThat(json.getString("id"), is(id.toString()));
        assertThat(json.getString("code"), is("cd2"));
        assertThat(json.getString("name"), is("Circ Desk 2"));
        assertThat(json.getBoolean("pickupLocation"), is(false));
        ctx.completeNow();
      }));

    awaitAtMost().until(() -> KAFKA_CONSUMER.getMessagesForServicePoint(id.toString()),
      hasSize(2));
  }

  @Test
  void canUpdateServicePointWithDefaultCheckInActionForUseAtLocation(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var id = UUID.randomUUID();
    var initial = new JsonObject()
      .put("id", id.toString())
      .put("name", "Circ Desk 1")
      .put("code", "cd1")
      .put("discoveryDisplayName", "Circulation Desk -- Hallway")
      .put("shelvingLagTime", 20)
      .put("pickupLocation", false)
      .put("holdShelfExpiryPeriod", null);
    var update = new JsonObject()
      .put("id", id.toString())
      .put("name", "Circ Desk 2")
      .put("code", "cd2")
      .put("discoveryDisplayName", "Circulation Desk -- Basement")
      .put("defaultCheckInActionForUseAtLocation", KEEP_ON_HOLD_SHELF.value());

    doPost(client, resourceUrl(), initial)
      .onComplete(verifyStatus(ctx, HTTP_CREATED))
      .compose(resp -> doPut(client, resourceUrl() + "/" + id, update))
      .onComplete(verifyStatus(ctx, HTTP_NO_CONTENT))
      .compose(resp -> doGet(client, resourceUrl() + "/" + id))
      .onComplete(ctx.succeeding(getResp -> {
        JsonObject json = getResp.jsonBody();
        assertThat(json.getString("id"), is(id.toString()));
        assertThat(json.getString("code"), is("cd2"));
        assertThat(json.getString("name"), is("Circ Desk 2"));
        assertThat(json.getString("defaultCheckInActionForUseAtLocation"),
          is(KEEP_ON_HOLD_SHELF.value()));
        ctx.completeNow();
      }));

    awaitAtMost().until(() -> KAFKA_CONSUMER.getMessagesForServicePoint(id.toString()),
      hasSize(2));
  }

  @Test
  void canCreateServicePointWithStaffSlips(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    String uuidTrue = UUID.randomUUID().toString();
    String uuidFalse = UUID.randomUUID().toString();
    var staffSlips = new JsonArray()
      .add(new JsonObject().put("id", uuidTrue).put("printByDefault", true))
      .add(new JsonObject().put("id", uuidFalse).put("printByDefault", false));
    var servicePoint = new JsonObject()
      .put("name", "Circ Desk 1")
      .put("code", "cd1")
      .put("discoveryDisplayName", "Circulation Desk -- Hallway")
      .put("pickupLocation", true)
      .put("holdShelfExpiryPeriod", JsonObject.mapFrom(createHoldShelfExpiryPeriod()))
      .put("staffSlips", staffSlips);

    doPost(client, resourceUrl(), servicePoint)
      .onComplete(verifyStatus(ctx, HTTP_CREATED))
      .onComplete(ctx.succeeding(response -> {
        JsonObject json = response.jsonBody();
        assertThat(json.getString("id"), notNullValue());
        assertThat(json.getString("code"), is("cd1"));
        assertThat(json.getString("name"), is("Circ Desk 1"));
        JsonArray slips = json.getJsonArray("staffSlips");
        assertThat(slips.getJsonObject(0).getString("id"), is(uuidTrue));
        assertThat(slips.getJsonObject(0).getBoolean("printByDefault"), is(true));
        assertThat(slips.getJsonObject(1).getString("id"), is(uuidFalse));
        assertThat(slips.getJsonObject(1).getBoolean("printByDefault"), is(false));
        ctx.completeNow();
      }));
  }

  @Test
  void cannotCreateServicePointWithStaffSlipsMissingFields(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    String uuid = UUID.randomUUID().toString();
    var staffSlips = new JsonArray()
      .add(new JsonObject().put("id", uuid)); // Missing printByDefault
    var servicePoint = new JsonObject()
      .put("name", "Circ Desk 1")
      .put("code", "cd1")
      .put("discoveryDisplayName", "Circulation Desk -- Hallway")
      .put("pickupLocation", true)
      .put("holdShelfExpiryPeriod", JsonObject.mapFrom(createHoldShelfExpiryPeriod()))
      .put("staffSlips", staffSlips);

    doPost(client, resourceUrl(), servicePoint)
      .onComplete(verifyStatus(ctx, HTTP_UNPROCESSABLE_ENTITY))
      .onComplete(ctx.succeeding(response -> ctx.completeNow()));
  }

  @ParameterizedTest
  @CsvSource({
    "false, ''", // no query parameters
    "false, ?query=cql.allRecords=1%20sortby%20name&limit=1000",
    "false, ?includeRoutingServicePoints=false",
    "true,  ?includeRoutingServicePoints=true",
    "false, ?includeRoutingServicePoints=false&query=cql.allRecords=1%20sortby%20name&limit=1000",
    "true,  ?includeRoutingServicePoints=true&query=cql.allRecords=1%20sortby%20name&limit=1000"
  })
  void ecsRequestRoutingServicePointsAreReturnedOnlyWhenExplicitlyRequested(
    boolean shouldReturnRoutingServicePoints, String queryParameters, Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var spId1 = UUID.randomUUID();
    var spId2 = UUID.randomUUID();
    var spId3 = UUID.randomUUID();

    var sp1 = new JsonObject()
      .put("id", spId1.toString())
      .put("name", "Circ Desk 1")
      .put("code", "cd1")
      .put("discoveryDisplayName", "Circulation Desk 1")
      .put("pickupLocation", true)
      .put("holdShelfExpiryPeriod", JsonObject.mapFrom(createHoldShelfExpiryPeriod()))
      .put("ecsRequestRouting", false);
    var sp2 = new JsonObject()
      .put("id", spId2.toString())
      .put("name", "Circ Desk 2")
      .put("code", "cd2")
      .put("discoveryDisplayName", "Circulation Desk 2")
      .put("pickupLocation", true)
      .put("holdShelfExpiryPeriod", JsonObject.mapFrom(createHoldShelfExpiryPeriod()))
      .put("ecsRequestRouting", false);
    var sp3 = new JsonObject()
      .put("id", spId3.toString())
      .put("name", "Circ Desk 3")
      .put("code", "cd3")
      .put("discoveryDisplayName", "Circulation Desk 3")
      .put("pickupLocation", true)
      .put("holdShelfExpiryPeriod", JsonObject.mapFrom(createHoldShelfExpiryPeriod()))
      .put("ecsRequestRouting", true);

    doPost(client, resourceUrl(), sp1)
      .compose(resp -> doPost(client, resourceUrl(), sp2))
      .compose(resp -> doPost(client, resourceUrl(), sp3))
      .compose(resp -> doGet(client, resourceUrl() + queryParameters))
      .onComplete(ctx.succeeding(getResp -> {
        JsonArray arr = getResp.jsonBody().getJsonArray("servicepoints");
        var servicePointIds = arr.stream()
          .map(o -> ((JsonObject) o).getString("id"))
          .toList();
        assertThat(servicePointIds, hasItems(spId1.toString(), spId2.toString()));
        if (shouldReturnRoutingServicePoints) {
          assertThat(servicePointIds, hasItem(spId3.toString()));
          assertThat(servicePointIds, hasSize(3));
        } else {
          assertThat(servicePointIds, hasSize(2));
        }
        ctx.completeNow();
      }));
  }

  @Test
  void canUpdateServicePointWithStaffSlips(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var id = UUID.randomUUID();
    var staffSlipId = UUID.randomUUID().toString();
    var initialStaffSlips = new JsonArray()
      .add(new JsonObject().put("id", staffSlipId).put("printByDefault", true));
    var initialServicePoint = new JsonObject()
      .put("id", id.toString())
      .put("name", "Circ Desk 1")
      .put("code", "cd1")
      .put("discoveryDisplayName", "Circulation Desk -- Hallway")
      .put("pickupLocation", true)
      .put("holdShelfExpiryPeriod", JsonObject.mapFrom(createHoldShelfExpiryPeriod()))
      .put("staffSlips", initialStaffSlips);
    var updateServicePoint = new JsonObject()
      .put("id", id.toString())
      .put("name", "Circ Desk 2")
      .put("code", "cd2")
      .put("discoveryDisplayName", "Circulation Desk -- Basement")
      .put("pickupLocation", false)
      .put("staffSlips", new JsonArray()
        .add(new JsonObject().put("id", staffSlipId).put("printByDefault", false)));

    doPost(client, resourceUrl(), initialServicePoint)
      .compose(postResp -> doPut(client, resourceUrl() + "/" + id, updateServicePoint))
      .compose(putResp -> doGet(client, resourceUrl() + "/" + id))
      .onComplete(ctx.succeeding(getResp -> {
        JsonObject json = getResp.jsonBody();
        assertThat(json.getString("id"), is(id.toString()));
        assertThat(json.getString("name"), is("Circ Desk 2"));
        assertThat(json.getString("code"), is("cd2"));
        assertThat(json.getBoolean("pickupLocation"), is(false));
        JsonArray slips = json.getJsonArray("staffSlips");
        assertThat(slips.getJsonObject(0).getString("id"), is(staffSlipId));
        assertThat(slips.getJsonObject(0).getBoolean("printByDefault"), is(false));
        ctx.completeNow();
      }));
  }

  @Test
  void canFilterByPickupLocation(Vertx vertx, VertxTestContext ctx) {
    var client = vertx.createHttpClient();
    var id = UUID.randomUUID();
    var servicePoint1 = new JsonObject()
      .put("id", id.toString())
      .put("name", "Circ Desk 1")
      .put("code", "cd1")
      .put("discoveryDisplayName", "Circulation Desk -- Hallway")
      .put("pickupLocation", true)
      .put("holdShelfExpiryPeriod", JsonObject.mapFrom(createHoldShelfExpiryPeriod()));
    var servicePoint2 = new JsonObject()
      .put("id", null)
      .put("name", "Circ Desk 2")
      .put("code", "cd2")
      .put("discoveryDisplayName", "Circulation Desk -- Basement")
      .put("pickupLocation", false)
      .put("holdShelfExpiryPeriod", JsonObject.mapFrom(createHoldShelfExpiryPeriod()));

    doPost(client, resourceUrl(), servicePoint1)
      .compose(postResp -> doPost(client, resourceUrl(), servicePoint2))
      .compose(putResp -> doGet(client, resourceUrl()))
      .onComplete(ctx.succeeding(getResp -> {
        var servicepoints = getResp.jsonBody().getJsonArray("servicepoints").stream()
          .map(JsonObject.class::cast)
          .toList();
        assertThat(servicepoints.size(), is(1));
        assertThat(servicepoints.getFirst().getString("id"),
          is(id.toString()));
        ctx.completeNow();
      }));
  }

  public static HoldShelfExpiryPeriod createHoldShelfExpiryPeriod(int duration, IntervalId intervalId) {
    return new HoldShelfExpiryPeriod()
      .withDuration(duration)
      .withIntervalId(intervalId);
  }

  public static HoldShelfExpiryPeriod createHoldShelfExpiryPeriod() {
    return createHoldShelfExpiryPeriod(2, IntervalId.DAYS);
  }
}
