package org.folio.services.migration.item;

import static org.folio.services.migration.MigrationName.ITEM_SHELVING_ORDER_MIGRATION;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowStream;
import java.util.List;
import java.util.Map;
import org.folio.persist.ItemRepository;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.EffectiveCallNumberComponentsUtil;
import org.folio.rest.support.PostgresClientFactory;
import org.folio.services.migration.async.AsyncBaseMigrationService;
import org.folio.utils.DatabaseUtils;

public class ItemShelvingOrderMigrationService extends AsyncBaseMigrationService {
  private static final String SELECT_SQL = "SELECT jsonb FROM %s WHERE "
                                           + "jsonb->>'effectiveShelvingOrder' IS NULL";

  private final PostgresClient postgresClient;
  private final ItemRepository itemRepository;

  public ItemShelvingOrderMigrationService(Context context, Map<String, String> okapiHeaders) {
    this(PostgresClientFactory.getInstance(context, okapiHeaders),
      new ItemRepository(context, okapiHeaders));
  }

  public ItemShelvingOrderMigrationService(PostgresClient postgresClient, ItemRepository itemRepository) {
    super("20.2.1", postgresClient);
    this.postgresClient = postgresClient;
    this.itemRepository = itemRepository;
  }

  @Override
  public String getMigrationName() {
    return ITEM_SHELVING_ORDER_MIGRATION.getValue();
  }

  @Override
  protected Future<RowStream<Row>> openStream(Conn connection) {
    return DatabaseUtils.selectStream(connection, selectSql());
  }

  @Override
  protected Future<Integer> updateBatch(List<Row> batch, Conn connection) {
    var items = batch.stream()
      .map(row -> rowToClass(row, Item.class))
      .map(EffectiveCallNumberComponentsUtil::calculateAndSetEffectiveShelvingOrder)
      .toList();

    return itemRepository.updateBatch(items, connection).map(notUsed -> items.size());
  }

  protected String selectSql() {
    return String.format(SELECT_SQL, postgresClient.getSchemaName() + ".item");
  }
}
