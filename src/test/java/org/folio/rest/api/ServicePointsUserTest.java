package org.folio.rest.api;

import static org.folio.rest.api.ServicePointTest.createHoldShelfExpiryPeriod;
import static org.folio.rest.support.http.InterfaceUrls.servicePointsUrl;
import static org.folio.rest.support.http.InterfaceUrls.servicePointsUsersUrl;
import static org.folio.util.StringUtil.urlEncode;
import static org.folio.utility.LocationUtility.createServicePoint;
import static org.folio.utility.RestUtility.send;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.SneakyThrows;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.junit.Before;
import org.junit.Test;

public class ServicePointsUserTest extends TestBase {
  private static final String SUPPORTED_CONTENT_TYPE_JSON_DEF = "application/json";

  public static Response createServicePointUser(UUID id, UUID userId,
                                                List<UUID> servicePointsIds, UUID defaultServicePointId)
    throws InterruptedException, ExecutionException, TimeoutException {

    JsonObject request = new JsonObject();
    if (id != null) {
      request.put("id", id.toString());
    }

    request.put("userId", userId.toString());

    if (defaultServicePointId != null) {
      request.put("defaultServicePointId", defaultServicePointId.toString());
    }

    if (servicePointsIds != null && !servicePointsIds.isEmpty()) {
      JsonArray spIds = new JsonArray();
      for (UUID uuid : servicePointsIds) {
        spIds.add(uuid.toString());
      }
      request.put("servicePointsIds", spIds);
    }

    CompletableFuture<Response> createServicePointUser = new CompletableFuture<>();

    send(servicePointsUsersUrl(""), HttpMethod.POST, request.toString(),
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(createServicePointUser));

    return createServicePointUser.get(TIMEOUT, TimeUnit.SECONDS);
  }

  public static Response getServicePointUserById(UUID id)
    throws InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    send(servicePointsUsersUrl("/" + id.toString()), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(getCompleted));

    return getCompleted.get(TIMEOUT, TimeUnit.SECONDS);
  }

  public static Response updateServicePointUserById(UUID id, JsonObject entity)
    throws InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    send(servicePointsUsersUrl("/" + id.toString()), HttpMethod.PUT, entity.toString(),
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(putCompleted));

    return putCompleted.get(TIMEOUT, TimeUnit.SECONDS);
  }

  public static Response deleteServicePointUserById(UUID id) throws InterruptedException,
    ExecutionException, TimeoutException {

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();

    send(servicePointsUsersUrl("/" + id.toString()), HttpMethod.DELETE, null,
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(deleteCompleted));

    return deleteCompleted.get(TIMEOUT, TimeUnit.SECONDS);
  }

  public static Response getServicePointUsers(String query) throws InterruptedException,
    ExecutionException, TimeoutException {

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    final URL url = query != null
                    ? servicePointsUsersUrl("?query=" + urlEncode(query))
                    : servicePointsUsersUrl("");

    send(url, HttpMethod.GET, null, SUPPORTED_CONTENT_TYPE_JSON_DEF,
      ResponseHandler.json(getCompleted));

    return getCompleted.get(TIMEOUT, TimeUnit.SECONDS);
  }

  @SneakyThrows
  @Before
  public void beforeEach() {
    StorageTestSuite.deleteAll(servicePointsUsersUrl(""));
    StorageTestSuite.deleteAll(servicePointsUrl(""));

    removeAllEvents();
  }

  //BEGIN TESTS
  @Test
  public void emptyTest() {
    assertThat(true, is(true));
  }

  @Test
  public void canCreateServicePointUser() throws InterruptedException, ExecutionException,
    TimeoutException {

    Response response = createServicePointUser(UUID.randomUUID(), UUID.randomUUID(),
      null, null);
    assertThat(response.getStatusCode(), is(201));
  }

  @Test
  public void canRetrieveCreatedSpu() throws InterruptedException, ExecutionException,
    TimeoutException {

    Response postResponse = createServicePointUser(UUID.randomUUID(),
      UUID.randomUUID(), null, null);
    String id = postResponse.getJson().getString("id");
    Response getResponse = getServicePointUserById(UUID.fromString(id));
    assertThat(getResponse.getStatusCode(), is(200));
    assertThat(getResponse.getJson().getString("id"), is(id));
  }

  @Test
  public void cannotCreateSpuWithNonExistantDefaultSp() throws InterruptedException,
    ExecutionException, TimeoutException {

    Response response = createServicePointUser(UUID.randomUUID(),
      UUID.randomUUID(), null, UUID.randomUUID());
    assertThat(response.getStatusCode(), is(422));
  }

  @Test
  public void canCreateSpuWithExistingDefaultSp()
    throws MalformedURLException, InterruptedException, ExecutionException,
    TimeoutException {
    UUID spId = UUID.randomUUID();
    createServicePoint(spId, "Circ Desk 1", "cd1",
      "Circulation Desk -- Hallway", null,
      20, true, createHoldShelfExpiryPeriod());
    Response response = createServicePointUser(UUID.randomUUID(),
      UUID.randomUUID(), null, spId);
    assertThat(response.getStatusCode(), is(201));
  }

  @Test
  public void canGetSpus() throws InterruptedException, ExecutionException, TimeoutException {
    createServicePointUser(null, UUID.randomUUID(), null, null);
    createServicePointUser(null, UUID.randomUUID(), null, null);
    Response response = getServicePointUsers(null);
    assertThat(response.getJson().getInteger("totalRecords"), is(2));
  }

  //END TESTS

  @Test
  public void canDeleteAllSpus() throws InterruptedException, ExecutionException, TimeoutException {
    createServicePointUser(null, UUID.randomUUID(), null, null);
    createServicePointUser(null, UUID.randomUUID(), null, null);
    StorageTestSuite.deleteAll(servicePointsUsersUrl(""));
    Response response = getServicePointUsers(null);
    assertThat(response.getJson().getInteger("totalRecords"), is(0));
  }

  @Test
  public void canQuerySpus() throws MalformedURLException, InterruptedException, ExecutionException,
    TimeoutException {
    UUID spId1 = UUID.randomUUID();
    UUID spId2 = UUID.randomUUID();
    UUID spId3 = UUID.randomUUID();
    createServicePoint(spId1, "Circ Desk 1", "cd1",
      "Circulation Desk -- Hallway", null, 20, true, createHoldShelfExpiryPeriod());
    createServicePoint(spId2, "Circ Desk 2", "cd2",
      "Circulation Desk -- Stairs", null, 20, true, createHoldShelfExpiryPeriod());
    createServicePoint(spId3, "Circ Desk 3", "cd3",
      "Circulation Desk -- Basement", null, 20, true, createHoldShelfExpiryPeriod());
    List<UUID> spList1 = new ArrayList<>();
    spList1.add(spId1);
    spList1.add(spId2);
    List<UUID> spList2 = new ArrayList<>();
    spList2.add(spId2);
    spList2.add(spId3);
    UUID spuId = UUID.randomUUID();
    createServicePointUser(null, UUID.randomUUID(), spList1, spId1);
    createServicePointUser(spuId, UUID.randomUUID(), spList2, spId2);
    //Response response = getServicePointUsers(null);
    Response response = getServicePointUsers(String.format("servicePointsIds=%s", spId3));
    System.out.println(response.toString());
    assertThat(response.getJson().getInteger("totalRecords"), is(1));
    assertThat(response.getJson().getJsonArray("servicePointsUsers")
      .getJsonObject(0).getString("id"), is(spuId.toString()));
  }

  @Test
  public void canUpdateServicePointUser() throws InterruptedException, ExecutionException,
    TimeoutException {

    UUID userId1 = UUID.randomUUID();
    UUID userId2 = UUID.randomUUID();
    UUID spuId = UUID.randomUUID();
    createServicePointUser(spuId, userId1, null, null);
    JsonObject entity = new JsonObject()
      .put("id", spuId.toString())
      .put("userId", userId2.toString());
    Response response = updateServicePointUserById(spuId, entity);
    assertThat(response.getStatusCode(), is(204));
    Response getResponse = getServicePointUserById(spuId);
    assertThat(getResponse.getJson().getString("userId"), is(userId2.toString()));
  }

  @Test
  public void canDeleteServicePointUser() throws InterruptedException, ExecutionException,
    TimeoutException {

    UUID spuId = UUID.randomUUID();
    createServicePointUser(spuId, UUID.randomUUID(), null, null);
    Response getResponse = getServicePointUserById(spuId);
    assertThat(getResponse.getStatusCode(), is(200));
    deleteServicePointUserById(spuId);
    Response getResponseAgain = getServicePointUserById(spuId);
    assertThat(getResponseAgain.getStatusCode(), is(404));
  }

  @Test
  public void cannotCreateServicePointUserIfUserAlreadyExists() throws Exception {
    final var firstId = UUID.randomUUID();
    final var secondId = UUID.randomUUID();
    final var userId = UUID.randomUUID();
    final var servicePoints = List.of(UUID.randomUUID());

    final var firstCreateResponse = createServicePointUser(firstId, userId,
      servicePoints, null);
    assertThat(firstCreateResponse.getStatusCode(), is(201));

    final var secondCreateResponse = createServicePointUser(secondId, userId,
      servicePoints, null);

    assertThat(secondCreateResponse.getStatusCode(), is(422));
  }
}
