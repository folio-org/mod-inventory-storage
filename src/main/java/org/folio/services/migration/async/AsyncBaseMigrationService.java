package org.folio.services.migration.async;

import io.vertx.core.Future;
import org.folio.rest.persist.PostgresClientFuturized;
import org.folio.services.migration.BaseMigrationService;

import java.util.HashSet;
import java.util.Set;

public abstract class AsyncBaseMigrationService extends BaseMigrationService {
  private Set<String> idsForMigration = new HashSet<>();

  protected AsyncBaseMigrationService(String fromVersion, PostgresClientFuturized client) {
    super(fromVersion, client);
  }

  public Future<Void> runMigrationForIds(Set<String> ids) {
    this.idsForMigration = ids;
    return runMigration();
  }

  protected Set<String> getIdsForMigration() {
    return idsForMigration;
  }

}
