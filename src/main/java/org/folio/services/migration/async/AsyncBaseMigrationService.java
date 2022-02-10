package org.folio.services.migration.async;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.AsyncMigrationJob;
import org.folio.rest.persist.PostgresClientFuturized;
import org.folio.services.migration.BaseMigrationService;

import java.util.HashSet;
import java.util.Set;

public abstract class AsyncBaseMigrationService extends BaseMigrationService {
  private volatile Set<String> idsForMigration = new HashSet<>();
  private volatile AsyncMigrationJob migrationJob;

  protected AsyncBaseMigrationService(String fromVersion, PostgresClientFuturized client) {
    super(fromVersion, client);
  }

  public Future<Void> runMigrationForIds(Set<String> ids, AsyncMigrationJob job) {
    this.idsForMigration = ids;
    this.migrationJob = job;
    return runMigration();
  }

  protected Set<String> getIdsForMigration() {
    return idsForMigration;
  }

  protected AsyncMigrationJob getMigrationJob() {
    return migrationJob;
  }
}
