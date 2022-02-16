package org.folio.services.migration.async;

import org.folio.rest.jaxrs.model.AffectedEntity;
import org.folio.rest.jaxrs.model.AsyncMigrationJob;

import java.util.List;

public interface AsyncMigrationJobRunner {

  void startAsyncMigration(AsyncMigrationJob migrationJob, AsyncMigrationContext context);

  String getMigrationName();

  List<AffectedEntity> getAffectedEntities();
}
