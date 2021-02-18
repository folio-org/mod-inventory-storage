package org.folio.rest.support.fixtures;

import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.folio.rest.support.http.InterfaceUrls.instanceReindex;

import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import org.folio.rest.jaxrs.model.ReindexJob;
import org.folio.rest.support.HttpClient;
import org.folio.rest.support.Response;

public final class InstanceReindexFixture {
  private final HttpClient client;

  public InstanceReindexFixture(HttpClient httpClient) {
    this.client = httpClient;
  }

  @SneakyThrows
  public ReindexJob submitReindex() {
    return client.post(instanceReindex(""),
      null, TENANT_ID)
      .thenApply(Response::getJson)
      .thenApply(json -> json.mapTo(ReindexJob.class))
      .get(5, TimeUnit.SECONDS);
  }

  @SneakyThrows
  public Response cancelReindexJob(String id) {
    return client.delete(instanceReindex("/" + id), TENANT_ID)
      .get(5, TimeUnit.SECONDS);
  }

  @SneakyThrows
  public ReindexJob getReindexJob(String id) {
    return client.get(instanceReindex("/" + id), TENANT_ID)
      .thenApply(Response::getJson)
      .thenApply(json -> json.mapTo(ReindexJob.class))
      .get(5, TimeUnit.SECONDS);
  }
}
