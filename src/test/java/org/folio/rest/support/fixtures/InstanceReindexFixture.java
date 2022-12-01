package org.folio.rest.support.fixtures;

import static org.folio.rest.api.TestBase.get;
import static org.folio.rest.support.http.InterfaceUrls.instanceReindex;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

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
  public void cancelReindexJob(String id) {
    var statusCode = get(client.delete(instanceReindex("/" + id), TENANT_ID)).getStatusCode();

    assertThat(statusCode, is(204));
  }

  @SneakyThrows
  public ReindexJob getReindexJob(String id) {
    return get(client.get(instanceReindex("/" + id), TENANT_ID)
      .thenApply(Response::getJson)
      .thenApply(json -> json.mapTo(ReindexJob.class)));
  }
}
