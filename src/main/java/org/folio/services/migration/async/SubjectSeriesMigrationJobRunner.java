package org.folio.services.migration.async;

import static java.lang.String.format;

import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowStream;
import java.util.Collections;
import java.util.List;
import org.folio.rest.jaxrs.model.AffectedEntity;
import org.folio.rest.persist.PostgresClientFuturized;
import org.folio.rest.persist.SQLConnection;

public class SubjectSeriesMigrationJobRunner extends AbstractAsyncMigrationJobRunner {

  private static final String SELECT_SQL = "SELECT id FROM %s "
    + "WHERE jsonb->'subjects' <> '[]' OR jsonb->'series' <> '[]'";

  @Override
  public String getMigrationName() {
    return "subjectSeriesMigration";
  }

  @Override
  public List<AffectedEntity> getAffectedEntities() {
    return Collections.singletonList(AffectedEntity.INSTANCE);
  }

  @Override
  protected Future<RowStream<Row>> openStream(PostgresClientFuturized postgresClient, SQLConnection connection) {
    return postgresClient.selectStream(connection, format(SELECT_SQL, postgresClient.getFullTableName("instance")));
  }
}
