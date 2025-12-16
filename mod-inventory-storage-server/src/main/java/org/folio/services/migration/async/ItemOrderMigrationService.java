package org.folio.services.migration.async;

import static org.apache.logging.log4j.LogManager.getLogger;
import static org.folio.rest.impl.ItemStorageApi.ITEM_TABLE;
import static org.folio.services.migration.MigrationName.ITEM_ORDER_MIGRATION;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.RowStream;
import io.vertx.sqlclient.Tuple;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.Logger;
import org.folio.persist.ItemRepository;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;

public class ItemOrderMigrationService extends AsyncBaseMigrationService {

  private static final Logger log = getLogger(ItemOrderMigrationService.class);

  private static final String FROM_VERSION = "29.1.0";

  private static final String ITEM_QUERY = """
    SELECT jsonb FROM %s
    WHERE holdingsrecordid = $1
    ORDER BY lower(jsonb ->> 'barcode'::text)
    FOR UPDATE;
    """;
  private static final String JSONB_COLUMN = "jsonb";

  private static final String ORDER_FIELD = "order";

  protected final PostgresClient postgresClient;
  protected final ItemRepository itemRepository;

  public ItemOrderMigrationService(PostgresClient postgresClient, ItemRepository itemRepository) {
    super(FROM_VERSION, postgresClient);
    this.postgresClient = postgresClient;
    this.itemRepository = itemRepository;
  }

  public ItemOrderMigrationService(Context context, Map<String, String> headers) {
    this(PgUtil.postgresClient(context, headers), new ItemRepository(context, headers));
  }

  @Override
  public Future<Void> runMigrationForIds(Set<String> ids) {
    var futures = ids.stream()
      .map(this::processItemsForHoldings)
      .toList();
    return Future.all(futures).mapEmpty();
  }

  @Override
  public String getMigrationName() {
    return ITEM_ORDER_MIGRATION.getValue();
  }

  @Override
  protected Future<RowStream<Row>> openStream(Conn connection) {
    throw new UnsupportedOperationException("This method is not supported in ItemOrderMigrationService");
  }

  @Override
  protected Future<Integer> updateBatch(List<Row> batch, Conn connection) {
    throw new UnsupportedOperationException("This method is not supported in ItemOrderMigrationService");
  }

  private Future<Void> processItemsForHoldings(String holdingsId) {
    var sql = ITEM_QUERY.formatted(itemRepository.getFullTableName());
    return postgresClient.withTrans(conn -> conn.execute(sql, Tuple.of(holdingsId))
        .map(this::mapAndUpdateItems)
        .compose(items -> {
          log.info("Migration: {} :: Updating items for holdings ID: {}", getMigrationName(), holdingsId);
          return conn.updateBatch(ITEM_TABLE, items);
        }))
      .mapEmpty();
  }

  private JsonArray mapAndUpdateItems(RowSet<Row> rows) {
    JsonArray items = new JsonArray();
    int order = 1;
    for (Row row : rows) {
      var item = row.getJsonObject(JSONB_COLUMN);
      item.put(ORDER_FIELD, order++);
      items.add(item);
    }
    return items;
  }
}
