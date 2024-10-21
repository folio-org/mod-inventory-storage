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
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClientFuturized;
import org.folio.rest.persist.SQLConnection;
import org.folio.rest.support.EffectiveCallNumberComponentsUtil;
import org.folio.services.migration.async.AsyncBaseMigrationService;

public class ItemShelvingOrderMigrationService extends AsyncBaseMigrationService {
  private static final String SELECT_SQL = "SELECT jsonb FROM %s WHERE "
    + "jsonb->>'effectiveShelvingOrder' IS NULL";

  private final PostgresClientFuturized postgresClient;
  private final ItemRepository itemRepository;

  public ItemShelvingOrderMigrationService(Context context, Map<String, String> okapiHeaders) {
    this(new PostgresClientFuturized(PgUtil.postgresClient(context, okapiHeaders)),
      new ItemRepository(context, okapiHeaders));
  }

  public ItemShelvingOrderMigrationService(
    PostgresClientFuturized postgresClient, ItemRepository itemRepository) {

    super("20.2.1", postgresClient);
    this.postgresClient = postgresClient;
    this.itemRepository = itemRepository;
  }

  @Override
  protected Future<RowStream<Row>> openStream(SQLConnection connection) {
    return postgresClient.selectStream(connection, selectSql());
  }

  @Override
  protected Future<Integer> updateBatch(List<Row> batch, SQLConnection connection) {
    var items = batch.stream()
      .map(row -> rowToClass(row, Item.class))
      .map(EffectiveCallNumberComponentsUtil::calculateAndSetEffectiveShelvingOrder)
      .toList();

    return itemRepository.updateBatch(items, connection).map(notUsed -> items.size());
  }

  @Override
  public String getMigrationName() {
    return ITEM_SHELVING_ORDER_MIGRATION.getValue();
  }

  protected String selectSql() {
    return String.format(SELECT_SQL, postgresClient.getFullTableName("item"));
  }
}
