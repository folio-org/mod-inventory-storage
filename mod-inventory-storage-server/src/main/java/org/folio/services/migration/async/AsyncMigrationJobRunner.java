package org.folio.services.migration.async;

import java.util.List;
import org.folio.rest.jaxrs.model.AffectedEntity;
import org.folio.rest.jaxrs.model.AsyncMigrationJob;

public interface AsyncMigrationJobRunner {

  void startAsyncMigration(AsyncMigrationJob migrationJob, AsyncMigrationContext context);

  String getMigrationName();

  List<AffectedEntity> getAffectedEntities();
}
