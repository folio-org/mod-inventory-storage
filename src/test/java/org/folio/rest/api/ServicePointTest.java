/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.folio.rest.api;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.folio.rest.support.AdditionalHttpStatusCodes;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.junit.Test;
import static org.folio.rest.support.http.InterfaceUrls.*;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import org.junit.Assert;
import static org.junit.Assert.assertThat;
import org.junit.Before;

/**
 *
 * @author kurt
 */
public class ServicePointTest {
  private static Logger logger = LoggerFactory.getLogger(ServicePointTest.class);
  private static final String SUPPORTED_CONTENT_TYPE_JSON_DEF = "application/json";

  @Before
  public void beforeEach()
          throws InterruptedException,
          ExecutionException,
          TimeoutException,
          MalformedURLException {
    StorageTestSuite.deleteAll(servicePointsUrl(""));
  }

  // --- BEGIN TESTS --- //

  @Test
  public void canCreateServicePoint()
          throws InterruptedException,
          ExecutionException,
          TimeoutException,
          MalformedURLException {
    Response response = createServicePoint(null, "Circ Desk 1", "cd1",
            "Circulation Desk -- Hallway", null, 20, true, true);
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
            "Circulation Desk -- Hallway", null, 20, true, true);
    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void cannotCreateServicePointWithoutCode()
          throws InterruptedException,
          ExecutionException,
          TimeoutException,
          MalformedURLException {
    Response response = createServicePoint(null, "Circ Desk 1", null,
            "Circulation Desk -- Hallway", null, 20, true, true);
    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void cannotCreateServicePointWithoutDDName()
          throws InterruptedException,
          ExecutionException,
          TimeoutException,
          MalformedURLException {
    Response response = createServicePoint(null, "Circ Desk 1", "cd1",
            null, null, 20, true, true);
    assertThat(response.getStatusCode(), is(AdditionalHttpStatusCodes.UNPROCESSABLE_ENTITY));
  }

  @Test
  public void cannotCreateServicePointWithDuplicateName()
          throws InterruptedException,
          ExecutionException,
          TimeoutException,
          MalformedURLException {
    createServicePoint(null, "Circ Desk 1", "cd1",
            "Circulation Desk -- Hallway", null, 20, true, true);
    Response response = createServicePoint(null, "Circ Desk 1", "cd2",
            "Circulation Desk -- Bathroom", null, 20, true, true);
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
            "Circulation Desk -- Hallway", null, 20, true, true);
    Response response = getById(id);
    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(response.getJson().getString("id"), is(id.toString()));
    assertThat(response.getJson().getString("code"), is("cd1"));
    assertThat(response.getJson().getString("name"), is("Circ Desk 1"));
  }

  @Test
  public void canGetMultipleServicePoints()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    createServicePoint(null, "Circ Desk 1", "cd1",
            "Circulation Desk -- Hallway", null, 20, true, true);
    createServicePoint(null, "Circ Desk 2", "cd2",
            "Circulation Desk -- Basement", null, 20, true, true);
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    send(servicePointsUrl("/"), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(getCompleted));
    Response getResponse = getCompleted.get(5, TimeUnit.SECONDS);
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
            "Circulation Desk -- Hallway", null, 20, true, true);
    createServicePoint(null, "Circ Desk 2", "cd2",
            "Circulation Desk -- Basement", null, 20, true, true);
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    send(servicePointsUrl("/?query=code==cd1"), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(getCompleted));
    Response getResponse = getCompleted.get(5, TimeUnit.SECONDS);
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
            "Circulation Desk -- Hallway", null, 20, true, true);
    JsonObject request = new JsonObject()
            .put("id", id.toString())
            .put("name", "Circ Desk 2")
            .put("code", "cd2")
            .put("discoveryDisplayName", "Circulation Desk -- Basement")
            .put("pickupLocation", false);
    CompletableFuture<Response> updated = new CompletableFuture<>();
    send(servicePointsUrl("/" + id.toString()), HttpMethod.PUT, request.encode(),
            SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(updated));
    Response updateResponse = updated.get(5, TimeUnit.SECONDS);
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
            "Circulation Desk -- Hallway", null, 20, true, true);
    CompletableFuture<Response> deleted = new CompletableFuture<>();
    send(servicePointsUrl("/" + id.toString()), HttpMethod.DELETE, null,
            SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(deleted));
    Response deleteResponse = deleted.get(5, TimeUnit.SECONDS);
    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
    CompletableFuture<Response> gotten = new CompletableFuture<>();
    send(servicePointsUrl("/" + id.toString()), HttpMethod.GET, null,
            SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.any(gotten));
    Response getResponse = gotten.get(5, TimeUnit.SECONDS);
    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }
  // --- END TESTS --- //

  public static Response createServicePoint(UUID id, String name, String code,
          String discoveryDisplayName, String description, Integer shelvingLagTime,
          Boolean pickupLocation, Boolean feeFineOwner) throws MalformedURLException,
          InterruptedException, ExecutionException, TimeoutException {

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
    if(feeFineOwner != null) { request.put("feeFineOwner", feeFineOwner); }
    send(servicePointsUrl(""), HttpMethod.POST, request.toString(),
            SUPPORTED_CONTENT_TYPE_JSON_DEF,
            ResponseHandler.json(createServicePoint));
    return createServicePoint.get(5, TimeUnit.SECONDS);

  }

  private Response getById(UUID id)
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    send(servicePointsUrl("/" + id.toString()), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(getCompleted));

    return getCompleted.get(5, TimeUnit.SECONDS);
  }

  private static void send(URL url, HttpMethod method, String content,
                    String contentType, Handler<HttpClientResponse> handler) {

    HttpClient client = StorageTestSuite.getVertx().createHttpClient();
    HttpClientRequest request;

    if(content == null){
      content = "";
    }
    Buffer buffer = Buffer.buffer(content);

    if (method == HttpMethod.POST) {
      request = client.postAbs(url.toString());
    }
    else if (method == HttpMethod.DELETE) {
      request = client.deleteAbs(url.toString());
    }
    else if (method == HttpMethod.GET) {
      request = client.getAbs(url.toString());
    }
    else {
      request = client.putAbs(url.toString());
    }
    request.exceptionHandler(error -> {
      Assert.fail(error.getLocalizedMessage());
    })
    .handler(handler);

    request.putHeader("Authorization", "test_tenant");
    request.putHeader("x-okapi-tenant", "test_tenant");
    request.putHeader("Accept", "application/json,text/plain");
    request.putHeader("Content-type", contentType);
    request.end(buffer);
  }
}
