package org.folio.persist;

import io.vertx.core.Context;
import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.AsyncMigrationJob;

import java.util.Map;
import java.util.function.UnaryOperator;

import static org.folio.rest.persist.PgUtil.postgresClient;

public class AsyncMigrationJobRepository extends AbstractRepository<AsyncMigrationJob> {
  public static final String TABLE_NAME = "async_migration_job";

  public AsyncMigrationJobRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), TABLE_NAME, AsyncMigrationJob.class);
  }

  public Future<AsyncMigrationJob> fetchAndUpdate(String id, UnaryOperator<AsyncMigrationJob> builder) {
    return getById(id)
      .map(builder)
      .compose(response -> update(id, response)
        .map(response));
  }
}
