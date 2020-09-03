package org.folio.rest.api;

import static org.folio.rest.support.http.InterfaceUrls.holdingsSourceUrl;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.http.ResourceClient;
import org.junit.BeforeClass;
import org.junit.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class HoldingsSourceTest extends TestBase {

  private static ResourceClient holdingsSourceClient;

  @BeforeClass
  public static void beforeAll() {
    holdingsSourceClient = ResourceClient.forHoldingsSource(client);
  }

  @Test
  public void canCreateHoldingSource()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    UUID sourceId = UUID.randomUUID();

    JsonObject source = holdingsSourceClient.create(
      new JsonObject()
      .put("id", sourceId.toString())
      .put("name", "test source")
    ).getJson();

    assertThat(source.getString("id"), is(sourceId.toString()));
    assertThat(source.getString("name"), is("test source"));

    Response getResponse = holdingsSourceClient.getById(sourceId);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject sourceFromGet = getResponse.getJson();

    assertThat(sourceFromGet.getString("id"), is(sourceId.toString()));
    assertThat(sourceFromGet.getString("name"), is("test source"));
  }

  @Test
  public void canCreateHoldingSourcesWithoutProvidingAnId()
    throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    IndividualResource sourceResponse = holdingsSourceClient.create(
      new JsonObject()
      .put("name", "test source without id")
    );

    JsonObject source = sourceResponse.getJson();

    assertThat(source.getString("id"), is(notNullValue()));
    assertThat(source.getString("name"), is("test source without id"));

    UUID sourceId = sourceResponse.getId();

    Response getResponse = holdingsSourceClient.getById(sourceId);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject sourceFromGet = getResponse.getJson();

    assertThat(sourceFromGet.getString("id"), is(sourceId.toString()));
    assertThat(sourceFromGet.getString("name"), is("test source without id"));
  }

  @Test
  public void cannotCreateAHoldingsSourcesWithIDThatIsNotUUID()
    throws InterruptedException,
    ExecutionException, TimeoutException {

    String nonUUIDId = "1234567";

    JsonObject request = new JsonObject()
      .put("id", nonUUIDId)
      .put("name", "source with invalid id");

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    client.post(holdingsSourceUrl(""), request, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    Response response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(422));
    JsonArray errors = response.getJson().getJsonArray("errors");
    assertThat(errors.size(), is(1));

    JsonObject firstError = errors.getJsonObject(0);
    assertThat(firstError.getString("message"), containsString("must match"));
    assertThat(firstError.getJsonArray("parameters").getJsonObject(0).getString("key"),
      is("id"));
  }
}
