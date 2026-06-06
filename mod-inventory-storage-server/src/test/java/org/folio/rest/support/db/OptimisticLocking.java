package org.folio.rest.support.db;

import java.util.Map;

/**
 * Report optimistic locking configuration of schema.json.
 */
public class OptimisticLocking {

  private static final Map<String, String> TABLES = Map.of(
    "instance", "failOnConflictUnlessSuppressed",
    "item", "failOnConflictUnlessSuppressed",
    "holdings-record", "failOnConflictUnlessSuppressed"
  );

  /**
   * true if tableName has failOnConflict or failOnConflictUnlessSuppressed, false otherwise.
   */
  public static boolean hasFailOnConflict(String tableName) {
    return switch (TABLES.get(tableName)) {
      case "failOnConflict", "failOnConflictUnlessSuppressed" -> true;
      default -> false;
    };
  }
}
