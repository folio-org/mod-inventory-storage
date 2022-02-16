package org.folio.services.migration.async;

import io.vertx.core.Context;
import org.folio.rest.persist.PostgresClientFuturized;

import java.util.Map;

public class AsyncMigrationContext {
  private final Context vertxContext;
  private final Map<String, String> okapiHeaders;
  private final PostgresClientFuturized postgresClient;

  public AsyncMigrationContext(Context vertxContext, Map<String, String> okapiHeaders, PostgresClientFuturized postgresClient) {
    this.vertxContext = vertxContext;
    this.okapiHeaders = okapiHeaders;
    this.postgresClient = postgresClient;
  }

  public Context getVertxContext() {
    return vertxContext;
  }

  public Map<String, String> getOkapiHeaders() {
    return okapiHeaders;
  }

  public PostgresClientFuturized getPostgresClient() {
    return postgresClient;
  }
}
