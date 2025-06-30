package org.folio.services.migration.async;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowStream;
import java.util.Map;
import java.util.stream.Collectors;
import org.folio.persist.ItemRepository;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.PostgresClientFactory;
import org.folio.services.migration.item.ItemShelvingOrderMigrationService;
import org.folio.utils.DatabaseUtils;

public class ShelvingOrderAsyncMigrationService extends ItemShelvingOrderMigrationService {

  private static final String SELECT_SQL = "SELECT jsonb FROM %s WHERE "
                                           + "id in (%s) FOR UPDATE";

  private final PostgresClient postgresClient;

  public ShelvingOrderAsyncMigrationService(Context context, Map<String, String> okapiHeaders) {
    this(PostgresClientFactory.getInstance(context, okapiHeaders), new ItemRepository(context, okapiHeaders));
  }

  public ShelvingOrderAsyncMigrationService(PostgresClient postgresClient,
                                            ItemRepository itemRepository) {

    super(postgresClient, itemRepository);
    this.postgresClient = postgresClient;
  }

  @Override
  protected Future<RowStream<Row>> openStream(Conn connection) {
    return DatabaseUtils.selectStream(connection, selectSql());
  }

  @Override
  protected String selectSql() {
    String ids = getIdsForMigration().stream()
      .map(id -> "'" + id + "'")
      .collect(Collectors.joining(", "));
    return String.format(SELECT_SQL, postgresClient.getSchemaName() + ".item", ids);
  }
}
