package org.folio.persist;

import io.vertx.core.Context;
import org.folio.rest.jaxrs.model.AsyncMigrationJob;
import org.springframework.stereotype.Repository;

@Repository
public class AsyncMigrationJobRepository extends AbstractRepository<AsyncMigrationJob> {

  public static final String TABLE_NAME = "async_migration_job";

  public AsyncMigrationJobRepository(Context context) {
    super(context, TABLE_NAME, AsyncMigrationJob.class);
  }
}
