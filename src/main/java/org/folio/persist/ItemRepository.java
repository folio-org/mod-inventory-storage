package org.folio.persist;

import static java.lang.String.format;
import static org.folio.rest.impl.HoldingsStorageAPI.ITEM_TABLE;
import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.rest.tools.utils.TenantTool.tenantId;

import java.util.List;
import java.util.Map;

import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

public class ItemRepository extends AbstractRepository<Item> {
  private final String tenantId;

  public ItemRepository(Context context, Map<String, String> okapiHeaders) {
    this(postgresClient(context, okapiHeaders), tenantId(okapiHeaders));
  }

  public ItemRepository(PostgresClient postgresClient, String tenantId) {
    super(postgresClient, ITEM_TABLE, Item.class);
    this.tenantId = tenantId;
  }

  public Future<List<Item>> getAll() {
    return postgresClientFuturized.get(tableName, new Item());
  }

  public Future<List<Item>> getItemsForHoldingRecord(String holdingRecordId) {
    final Criterion criterion = new Criterion(new Criteria().setJSONB(false)
      .addField("holdingsRecordId").setOperation("=").setVal(holdingRecordId));

    return get(criterion);
  }

  public Future<RowSet<Row>> deleteAll() {
    final String removeAllQuery = format("DELETE FROM %s_mod_inventory_storage.%s",
      tenantId, ITEM_TABLE);

    return postgresClientFuturized.execute(removeAllQuery);
  }
}
