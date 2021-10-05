package org.folio.rest.support.db;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.security.InvalidParameterException;
import org.folio.rest.tools.utils.ResourceUtils;

/**
 * Report optimistic locking configuration of schema.json.
 */
public class OptimisticLocking {
  private static final JsonArray tables =
      new JsonObject(ResourceUtils.resource2String("templates/db_scripts/schema.json"))
      .getJsonArray("tables");

  public static boolean hasFailOnConflict(String tableName) {
    for (int i = 0; i < tables.size(); i++) {
      JsonObject table = tables.getJsonObject(i);
      if (tableName.equals(table.getString("tableName"))) {
        return "failOnConflict".equals(table.getString("withOptimisticLocking"));
      }
    }
    throw new InvalidParameterException("Table not found: " + tableName);
  }
}
