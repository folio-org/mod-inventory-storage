package org.folio.rest.support.client;

import io.vertx.core.json.JsonObject;
import org.folio.rest.api.StorageTestSuite;
import org.folio.rest.support.HttpClient;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.ResponseHandler;

import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class MaterialTypes {
  private final HttpClient client;
  private final URL materialTypesUrl;

  public MaterialTypes(HttpClient client, URL materialTypesUrl) {
    this.client = client;
    this.materialTypesUrl = materialTypesUrl;
  }

  public String create(String name)
    throws InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<JsonResponse> completed = new CompletableFuture();

    JsonObject materialTypeRequest = new JsonObject()
      .put("name", name);

    client.post(materialTypesUrl, materialTypeRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(completed));

    JsonResponse mtPostResponse = completed.get(5, TimeUnit.SECONDS);

    return mtPostResponse.getJson().getString("id");
  }
}
