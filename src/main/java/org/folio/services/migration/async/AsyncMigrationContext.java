package org.folio.services.migration.async;

import io.vertx.core.Context;
import java.util.Map;
import org.folio.rest.persist.PostgresClientFuturized;

public class AsyncMigrationContext {
  private final Context vertxContext;
  private final Map<String, String> okapiHeaders;
  private final PostgresClientFuturized postgresClient;
  private String migrationName;

  public AsyncMigrationContext(AsyncMigrationContext context, String migrationName) {
    this.vertxContext = context.getVertxContext();
    this.okapiHeaders = context.getOkapiHeaders();
    this.postgresClient = context.getPostgresClient();
    this.migrationName = migrationName;
  }

  public AsyncMigrationContext(Context vertxContext, Map<String, String> okapiHeaders,
                               PostgresClientFuturized postgresClient) {
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

  public String getMigrationName() {
    return migrationName;
  }
}
