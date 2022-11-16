package org.folio.rest.support.fixtures;

import static org.folio.rest.api.TestBase.get;
import static org.folio.rest.support.http.InterfaceUrls.instanceIteration;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import lombok.SneakyThrows;
import org.folio.rest.jaxrs.model.IterationJob;
import org.folio.rest.support.HttpClient;
import org.folio.rest.support.Response;

public final class InstanceIterationFixture {

  private final HttpClient client;

  public InstanceIterationFixture(HttpClient httpClient) {
    this.client = httpClient;
  }

  @SneakyThrows
  public void cancelIterationJob(String id) {
    var statusCode = get(client.delete(instanceIteration("/" + id), TENANT_ID)).getStatusCode();

    assertThat(statusCode, is(204));
  }

  @SneakyThrows
  public IterationJob getIterationJob(String id) {
    return get(client.get(instanceIteration("/" + id), TENANT_ID)
      .thenApply(Response::getJson)
      .thenApply(json -> json.mapTo(IterationJob.class)));
  }

}
