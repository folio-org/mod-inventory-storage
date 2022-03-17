package org.folio.services.migration.async;

import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowStream;
import org.folio.rest.jaxrs.model.AffectedEntity;
import org.folio.rest.persist.PostgresClientFuturized;
import org.folio.rest.persist.SQLConnection;

import java.util.Collections;
import java.util.List;

import static java.lang.String.format;

public class ShelvingOrderMigrationJobRunner extends AbstractAsyncMigrationJobRunner {

  private static final String SELECT_SQL = "SELECT id FROM %s " +
    "WHERE jsonb->>'effectiveCallNumberComponents' IS NOT NULL";

  @Override
  protected Future<RowStream<Row>> openStream(PostgresClientFuturized postgresClient, SQLConnection connection) {
    return postgresClient.selectStream(connection, format(SELECT_SQL, postgresClient.getFullTableName("item")));
  }

  @Override
  public String getMigrationName() {
    return "itemShelvingOrderMigration";
  }

  @Override
  public List<AffectedEntity> getAffectedEntities() {
    return Collections.singletonList(AffectedEntity.ITEM);
  }
}
