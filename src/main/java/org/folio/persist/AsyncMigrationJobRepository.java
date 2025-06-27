package org.folio.persist;

import static org.folio.rest.persist.PgUtil.postgresClient;

import io.vertx.core.Context;
import java.util.Map;
import org.folio.rest.jaxrs.model.AsyncMigrationJob;

public class AsyncMigrationJobRepository extends AbstractRepository<AsyncMigrationJob> {
  public static final String TABLE_NAME = "async_migration_job";

  public AsyncMigrationJobRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), TABLE_NAME, AsyncMigrationJob.class);
  }
}
