package org.folio.rest.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.cql.CQLWrapper;

/**
 * Small helpers for mod-inventory-storage.
 */
public final class StorageHelper {

  /**
   * Limit for PgUtil.postSync to avoid out-of-memory.
   */
  public static final int MAX_ENTITIES = 10000;

  private static final Logger logger = LogManager.getLogger();

  private StorageHelper() {
    throw new UnsupportedOperationException("Cannot instantiate utility class");
  }

  public static String logAndSaveError(Throwable err) {
    String message = err.getLocalizedMessage();
    logger.error(message, err);
    return message;
  }

  static boolean isDuplicate(String message) {
    return message != null && message.contains("duplicate key value violates unique constraint");
  }

  static boolean isInUse(String message) {
    return message != null && message.contains("is still referenced");
  }

  static CQLWrapper getCql(String query,
                           int limit, int offset, String tableName) throws FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(tableName + ".jsonb");
    return new CQLWrapper(cql2pgJson, query)
      .setLimit(new Limit(limit))
      .setOffset(new Offset(offset));
  }
}
