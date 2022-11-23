package org.folio.rest.api;

import static org.folio.rest.impl.ServicePointAPI.SERVICE_POINT_CREATE_ERR_MSG_WITHOUT_BEING_PICKUP_LOC;
import static org.folio.rest.impl.ServicePointAPI.SERVICE_POINT_CREATE_ERR_MSG_WITHOUT_HOLD_EXPIRY;
import static org.folio.rest.support.http.InterfaceUrls.servicePointsUrl;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import org.folio.rest.jaxrs.model.HoldShelfExpiryPeriod;
import org.folio.rest.jaxrs.model.Servicepoint;
import org.folio.rest.jaxrs.model.StaffSlip;
import org.folio.rest.support.AdditionalHttpStatusCodes;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.junit.Before;
import org.junit.Test;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import static org.folio.rest.support.http.InterfaceUrls.servicePointsUsersUrl;

/**
 *
 * @author kurt
 */
public class ServicePointTest extends TestBase{
  private static final String SUPPORTED_CONTENT_TYPE_JSON_DEF = "application/json";

  @Before
  public void beforeEach() {
    StorageTestSuite.deleteAll(servicePointsUsersUrl(""));
    StorageTestSuite.deleteAll(servicePointsUrl(""));
  }

  @Test
  public void canCreateServicePoint()
          throws InterruptedException,
          ExecutionException,
          TimeoutException,
          MalformedURLException {

    Response response = createServicePoint(null, "Circ Desk 1", "cd1",
        "Circulation Desk -- Hallway", null, 20,
        true, createHoldShelfExpiryPeriod());
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
    assertThat(response.getJson().getString("id"), notNullValue());
    assertThat(response.getJson().getString("code"), is("cd1"));
    assertThat(response.getJson().getString("name"), is("Circ Desk 1"));
  }

  @Test
  public void cannotCreateServicePointWithoutName()
          throws InterruptedException,
          ExecutionException,
          TimeoutException,
          MalformedURLException {
    Response response = createServicePoint(null, null, "cd1",
        "Circulation Desk -- Hallway", null,
        20, true, createHoldShelfExpiryPeriod());
    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void cannotCreateServicePointWithoutCode()
          throws InterruptedException,
          ExecutionException,
          TimeoutException,
          MalformedURLException {
    Response response = createServicePoint(null, "Circ Desk 103", null,
        "Circulation Desk -- Hallway", null,
        20, true, createHoldShelfExpiryPeriod());
    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void cannotCreateServicePointWithoutDDName()
          throws InterruptedException,
          ExecutionException,
          TimeoutException,
          MalformedURLException {
    Response response = createServicePoint(null, "Circ Desk 1", "cd1",
        null, null, 20, true, createHoldShelfExpiryPeriod());
    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void cannotCreateServicePointWithDuplicateName()
          throws InterruptedException,
          ExecutionException,
          TimeoutException,
          MalformedURLException {
    createServicePoint(null, "Circ Desk 1", "cd1",
        "Circulation Desk -- Hallway", null, 20, true, createHoldShelfExpiryPeriod());
    Response response = createServicePoint(null, "Circ Desk 1", "cd2",
        "Circulation Desk -- Bathroom", null, 20, true, createHoldShelfExpiryPeriod());
    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void canGetAServicePointById()
          throws InterruptedException,
          ExecutionException,
          TimeoutException,
          MalformedURLException {
    UUID id = UUID.randomUUID();
    createServicePoint(id, "Circ Desk 1", "cd1",
        "Circulation Desk -- Hallway", null, 20, true, createHoldShelfExpiryPeriod());
    Response response = getById(id);
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(response.getJson().getString("id"), is(id.toString()));
    assertThat(response.getJson().getString("code"), is("cd1"));
    assertThat(response.getJson().getString("name"), is("Circ Desk 1"));

    JsonObject holdShelfExpiryPeriod = response.getJson().getJsonObject("holdShelfExpiryPeriod");
    assertThat(holdShelfExpiryPeriod.getInteger("duration"), is (2));
    assertThat(holdShelfExpiryPeriod.getString("intervalId"), is (HoldShelfExpiryPeriod.IntervalId.DAYS.toString()));
  }

  @Test
  public void canGetMultipleServicePoints()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    createServicePoint(null, "Circ Desk 1", "cd1",
        "Circulation Desk -- Hallway", null, 20, true, createHoldShelfExpiryPeriod());
    createServicePoint(null, "Circ Desk 2", "cd2",
        "Circulation Desk -- Basement", null, 20, true, createHoldShelfExpiryPeriod());
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    send(servicePointsUrl("/"), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(getCompleted));
    Response getResponse = getCompleted.get(10, TimeUnit.SECONDS);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    JsonObject item = getResponse.getJson();
    assertThat(item.getInteger("totalRecords"), is(2));
  }

  @Test
  public void canQueryServicePoints()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    createServicePoint(null, "Circ Desk 1", "cd1",
        "Circulation Desk -- Hallway", null, 20, true, createHoldShelfExpiryPeriod());
    createServicePoint(null, "Circ Desk 2", "cd2",
        "Circulation Desk -- Basement", null, 20, true, createHoldShelfExpiryPeriod());
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    send(servicePointsUrl("/?query=code==cd1"), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(getCompleted));
    Response getResponse = getCompleted.get(10, TimeUnit.SECONDS);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    JsonObject item = getResponse.getJson();
    assertThat(item.getInteger("totalRecords"), is(1));
  }

  @Test
  public void canUpdateAServicePoint()
          throws InterruptedException,
          ExecutionException,
          TimeoutException,
          MalformedURLException {
    UUID id = UUID.randomUUID();
    createServicePoint(id, "Circ Desk 1", "cd1",
        "Circulation Desk -- Hallway", null, 20, true, createHoldShelfExpiryPeriod());
    JsonObject request = new JsonObject()
            .put("id", id.toString())
            .put("name", "Circ Desk 2")
            .put("code", "cd2")
            .put("discoveryDisplayName", "Circulation Desk -- Basement")
            .put("pickupLocation", false);
    CompletableFuture<Response> updated = new CompletableFuture<>();
    send(servicePointsUrl("/" + id.toString()), HttpMethod.PUT, request.encode(),
            SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(updated));
    Response updateResponse = updated.get(10, TimeUnit.SECONDS);
    assertThat(updateResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
    Response getResponse = getById(id);
    assertThat(getResponse.getJson().getString("id"), is(id.toString()));
    assertThat(getResponse.getJson().getString("code"), is("cd2"));
    assertThat(getResponse.getJson().getString("name"), is("Circ Desk 2")); //should fail
    assertThat(getResponse.getJson().getBoolean("pickupLocation"), is(false));
  }

  @Test
  public void canDeleteAServicePointById()
          throws InterruptedException,
          ExecutionException,
          TimeoutException,
          MalformedURLException {
    UUID id = UUID.randomUUID();
    createServicePoint(id, "Circ Desk 1", "cd1",
        "Circulation Desk -- Hallway", null, 20, true, createHoldShelfExpiryPeriod());
    CompletableFuture<Response> deleted = new CompletableFuture<>();
    send(servicePointsUrl("/" + id.toString()), HttpMethod.DELETE, null,
            SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(deleted));
    Response deleteResponse = deleted.get(10, TimeUnit.SECONDS);
    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
    CompletableFuture<Response> gotten = new CompletableFuture<>();
    send(servicePointsUrl("/" + id.toString()), HttpMethod.GET, null,
            SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(gotten));
    Response getResponse = gotten.get(10, TimeUnit.SECONDS);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  @Test
  public void canCreateServicePointWithHoldShelfExpiryPeriod()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    Response response = createServicePoint(null, "Circ Desk 11", "cd11",
      "Circulation Desk 11 -- Hallway", null, 20,
      true, createHoldShelfExpiryPeriod(3, HoldShelfExpiryPeriod.IntervalId.MINUTES));
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject responseJson = response.getJson();

    assertThat(responseJson.getString("id"), notNullValue());
    assertThat(responseJson.getString("code"), is("cd11"));
    assertThat(responseJson.getString("name"), is("Circ Desk 11"));

    JsonObject holdShelfExpiryPeriod = responseJson.getJsonObject("holdShelfExpiryPeriod");
    assertThat(holdShelfExpiryPeriod.getInteger("duration"), is (3));
    assertThat(holdShelfExpiryPeriod.getString("intervalId"), is (HoldShelfExpiryPeriod.IntervalId.MINUTES.toString()));
  }

  @Test
  public void canCreateServicePointWithoutPickupLocationAndHoldShelfExpiryPeriod()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    Response response = createServicePoint(null, "Circ Desk 11", "cd11",
      "Circulation Desk 11 -- Hallway", null, 20,
      null, null);

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject responseJson = response.getJson();

    assertThat(responseJson.getString("id"), notNullValue());
    assertThat(responseJson.getString("code"), is("cd11"));
    assertThat(responseJson.getString("name"), is("Circ Desk 11"));
  }

  @Test
  public void cannotCreateServicePointWithoutPickupLocationButWithHoldShelfExpiryPeriod()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    Response response = createServicePoint(null, "Circ Desk 101", "cd101",
      "Circulation Desk 101 -- Hallway", null, 20,
      null, createHoldShelfExpiryPeriod());

    assertThat(response.getStatusCode(), is(422));
    JsonObject responseJson = response.getJson();
    JsonArray errorsArray = responseJson.getJsonArray("errors");
    assertThat(errorsArray.size(), is(1));
    JsonObject errorObject = errorsArray.getJsonObject(0);
    assertThat(errorObject.getString("message"), is(SERVICE_POINT_CREATE_ERR_MSG_WITHOUT_BEING_PICKUP_LOC));
  }

  @Test
  public void cannotCreateServicePointWithoutHoldShelfExpiryPeriod()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    Response response = createServicePoint(null, "Circ Desk 1", "cd1",
      "Circulation Desk -- Hallway", null, 20,
      true, null);
    assertThat(response.getStatusCode(), is(422));

    JsonObject responseJson = response.getJson();
    JsonArray errorsArray = responseJson.getJsonArray("errors");
    assertThat(errorsArray.size(), is(1));
    JsonObject errorObject = errorsArray.getJsonObject(0);
    assertThat(errorObject.getString("message"), is(SERVICE_POINT_CREATE_ERR_MSG_WITHOUT_HOLD_EXPIRY));
  }

  @Test
  public void cannotCreateServicePointWithoutBeingPickupLocation()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    Response response = createServicePoint(null, "Circ Desk 1", "cd1",
      "Circulation Desk -- Hallway", null, 20,
      false, createHoldShelfExpiryPeriod());
    assertThat(response.getStatusCode(), is(422));
    JsonObject responseJson = response.getJson();
    JsonArray errorsArray = responseJson.getJsonArray("errors");
    assertThat(errorsArray.size(), is(1));
    JsonObject errorObject = errorsArray.getJsonObject(0);
    assertThat(errorObject.getString("message"), is(SERVICE_POINT_CREATE_ERR_MSG_WITHOUT_BEING_PICKUP_LOC));
  }

  @Test
  public void cannotCreateServicePointWithoutAValidHoldShelfExpiryPeriod()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    Response response = createServicePoint(null, "Circ Desk 1", "cd1",
      "Circulation Desk -- Hallway", null, 20,
      false, createHoldShelfExpiryPeriod(2, null));
    assertThat(response.getStatusCode(), is(422));
  }

  @Test
  public void canUpdateAServicePointWithHoldShelfExpiryPeriodWhenThereWasNoHoldShelfExpiryAndNotBeingPickupLocation()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {
    UUID id = UUID.randomUUID();
    createServicePoint(id, "Circ Desk 1", "cd1",
      "Circulation Desk -- Hallway", null, 20,
      false, null);
    JsonObject request = new JsonObject()
      .put("id", id.toString())
      .put("name", "Circ Desk 2")
      .put("code", "cd2")
      .put("discoveryDisplayName", "Circulation Desk -- Basement")
      .put("pickupLocation", true)
      .put("holdShelfExpiryPeriod", new JsonObject(
                                      Json.encode(createHoldShelfExpiryPeriod(5, HoldShelfExpiryPeriod.IntervalId.WEEKS)))
      );
    CompletableFuture<Response> updated = new CompletableFuture<>();
    send(servicePointsUrl("/" + id.toString()), HttpMethod.PUT, request.encode(),
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(updated));

    Response updateResponse = updated.get(10, TimeUnit.SECONDS);
    assertThat(updateResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
    Response getResponse = getById(id);
    JsonObject responseJson = getResponse.getJson();
    assertThat(responseJson.getString("id"), is(id.toString()));
    assertThat(responseJson.getString("code"), is("cd2"));
    assertThat(responseJson.getString("name"), is("Circ Desk 2")); //should fail
    assertThat(responseJson.getBoolean("pickupLocation"), is(true));

    JsonObject holdShelfExpiryPeriod = responseJson.getJsonObject("holdShelfExpiryPeriod");
    assertThat(holdShelfExpiryPeriod.getInteger("duration"), is (5));
    assertThat(holdShelfExpiryPeriod.getString("intervalId"), is (HoldShelfExpiryPeriod.IntervalId.WEEKS.toString()));
  }

  @Test
  public void canUpdateAServicePointWithHoldShelfExpiryPeriodAndHoldShelfCloseLibraryDateManagementAndNotBeingPickupLocation()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {
    UUID id = UUID.randomUUID();
    createServicePoint(id, "Circ Desk 1", "cd1",
      "Circulation Desk -- Hallway", null, 20,
      false, null);
    JsonObject request = new JsonObject()
      .put("id", id.toString())
      .put("name", "Circ Desk 2")
      .put("code", "cd2")
      .put("discoveryDisplayName", "Circulation Desk -- Basement")
      .put("pickupLocation", true)
      .put("holdShelfClosedLibraryDateManagement", Servicepoint.HoldShelfClosedLibraryDateManagement.MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS.value())
      .put("holdShelfExpiryPeriod", new JsonObject(
        Json.encode(createHoldShelfExpiryPeriod(5, HoldShelfExpiryPeriod.IntervalId.WEEKS)))
      );
    CompletableFuture<Response> updated = new CompletableFuture<>();
    send(servicePointsUrl("/" + id.toString()), HttpMethod.PUT, request.encode(),
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(updated));

    Response updateResponse = updated.get(10, TimeUnit.SECONDS);
    assertThat(updateResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
    Response getResponse = getById(id);
    JsonObject responseJson = getResponse.getJson();
    assertThat(responseJson.getString("id"), is(id.toString()));
    assertThat(responseJson.getString("code"), is("cd2"));
    assertThat(responseJson.getString("name"), is("Circ Desk 2")); //should fail
    assertThat(responseJson.getBoolean("pickupLocation"), is(true));
    assertThat(responseJson.getString("holdShelfClosedLibraryDateManagement"),
      is (Servicepoint.HoldShelfClosedLibraryDateManagement.MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS.value()));

    JsonObject holdShelfExpiryPeriod = responseJson.getJsonObject("holdShelfExpiryPeriod");
    assertThat(holdShelfExpiryPeriod.getInteger("duration"), is (5));
    assertThat(holdShelfExpiryPeriod.getString("intervalId"), is (HoldShelfExpiryPeriod.IntervalId.WEEKS.toString()));
  }

  @Test
  public void canUpdateAServicePointWithoutHoldShelfExpiryPeriodAndWithoutBeingPickupLocationWhenNeitherWasTheCase()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {
    UUID id = UUID.randomUUID();
    createServicePoint(id, "Circ Desk 1", "cd1",
      "Circulation Desk -- Hallway", null, 20,
      false, null);
    JsonObject request = new JsonObject()
      .put("id", id.toString())
      .put("name", "Circ Desk 2")
      .put("code", "cd2")
      .put("discoveryDisplayName", "Circulation Desk -- Basement")
      .put("pickupLocation", false);

    CompletableFuture<Response> updated = new CompletableFuture<>();
    send(servicePointsUrl("/" + id.toString()), HttpMethod.PUT, request.encode(),
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(updated));

    Response updateResponse = updated.get(10, TimeUnit.SECONDS);
    assertThat(updateResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
    Response getResponse = getById(id);
    JsonObject responseJson = getResponse.getJson();
    assertThat(responseJson.getString("id"), is(id.toString()));
    assertThat(responseJson.getString("code"), is("cd2"));
    assertThat(responseJson.getString("name"), is("Circ Desk 2"));
    assertThat(responseJson.getBoolean("pickupLocation"), is(false));
  }

  @Test
  public void cannotUpdateAServicePointWithoutHoldShelfExpiryPeriodWhenItOriginallyExisted()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {
    UUID id = UUID.randomUUID();
    createServicePoint(id, "Circ Desk 1", "cd1",
      "Circulation Desk -- Hallway", null, 20, true, createHoldShelfExpiryPeriod());
    JsonObject request = new JsonObject()
      .put("id", id.toString())
      .put("name", "Circ Desk 2")
      .put("code", "cd2")
      .put("discoveryDisplayName", "Circulation Desk -- Basement")
      .put("pickupLocation", true);

    CompletableFuture<Response> updated = new CompletableFuture<>();
    send(servicePointsUrl("/" + id.toString()), HttpMethod.PUT, request.encode(),
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(updated));
    Response updateResponse = updated.get(10, TimeUnit.SECONDS);

    assertThat(updateResponse.getStatusCode(), is(422));
    JsonObject responseJson = updateResponse.getJson();
    JsonArray errorsArray = responseJson.getJsonArray("errors");
    assertThat(errorsArray.size(), is(1));
    JsonObject errorObject = errorsArray.getJsonObject(0);
    assertThat(errorObject.getString("message"), is(SERVICE_POINT_CREATE_ERR_MSG_WITHOUT_HOLD_EXPIRY));
  }

  @Test
  public void cannotUpdateAServicePointWithoutHoldShelfExpiryPeriodWhenItNeverExisted()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {
    UUID id = UUID.randomUUID();
    createServicePoint(id, "Circ Desk 102", "cd102",
      "Circulation Desk -- Hallway", null, 20, false, null);
    JsonObject request = new JsonObject()
      .put("id", id.toString())
      .put("name", "Circ Desk 102")
      .put("code", "cd102")
      .put("discoveryDisplayName", "Circulation Desk -- Basement")
      .put("pickupLocation", true);

    CompletableFuture<Response> updated = new CompletableFuture<>();
    send(servicePointsUrl("/" + id.toString()), HttpMethod.PUT, request.encode(),
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(updated));
    Response updateResponse = updated.get(10, TimeUnit.SECONDS);

    assertThat(updateResponse.getStatusCode(), is(422));
    JsonObject responseJson = updateResponse.getJson();
    JsonArray errorsArray = responseJson.getJsonArray("errors");
    assertThat(errorsArray.size(), is(1));
    JsonObject errorObject = errorsArray.getJsonObject(0);
    assertThat(errorObject.getString("message"), is(SERVICE_POINT_CREATE_ERR_MSG_WITHOUT_HOLD_EXPIRY));
  }

  @Test
  public void cannotUpdateAServicePointWithoutBeingPickupLocationAndHoldShelfExpiryPeriodAlreadyExisted()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {
    UUID id = UUID.randomUUID();

    HoldShelfExpiryPeriod defaultHoldShelfExpiryPeriod = createHoldShelfExpiryPeriod();

    createServicePoint(id, "Circ Desk 1", "cd1",
      "Circulation Desk -- Hallway", null, 20, true, defaultHoldShelfExpiryPeriod);

    JsonObject request = new JsonObject()
      .put("id", id.toString())
      .put("name", "Circ Desk 2")
      .put("code", "cd2")
      .put("discoveryDisplayName", "Circulation Desk -- Basement")
      .put("pickupLocation", false)
      .put("holdShelfExpiryPeriod", new JsonObject(Json.encode(defaultHoldShelfExpiryPeriod)));

    CompletableFuture<Response> updated = new CompletableFuture<>();
    send(servicePointsUrl("/" + id.toString()), HttpMethod.PUT, request.encode(),
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(updated));
    Response updateResponse = updated.get(10, TimeUnit.SECONDS);

    assertThat(updateResponse.getStatusCode(), is(422));
    JsonObject responseJson = updateResponse.getJson();
    JsonArray errorsArray = responseJson.getJsonArray("errors");
    assertThat(errorsArray.size(), is(1));
    JsonObject errorObject = errorsArray.getJsonObject(0);
    assertThat(errorObject.getString("message"), is(SERVICE_POINT_CREATE_ERR_MSG_WITHOUT_BEING_PICKUP_LOC));
  }

  @Test
  public void cannotUpdateAServicePointWithoutBeingPickupLocationWhileAddingHoldShelfExpiryPeriod()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {
    UUID id = UUID.randomUUID();

    HoldShelfExpiryPeriod defaultHoldShelfExpiryPeriod = createHoldShelfExpiryPeriod();

    createServicePoint(id, "Circ Desk 1", "cd1",
      "Circulation Desk -- Hallway", null, 20, false, null);

    JsonObject request = new JsonObject()
      .put("id", id.toString())
      .put("name", "Circ Desk 2")
      .put("code", "cd2")
      .put("discoveryDisplayName", "Circulation Desk -- Basement")
      .put("pickupLocation", false)
      .put("holdShelfExpiryPeriod", new JsonObject(Json.encode(defaultHoldShelfExpiryPeriod)));

    CompletableFuture<Response> updated = new CompletableFuture<>();
    send(servicePointsUrl("/" + id.toString()), HttpMethod.PUT, request.encode(),
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(updated));
    Response updateResponse = updated.get(10, TimeUnit.SECONDS);

    assertThat(updateResponse.getStatusCode(), is(422));
    JsonObject responseJson = updateResponse.getJson();
    JsonArray errorsArray = responseJson.getJsonArray("errors");
    assertThat(errorsArray.size(), is(1));
    JsonObject errorObject = errorsArray.getJsonObject(0);
    assertThat(errorObject.getString("message"), is(SERVICE_POINT_CREATE_ERR_MSG_WITHOUT_BEING_PICKUP_LOC));
  }

  @Test
  public void canUpdateAServicePointWithHoldShelfExpiryPeriodAndBeingPickupLocationWhenBothWasPresent()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();
    createServicePoint(id, "Circ Desk 1", "cd1",
      "Circulation Desk -- Hallway", null, 20,
      true, createHoldShelfExpiryPeriod());
    JsonObject request = new JsonObject()
      .put("id", id.toString())
      .put("name", "Circ Desk 2")
      .put("code", "cd2")
      .put("discoveryDisplayName", "Circulation Desk -- Basement")
      .put("pickupLocation", true)
      .put("holdShelfExpiryPeriod", new JsonObject(
        Json.encode(createHoldShelfExpiryPeriod(5, HoldShelfExpiryPeriod.IntervalId.WEEKS)))
      );
    CompletableFuture<Response> updated = new CompletableFuture<>();
    send(servicePointsUrl("/" + id.toString()), HttpMethod.PUT, request.encode(),
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(updated));

    Response updateResponse = updated.get(10, TimeUnit.SECONDS);
    assertThat(updateResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
    Response getResponse = getById(id);
    JsonObject responseJson = getResponse.getJson();
    assertThat(responseJson.getString("id"), is(id.toString()));
    assertThat(responseJson.getString("code"), is("cd2"));
    assertThat(responseJson.getString("name"), is("Circ Desk 2"));
    assertThat(responseJson.getBoolean("pickupLocation"), is(true));

    JsonObject holdShelfExpiryPeriod = responseJson.getJsonObject("holdShelfExpiryPeriod");
    assertThat(holdShelfExpiryPeriod.getInteger("duration"), is (5));
    assertThat(holdShelfExpiryPeriod.getString("intervalId"), is (HoldShelfExpiryPeriod.IntervalId.WEEKS.toString()));
  }

  @Test
  public void canUpdateAServicePointWithoutHoldShelfExpiryPeriodAndBeingPickupLocationWhenBothWasPresent()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();
    createServicePoint(id, "Circ Desk 1", "cd1",
      "Circulation Desk -- Hallway", null, 20,
      true, createHoldShelfExpiryPeriod());
    JsonObject request = new JsonObject()
      .put("id", id.toString())
      .put("name", "Circ Desk 2")
      .put("code", "cd2")
      .put("discoveryDisplayName", "Circulation Desk -- Basement")
      .put("pickupLocation", false);

    CompletableFuture<Response> updated = new CompletableFuture<>();
    send(servicePointsUrl("/" + id.toString()), HttpMethod.PUT, request.encode(),
      SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(updated));

    Response updateResponse = updated.get(10, TimeUnit.SECONDS);
    assertThat(updateResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
    Response getResponse = getById(id);
    JsonObject responseJson = getResponse.getJson();
    assertThat(responseJson.getString("id"), is(id.toString()));
    assertThat(responseJson.getString("code"), is("cd2"));
    assertThat(responseJson.getString("name"), is("Circ Desk 2"));
    assertThat(responseJson.getBoolean("pickupLocation"), is(false));
  }


  @Test
  public void canCreateServicePointWithStaffSlips()
      throws InterruptedException,
      ExecutionException,
      TimeoutException,
      MalformedURLException {

    String uuidTrue = UUID.randomUUID().toString();
    String uuidFalse = UUID.randomUUID().toString();
    List<StaffSlip> staffSlips = new ArrayList<>(2);
    staffSlips.add(new StaffSlip().withId(uuidTrue).withPrintByDefault(Boolean.TRUE));
    staffSlips.add(new StaffSlip().withId(uuidFalse).withPrintByDefault(Boolean.FALSE));

    Response response = createServicePoint(null, "Circ Desk 1", "cd1",
        "Circulation Desk -- Hallway", null, 20, true, createHoldShelfExpiryPeriod(), staffSlips);
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
    assertThat(response.getJson().getString("id"), notNullValue());
    assertThat(response.getJson().getString("code"), is("cd1"));
    assertThat(response.getJson().getString("name"), is("Circ Desk 1"));
    assertThat(response.getJson().getJsonArray("staffSlips").getJsonObject(0).getString("id"), is(uuidTrue));
    assertThat(response.getJson().getJsonArray("staffSlips").getJsonObject(0).getBoolean("printByDefault"), is(Boolean.TRUE));
    assertThat(response.getJson().getJsonArray("staffSlips").getJsonObject(1).getString("id"), is(uuidFalse));
    assertThat(response.getJson().getJsonArray("staffSlips").getJsonObject(1).getBoolean("printByDefault"), is(Boolean.FALSE));
  }

  @Test
  public void cannotCreateServicePointWithStaffSlipsMissingFields()
      throws InterruptedException,
      ExecutionException,
      TimeoutException,
      MalformedURLException {

    String uuid = UUID.randomUUID().toString();
    List<StaffSlip> staffSlips = new ArrayList<>(1);
    staffSlips.add(new StaffSlip().withId(uuid));

    Response response = createServicePoint(null, "Circ Desk 1", "cd1",
        "Circulation Desk -- Hallway", null, 20, true, createHoldShelfExpiryPeriod(), staffSlips);
    assertThat(response.getStatusCode(), is(422));
  }

  @Test
  public void canUpdateAServicePointWithStaffSlips()
          throws InterruptedException,
          ExecutionException,
          TimeoutException,
          MalformedURLException {
    UUID id = UUID.randomUUID();
    String staffSlipId = UUID.randomUUID().toString();
    List<StaffSlip> staffSlips = new ArrayList<>(2);
    staffSlips.add(new StaffSlip().withId(staffSlipId).withPrintByDefault(Boolean.TRUE));
    createServicePoint(id, "Circ Desk 1", "cd1",
        "Circulation Desk -- Hallway", null, 20, true, createHoldShelfExpiryPeriod(), staffSlips);
    JsonObject request = new JsonObject()
            .put("id", id.toString())
            .put("name", "Circ Desk 2")
            .put("code", "cd2")
            .put("discoveryDisplayName", "Circulation Desk -- Basement")
            .put("pickupLocation", false)
            .put("staffSlips", new JsonArray()
                .add(new JsonObject()
                    .put("id", staffSlipId)
                    .put("printByDefault", Boolean.FALSE)));
    CompletableFuture<Response> updated = new CompletableFuture<>();
    send(servicePointsUrl("/" + id.toString()), HttpMethod.PUT, request.encode(),
            SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(updated));
    Response updateResponse = updated.get(10, TimeUnit.SECONDS);
    assertThat(updateResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
    Response getResponse = getById(id);
    assertThat(getResponse.getJson().getString("id"), is(id.toString()));
    assertThat(getResponse.getJson().getString("code"), is("cd2"));
    assertThat(getResponse.getJson().getString("name"), is("Circ Desk 2")); //should fail
    assertThat(getResponse.getJson().getBoolean("pickupLocation"), is(false));
    assertThat(getResponse.getJson().getJsonArray("staffSlips").getJsonObject(0).getString("id"), is(staffSlipId));
    assertThat(getResponse.getJson().getJsonArray("staffSlips").getJsonObject(0).getBoolean("printByDefault"), is(Boolean.FALSE));
  }

  @Test
  public void canFilterByPickupLocation() throws Exception {
    final UUID pickupLocationServicePointId = UUID.randomUUID();
    createServicePoint(pickupLocationServicePointId, "Circ Desk 1", "cd1",
      "Circulation Desk -- Hallway", null, 20, true, createHoldShelfExpiryPeriod());
    createServicePoint(null, "Circ Desk 2", "cd2",
      "Circulation Desk -- Basement", null, 20, false, createHoldShelfExpiryPeriod());

    final List<JsonObject> servicePoints = getMany("pickupLocation==true");

    assertThat(servicePoints.size(), is(1));
    assertThat(servicePoints.get(0).getString("id"),
      is(pickupLocationServicePointId.toString()));
  }

  private List<JsonObject> getMany(String cql, Object... args) throws InterruptedException,
    ExecutionException, TimeoutException {

    final CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    send(servicePointsUrl("?query=" + String.format(cql, args)),
      HttpMethod.GET, null, SUPPORTED_CONTENT_TYPE_JSON_DEF,
      ResponseHandler.json(getCompleted));

    return getCompleted.get(10, TimeUnit.SECONDS).getJson()
      .getJsonArray("servicepoints").stream()
      .map(obj -> (JsonObject) obj)
      .collect(Collectors.toList());
  }

  public static Response createServicePoint(UUID id, String name, String code,
                                            String discoveryDisplayName, String description, Integer shelvingLagTime,
                                            Boolean pickupLocation, HoldShelfExpiryPeriod shelfExpiryPeriod)
    throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {

    return createServicePoint(id, name, code, discoveryDisplayName, description,
      shelvingLagTime, pickupLocation, shelfExpiryPeriod, Collections.emptyList());

  }

  private static Response createServicePoint(UUID id, String name, String code,
                                            String discoveryDisplayName, String description, Integer shelvingLagTime,
                                            Boolean pickupLocation, HoldShelfExpiryPeriod shelfExpiryPeriod, List<StaffSlip> slips)
    throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<Response> createServicePoint = new CompletableFuture<>();
    JsonObject request = new JsonObject();
    request
      .put("name", name)
      .put("code", code)
      .put("discoveryDisplayName", discoveryDisplayName);
    if(id != null) { request.put("id", id.toString()); }
    if(description != null) { request.put("description", description); }
    if(shelvingLagTime != null) { request.put("shelvingLagTime", shelvingLagTime); }
    if(pickupLocation != null) { request.put("pickupLocation", pickupLocation); }
    if(shelfExpiryPeriod != null) {request.put("holdShelfExpiryPeriod", new JsonObject(Json.encode(shelfExpiryPeriod)));}

    if (!slips.isEmpty()) {
      JsonArray staffSlips = new JsonArray();
      for (StaffSlip ss : slips) {
        JsonObject staffSlip = new JsonObject();
        staffSlip.put("id", ss.getId());
        staffSlip.put("printByDefault", ss.getPrintByDefault());
        staffSlips.add(staffSlip);
      }
      request.put("staffSlips", staffSlips);
    }

    send(servicePointsUrl(""), HttpMethod.POST, request.toString(),
            SUPPORTED_CONTENT_TYPE_JSON_DEF,
            ResponseHandler.json(createServicePoint));
    return createServicePoint.get(10, TimeUnit.SECONDS);
  }

  public static HoldShelfExpiryPeriod createHoldShelfExpiryPeriod(int duration, HoldShelfExpiryPeriod.IntervalId intervalId){
    HoldShelfExpiryPeriod holdShelfExpiryPeriod = new HoldShelfExpiryPeriod();
    holdShelfExpiryPeriod.setDuration(duration);
    holdShelfExpiryPeriod.setIntervalId(intervalId);
    return holdShelfExpiryPeriod;
  }

  public static HoldShelfExpiryPeriod createHoldShelfExpiryPeriod(){
      return createHoldShelfExpiryPeriod(2, HoldShelfExpiryPeriod.IntervalId.DAYS);
  }

  private Response getById(UUID id)
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    send(servicePointsUrl("/" + id.toString()), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(getCompleted));

    return getCompleted.get(10, TimeUnit.SECONDS);
  }

}
