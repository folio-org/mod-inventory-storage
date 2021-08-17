package org.folio.persist;

import static org.folio.rest.impl.ItemStorageAPI.ITEM_TABLE;
import static org.folio.rest.persist.PgUtil.postgresClient;

import java.util.List;
import java.util.Map;

import io.vertx.core.AsyncResult;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.Context;
import io.vertx.core.Future;
import org.folio.rest.persist.SQLConnection;

public class ItemRepository extends AbstractRepository<Item> {
  public ItemRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), ITEM_TABLE, Item.class);
  }

  public Future<List<Item>> getItemsForHoldingRecord(AsyncResult<SQLConnection> connection, String holdingRecordId) {
    final Criterion criterion = new Criterion(new Criteria().setJSONB(false)
      .addField("holdingsRecordId").setOperation("=").setVal(holdingRecordId));

    return get(connection, criterion);
  }
}
