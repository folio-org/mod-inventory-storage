package org.folio.services.migration.async;

import io.vertx.core.Context;
import java.util.Map;
import org.folio.persist.AbstractRepository;

public class AsyncMigrationContext {
  private final Context vertxContext;
  private final Map<String, String> headers;
  private final AbstractRepository<?> repository;
  private final AsyncMigrationJobService migrationService;
  private String migrationName;

  public AsyncMigrationContext(AsyncMigrationContext context, String migrationName) {
    this.vertxContext = context.getVertxContext();
    this.headers = context.getOkapiHeaders();
    this.repository = context.getRepository();
    this.migrationService = context.getMigrationService();
    this.migrationName = migrationName;
  }

  public AsyncMigrationContext(Context vertxContext, Map<String, String> headers,
                               AbstractRepository<?> repository, AsyncMigrationJobService migrationService) {
    this.vertxContext = vertxContext;
    this.headers = headers;
    this.repository = repository;
    this.migrationService = migrationService;
  }

  public Context getVertxContext() {
    return vertxContext;
  }

  public Map<String, String> getOkapiHeaders() {
    return headers;
  }

  public AbstractRepository<?> getRepository() {
    return repository;
  }

  public AsyncMigrationJobService getMigrationService() {
    return migrationService;
  }

  public String getMigrationName() {
    return migrationName;
  }
}
