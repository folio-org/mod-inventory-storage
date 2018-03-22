package org.folio.rest.api;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.client.LoanTypesClient;
import org.folio.rest.support.client.MaterialTypesClient;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.folio.rest.support.HttpResponseMatchers.statusCodeIs;
import static org.folio.rest.support.http.InterfaceUrls.*;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;



public class ShelfLocationsTest {

  private static final String SUPPORTED_CONTENT_TYPE_JSON_DEF = "application/json";
  private String canCirculateLoanTypeID;
  private String journalMaterialTypeID;
  private UUID instID;
  private UUID campID;
  private UUID libID;

  @Before
  public void beforeEach()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    StorageTestSuite.deleteAll(itemsStorageUrl(""));
    StorageTestSuite.deleteAll(ShelfLocationsStorageUrl(""));

    StorageTestSuite.deleteAll(locationsStorageUrl(""));
    StorageTestSuite.deleteAll(locInstitutionStorageUrl(""));
    StorageTestSuite.deleteAll(locCampusStorageUrl(""));
    StorageTestSuite.deleteAll(locLibraryStorageUrl(""));

    StorageTestSuite.deleteAll(loanTypesStorageUrl(""));
    StorageTestSuite.deleteAll(materialTypesStorageUrl(""));

    canCirculateLoanTypeID = new LoanTypesClient(
      new org.folio.rest.support.HttpClient(StorageTestSuite.getVertx()),
      loanTypesStorageUrl("")).create("Can Circulate");

    journalMaterialTypeID = new MaterialTypesClient(
      new org.folio.rest.support.HttpClient(StorageTestSuite.getVertx()),
      materialTypesStorageUrl("")).create("Journal");

    instID = UUID.randomUUID();
    LocationUnitTest.createInst(instID, "Primary Institution", "PI");
    campID = UUID.randomUUID();
    LocationUnitTest.createCamp(campID, "Central Campus", "CC", instID);
    libID = UUID.randomUUID();
    LocationUnitTest.createLib(libID, "Main Library", "ML", campID);
  }

  @Test
  public void canCreateShelfLocation()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    Response response = LocationsTest.createLocation(null, "Main Library",
      instID, campID, libID, "PI/CC/ML/X");

    assertThat(response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
    assertThat(response.getJson().getString("id"), notNullValue());
    assertThat(response.getJson().getString("name"), is("Main Library"));
  }

  @Test
  public void canGetAShelfLocationById()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();
    LocationsTest.createLocation(id, "Main Library", instID, campID, libID, "PI/CC/ML/X");
    Response getResponse = getById(id);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    JsonObject item = getResponse.getJson();
    assertThat(item.getString("id"), is(id.toString()));
    assertThat(item.getString("name"), is("Main Library"));
  }

  private static void send(URL url, HttpMethod method, String content,                           String contentType, Handler<HttpClientResponse> handler) {

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

  /*
  private JsonObject createItemRequest(String temporaryLocationId) {

    JsonObject item = new JsonObject();

    item.put("holdingsRecordId", UUID.randomUUID().toString());
    item.put("barcode", "12345");
    item.put("permanentLoanTypeId", canCirculateLoanTypeID);
    item.put("materialTypeId", journalMaterialTypeID);
    item.put("temporaryLocationId", temporaryLocationId);

    return item;
  }
*/
  private Response getById(UUID id)
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    send(ShelfLocationsStorageUrl("/" + id.toString()), HttpMethod.GET,
      null, SUPPORTED_CONTENT_TYPE_JSON_DEF, ResponseHandler.json(getCompleted));

    return getCompleted.get(5, TimeUnit.SECONDS);
  }

}

