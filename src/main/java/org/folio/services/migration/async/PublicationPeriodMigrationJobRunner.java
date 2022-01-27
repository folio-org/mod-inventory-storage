package org.folio.services.migration.async;

import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.AffectedEntity;
import org.folio.rest.jaxrs.model.AsyncMigrationJob;
import org.folio.rest.persist.PostgresClientFuturized;
import org.folio.rest.persist.SQLConnection;

import java.util.Collections;
import java.util.List;

import static java.lang.String.format;

public class PublicationPeriodMigrationJobRunner extends AbstractAsyncMigrationJobRunner implements AsyncMigrationJobRunner {

  private static final String SELECT_SQL = "SELECT id FROM %s WHERE jsonb->>'publicationPeriod' IS NULL";
  private static final Logger log = LogManager.getLogger(PublicationPeriodMigrationJobRunner.class);

  @Override
  public void startAsyncMigration(AsyncMigrationJob migrationJob, AsyncMigrationContext context) {
    context.getVertxContext().executeBlocking(v -> {
      log.info("Starting async migration for class [class={}]", getClass());
      startMigration(migrationJob, context)
        .onComplete(result -> v.complete());
    });
  }

  @Override
  protected Future<RowStream<Row>> openStream(PostgresClientFuturized postgresClient, SQLConnection connection) {
    return postgresClient.selectStream(connection, format(SELECT_SQL, postgresClient.getFullTableName("instance")));
  }

  @Override
  public String getMigrationName() {
    return "publicationPeriodMigration";
  }

  @Override
  public List<AffectedEntity> getAffectedEntities() {
    return Collections.singletonList(AffectedEntity.INSTANCE);
  }
}
