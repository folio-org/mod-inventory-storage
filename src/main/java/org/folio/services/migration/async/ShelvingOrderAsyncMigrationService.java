package org.folio.services.migration.async;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowStream;
import org.folio.persist.ItemRepository;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClientFuturized;
import org.folio.rest.persist.SQLConnection;
import org.folio.services.migration.item.ItemShelvingOrderMigrationService;

import java.util.Map;
import java.util.stream.Collectors;

public class ShelvingOrderAsyncMigrationService extends ItemShelvingOrderMigrationService {

  private static final String SELECT_SQL = "SELECT jsonb FROM %s WHERE "
    + "id in (%s) FOR UPDATE";

  private final PostgresClientFuturized postgresClient;

  public ShelvingOrderAsyncMigrationService(Context context, Map<String, String> okapiHeaders) {
    this(new PostgresClientFuturized(PgUtil.postgresClient(context, okapiHeaders)),
      new ItemRepository(context, okapiHeaders));
  }

  public ShelvingOrderAsyncMigrationService(PostgresClientFuturized postgresClient,
                                            ItemRepository itemRepository) {

    super(postgresClient, itemRepository);
    this.postgresClient = postgresClient;
  }

  @Override
  protected Future<RowStream<Row>> openStream(SQLConnection connection) {
    return postgresClient.selectStream(connection, selectSql());
  }

  @Override
  protected String selectSql() {
    String ids = getIdsForMigration().stream()
      .map(id -> "'" + id + "'")
      .collect(Collectors.joining(", "));
    return String.format(SELECT_SQL, postgresClient.getFullTableName("item"), ids);
  }

}
