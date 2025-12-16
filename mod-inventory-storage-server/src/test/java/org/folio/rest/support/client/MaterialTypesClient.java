package org.folio.rest.support.client;

import static org.folio.utility.RestUtility.TENANT_ID;

import io.vertx.core.json.JsonObject;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import org.folio.rest.api.TestBase;
import org.folio.rest.support.HttpClient;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;

public class MaterialTypesClient {
  private final HttpClient client;
  private final URL materialTypesUrl;

  public MaterialTypesClient(HttpClient client, URL materialTypesUrl) {
    this.client = client;
    this.materialTypesUrl = materialTypesUrl;
  }

  public String create(String name) {
    return create(name, TENANT_ID);
  }

  public String create(String name, String tenantId) {

    CompletableFuture<Response> completed = new CompletableFuture<>();

    JsonObject materialTypeRequest = new JsonObject()
      .put("name", name);

    client.post(materialTypesUrl, materialTypeRequest, tenantId,
      ResponseHandler.json(completed));

    Response response = TestBase.get(completed);

    return response.getJson().getString("id");
  }
}
