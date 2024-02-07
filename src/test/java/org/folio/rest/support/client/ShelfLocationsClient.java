package org.folio.rest.support.client;

import static org.folio.rest.api.TestBase.TIMEOUT;
import static org.folio.utility.RestUtility.TENANT_ID;

import io.vertx.core.json.JsonObject;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.folio.rest.support.HttpClient;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;

public class ShelfLocationsClient {
  private final HttpClient client;
  private final URL shelfLocationsUrl;

  public ShelfLocationsClient(HttpClient client, URL shelfLocationsUrl) {
    this.client = client;
    this.shelfLocationsUrl = shelfLocationsUrl;
  }

  public String create(String name)
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<Response> completed = new CompletableFuture<>();

    JsonObject shelfLocationRequest = new JsonObject()
      .put("name", name);

    client.post(shelfLocationsUrl, shelfLocationRequest, TENANT_ID,
      ResponseHandler.json(completed));

    Response response = completed.get(TIMEOUT, TimeUnit.SECONDS);

    return response.getJson().getString("id");
  }

}
