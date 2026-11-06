package org.folio.services.migration.async;

import static org.apache.logging.log4j.LogManager.getLogger;
import static org.folio.persist.ItemRepository.ITEM_TABLE;
import static org.folio.rest.tools.utils.TenantTool.tenantId;
import static org.folio.services.migration.MigrationName.ITEM_ORDER_MIGRATION;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.Logger;
import org.folio.persist.ItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ItemOrderMigrationService implements AsyncBaseMigrationService {

  private static final Logger log = getLogger(ItemOrderMigrationService.class);

  private static final String ITEM_QUERY = """
    SELECT jsonb FROM %s
    WHERE holdingsrecordid = $1
    ORDER BY lower(jsonb ->> 'barcode'::text)
    FOR UPDATE;
    """;
  private static final String JSONB_COLUMN = "jsonb";

  private static final String ORDER_FIELD = "order";

  private final ItemRepository itemRepository;

  @Autowired
  public ItemOrderMigrationService(ItemRepository itemRepository) {
    this.itemRepository = itemRepository;
  }

  @Override
  public Future<Void> runMigrationForIds(Set<String> ids, Map<String, String> headers) {
    var futures = ids.stream()
      .map(holdingsId -> processItemsForHoldings(holdingsId, headers))
      .toList();
    return Future.all(futures).mapEmpty();
  }

  @Override
  public String getMigrationName() {
    return ITEM_ORDER_MIGRATION.getValue();
  }

  private Future<Void> processItemsForHoldings(String holdingsId, Map<String, String> headers) {
    var sql = ITEM_QUERY.formatted(itemRepository.getFullTableName(tenantId(headers)));
    return itemRepository.inTransaction(tenantId(headers), conn -> conn.execute(sql, Tuple.of(holdingsId))
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
