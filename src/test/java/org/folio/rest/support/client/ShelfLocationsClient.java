package org.folio.rest.support.client;

import io.vertx.core.json.JsonObject;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.folio.rest.api.StorageTestSuite;
import org.folio.rest.support.HttpClient;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.ResponseHandler;

/**
 *
 * @author kurt
 */
public class ShelfLocationsClient {
  private final HttpClient client;
  private final URL shelfLocationsUrl;

  public ShelfLocationsClient(HttpClient client, URL shelfLocationsUrl) {
    this.client = client;
    this.shelfLocationsUrl = shelfLocationsUrl;
  }

  public String create(String name)
          throws InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<JsonResponse> completed = new CompletableFuture();

    JsonObject shelfLocationRequest = new JsonObject()
      .put("name", name);

    client.post(shelfLocationsUrl, shelfLocationRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(completed));

    JsonResponse response = completed.get(5, TimeUnit.SECONDS);

    return response.getJson().getString("id");
  }

}
