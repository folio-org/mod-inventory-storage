package org.folio.rest.support.fixtures;

import static org.folio.rest.api.TestBase.get;
import static org.folio.rest.support.http.InterfaceUrls.migrationJobsUrl;
import static org.folio.rest.support.http.InterfaceUrls.migrationsUrl;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import lombok.SneakyThrows;
import org.folio.rest.jaxrs.model.AsyncMigrationJob;
import org.folio.rest.jaxrs.model.AsyncMigrationJobCollection;
import org.folio.rest.jaxrs.model.AsyncMigrationJobRequest;
import org.folio.rest.jaxrs.model.AsyncMigrations;
import org.folio.rest.support.HttpClient;
import org.folio.rest.support.Response;

public final class AsyncMigrationFixture {
  private final HttpClient client;

  public AsyncMigrationFixture(HttpClient httpClient) {
    this.client = httpClient;
  }

  @SneakyThrows
  public void cancelMigrationJob(String id) {
    var statusCode = get(client.delete(migrationJobsUrl("/" + id), TENANT_ID)).getStatusCode();
    assertThat(statusCode, is(204));
  }

  @SneakyThrows
  public AsyncMigrationJob getMigrationJob(String id) {
    return get(client.get(migrationJobsUrl("/" + id), TENANT_ID)
      .thenApply(Response::getJson)
      .thenApply(json -> json.mapTo(AsyncMigrationJob.class)));
  }

  @SneakyThrows
  public AsyncMigrations getMigrations() {
    return get(client.get(migrationsUrl(""), TENANT_ID)
      .thenApply(Response::getJson)
      .thenApply(json -> json.mapTo(AsyncMigrations.class)));
  }

  @SneakyThrows
  public AsyncMigrationJobCollection getAllMigrationJobs() {
    return get(client.get(migrationJobsUrl(""), TENANT_ID)
      .thenApply(Response::getJson)
      .thenApply(json -> json.mapTo(AsyncMigrationJobCollection.class)));
  }

  @SneakyThrows
  public AsyncMigrationJob postMigrationJob(AsyncMigrationJobRequest job) {
    return get(client.post(migrationJobsUrl(""), job, TENANT_ID)
      .thenApply(Response::getJson)
      .thenApply(json -> json.mapTo(AsyncMigrationJob.class)));
  }

  @SneakyThrows
  public Response postMigrationJobRequest(AsyncMigrationJobRequest job) {
    return get(client.post(migrationJobsUrl(""), job, TENANT_ID));
  }
}
