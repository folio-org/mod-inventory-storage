package org.folio.services.holding;

import static org.apache.logging.log4j.LogManager.getLogger;

import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Logger;
import org.folio.dbschema.ObjectMapperTool;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;

final class HoldingsUpsertResultProcessor {
  private static final Logger log = getLogger(HoldingsUpsertResultProcessor.class);

  private HoldingsUpsertResultProcessor() {
    throw new UnsupportedOperationException("Do not instantiate utility class");
  }

  static Pair<Map<String, HoldingsRecord>, Map<String, List<Item>>> processUpsertResultSet(RowSet<Row> rowSet) {
    var oldHoldingsMap = new HashMap<String, HoldingsRecord>();
    var oldItemsMap = new HashMap<String, List<Item>>();

    for (var row : rowSet) {
      var id = row.getUUID(0).toString();
      var oldHoldingsContent = row.getString(1);
      var oldItemContent = row.getString(2);

      processOldHoldingsContent(id, oldHoldingsContent, oldHoldingsMap);
      processOldItemContent(id, oldItemContent, oldItemsMap);
    }

    return Pair.of(oldHoldingsMap, oldItemsMap);
  }

  private static void processOldHoldingsContent(String id, String oldHoldingsContent,
                                                Map<String, HoldingsRecord> oldHoldingsMap) {
    if (!"null".equals(oldHoldingsContent) && !oldHoldingsMap.containsKey(id)) {
      try {
        var oldHolding = ObjectMapperTool.readValue(oldHoldingsContent, HoldingsRecord.class);
        oldHoldingsMap.put(id, oldHolding);
      } catch (Exception e) {
        log.warn("Failed to parse old holdings record content for id: {}", id, e);
      }
    }
  }

  private static void processOldItemContent(String id, String oldItemContent,
                                            Map<String, List<Item>> oldItemsMap) {
    if (oldItemContent != null) {
      try {
        var oldItem = ObjectMapperTool.readValue(oldItemContent, Item.class);
        oldItemsMap.computeIfAbsent(id, k -> new ArrayList<>()).add(oldItem);
      } catch (Exception e) {
        log.warn("Failed to parse old item record content for holdings {}", id, e);
      }
    }
  }
}
