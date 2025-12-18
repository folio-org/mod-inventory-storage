package org.folio.rest.support.db;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.security.InvalidParameterException;
import org.folio.rest.tools.utils.ResourceUtils;

/**
 * Report optimistic locking configuration of schema.json.
 */
public class OptimisticLocking {
  private static final JsonArray TABLES =
    new JsonObject(ResourceUtils.resource2String("templates/db_scripts/schema.json"))
      .getJsonArray("tables");

  /**
   * true if tableName has failOnConflict or failOnConflictUnlessSuppressed, false otherwise.
   */
  public static boolean hasFailOnConflict(String tableName) {
    for (int i = 0; i < TABLES.size(); i++) {
      JsonObject table = TABLES.getJsonObject(i);
      if (tableName.equals(table.getString("tableName"))) {
        return switch (table.getString("withOptimisticLocking", "")) {
          case "failOnConflict", "failOnConflictUnlessSuppressed" -> true;
          default -> false;
        };
      }
    }
    throw new InvalidParameterException("Table not found: " + tableName);
  }
}
