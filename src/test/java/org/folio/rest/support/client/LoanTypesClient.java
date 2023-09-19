package org.folio.rest.support.client;

import static org.folio.utility.RestUtility.TENANT_ID;

import io.vertx.core.json.JsonObject;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import org.folio.rest.api.TestBase;
import org.folio.rest.support.HttpClient;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;

public class LoanTypesClient {
  private final HttpClient client;
  private final URL loanTypesUrl;

  public LoanTypesClient(HttpClient client, URL loanTypesUrl) {
    this.client = client;
    this.loanTypesUrl = loanTypesUrl;
  }

  public String create(String name) {

    return create(name, TENANT_ID);
  }

  public String create(String name, String tenantId) {

    CompletableFuture<Response> completed = new CompletableFuture<>();

    JsonObject loanTypeRequest = new JsonObject()
      .put("name", name);

    client.post(loanTypesUrl, loanTypeRequest, tenantId,
      ResponseHandler.json(completed));

    Response response = TestBase.get(completed);

    return response.getJson().getString("id");
  }
}
