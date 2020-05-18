package org.folio.rest.support.db;

import io.vertx.core.json.JsonArray;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowIterator;
import io.vertx.sqlclient.RowSet;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public class RowSetUtil {
  private RowSetUtil() {
  }

  /**
   * Map RowSet to JsonArray(s). One array per row.
   * @param rowSet
   * @return
   */
  public static List<JsonArray> rowSetToJsonArrays(RowSet<Row> rowSet) {
    RowIterator<Row> iterator = rowSet.iterator();
    List<JsonArray> list = new LinkedList<>();
    while (iterator.hasNext()) {
      Row row = iterator.next();
      JsonArray ar = new JsonArray();
      for (int i = 0; i < row.size(); i++) {
        Object obj = row.getValue(i);
        if (obj instanceof UUID) {
          ar.add(obj.toString());
        } else {
          ar.add(obj);
        }
      }
      list.add(ar);
    }
    return list;
  }

}
