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

public class LoanTypesClient {
  private final HttpClient client;
  private final URL loanTypesUrl;

  public LoanTypesClient(HttpClient client, URL loanTypesUrl) {
    this.client = client;
    this.loanTypesUrl = loanTypesUrl;
  }

  public String create(String name)
    throws InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<JsonResponse> completed = new CompletableFuture();

    JsonObject loanTypeRequest = new JsonObject()
      .put("name", name);

    client.post(loanTypesUrl, loanTypeRequest, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(completed));

    JsonResponse response = completed.get(5, TimeUnit.SECONDS);

    return response.getJson().getString("id");
  }
}
