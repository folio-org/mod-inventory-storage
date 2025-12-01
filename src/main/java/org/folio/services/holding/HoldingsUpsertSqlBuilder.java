package org.folio.services.holding;

import io.vertx.sqlclient.Tuple;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.persist.HoldingsRepository;
import org.folio.persist.ItemRepository;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.persist.PostgresClient;

class HoldingsUpsertSqlBuilder {
  private final HoldingsRepository holdingsRepository;
  private final ItemRepository itemRepository;

  HoldingsUpsertSqlBuilder(HoldingsRepository holdingsRepository, ItemRepository itemRepository) {
    this.holdingsRepository = holdingsRepository;
    this.itemRepository = itemRepository;
  }

  Pair<Pair<String, Tuple>, Exception> buildUpsertSqlWithParams(List<HoldingsRecord> holdings) {
    var sqlBuilder = new StringBuilder();
    var params = new ArrayList<>();
    var paramIndex = 1;

    sqlBuilder.append("WITH upsert_data AS (");
    for (int i = 0; i < holdings.size(); i++) {
      if (i > 0) {
        sqlBuilder.append(" UNION ALL ");
      }
      sqlBuilder.append("SELECT $").append(paramIndex++).append("::uuid as id, $")
        .append(paramIndex++).append("::jsonb as data");

      var holding = holdings.get(i);
      params.add(holding.getId());
      try {
        params.add(PostgresClient.pojo2JsonObject(holding));
      } catch (Exception e) {
        return Pair.of(null, e);
      }
    }

    var holdingsTableName = holdingsRepository.getFullTableName();
    var itemsTableName = itemRepository.getFullTableName();

    sqlBuilder.append("), ")
      .append(buildOldDataQueries(holdingsTableName, itemsTableName))
      .append(buildUpsertQuery(holdingsTableName))
      .append(buildCombinedResultsQuery())
      .append("SELECT id, old_holdings_content, old_item_content FROM combined_results");

    var sqlAndParams = Pair.of(sqlBuilder.toString(), Tuple.from(params));
    return Pair.of(sqlAndParams, null);
  }

  private String buildOldDataQueries(String holdingsTableName, String itemsTableName) {
    return "old_holdings_data AS ("
           + "  SELECT id, jsonb::text as old_content FROM " + holdingsTableName
           + "  WHERE id = ANY(SELECT id FROM upsert_data)"
           + "), "
           + "old_items_data AS ("
           + "  SELECT holdingsrecordid, jsonb::text as item_content FROM " + itemsTableName
           + "  WHERE holdingsrecordid = ANY(SELECT id FROM upsert_data)"
           + "), ";
  }

  private String buildUpsertQuery(String holdingsTableName) {
    return "updated AS ("
           + "  UPDATE " + holdingsTableName + " SET jsonb = upsert_data.data "
           + "  FROM upsert_data WHERE " + holdingsTableName + ".id = upsert_data.id "
           + "  RETURNING " + holdingsTableName + ".id"
           + "), "
           + "inserted AS ("
           + "  INSERT INTO " + holdingsTableName + " (id, jsonb) "
           + "  SELECT id, data FROM upsert_data "
           + "  WHERE id NOT IN (SELECT id FROM updated) "
           + "  RETURNING id"
           + "), "
           + "upserted AS ("
           + "  SELECT id FROM updated UNION ALL SELECT id FROM inserted"
           + "), ";
  }

  private String buildCombinedResultsQuery() {
    return "combined_results AS ("
           + "  SELECT "
           + "    u.id, "
           + "    COALESCE(oh.old_content, 'null') as old_holdings_content, "
           + "    oi.item_content as old_item_content"
           + "  FROM upserted u "
           + "  LEFT JOIN old_holdings_data oh ON u.id = oh.id"
           + "  LEFT JOIN old_items_data oi ON u.id = oi.holdingsrecordid"
           + ")";
  }
}
