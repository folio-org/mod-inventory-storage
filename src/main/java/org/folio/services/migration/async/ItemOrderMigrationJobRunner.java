package org.folio.services.migration.async;

import static java.lang.String.format;
import static org.folio.rest.impl.ItemStorageApi.ITEM_TABLE;
import static org.folio.services.migration.MigrationName.ITEM_ORDER_MIGRATION;

import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowStream;
import java.util.Collections;
import java.util.List;
import org.folio.rest.jaxrs.model.AffectedEntity;
import org.folio.rest.persist.Conn;
import org.folio.utils.DatabaseUtils;

public class ItemOrderMigrationJobRunner extends AbstractAsyncMigrationJobRunner {

  private static final String SELECT_SQL = "SELECT DISTINCT(holdingsrecordid) as id FROM %s;";

  @Override
  public String getMigrationName() {
    return ITEM_ORDER_MIGRATION.getValue();
  }

  @Override
  public List<AffectedEntity> getAffectedEntities() {
    return Collections.singletonList(AffectedEntity.ITEM);
  }

  @Override
  protected Future<RowStream<Row>> openStream(String schemaName, Conn connection) {
    var query = format(SELECT_SQL, schemaName + "." + ITEM_TABLE);
    return DatabaseUtils.selectStream(connection, query);
  }
}
