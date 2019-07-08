package org.folio.rest.impl;

import io.vertx.core.Context;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.Map;
import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.rest.RestVerticle;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.utils.TenantTool;

/**
 * Small helpers for mod-inventory-storage.
 */
public final class StorageHelper {

  private static Logger logger = LoggerFactory.getLogger(StorageHelper.class);

  private StorageHelper() {
    throw new UnsupportedOperationException("Cannot instantiate utility class");
  }

  protected static String logAndSaveError(Throwable err) {
    String message = err.getLocalizedMessage();
    logger.error(message, err);
    return message;
  }

  protected static boolean isDuplicate(String message) {
    return message != null && message.contains("duplicate key value violates unique constraint");
  }

  protected static boolean isInUse(String message) {
    return message != null && message.contains("is still referenced");
  }

  protected static CQLWrapper getCQL(String query,
    int limit, int offset, String tableName) throws FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(tableName + ".jsonb");
    return new CQLWrapper(cql2pgJson, query)
      .setLimit(new Limit(limit))
      .setOffset(new Offset(offset));
  }

  protected static String getTenant(Map<String, String> headers) {
    return TenantTool.calculateTenantId(headers.get(RestVerticle.OKAPI_HEADER_TENANT));
  }

  /**
   * Return a PostgresClient.
   * @param vertxContext  Where to get a Vertx from.
   * @param okapiHeaders  Where to get the tenantId from.
   * @return the PostgresClient for the vertx and the tenantId
   */
  protected static PostgresClient postgresClient(Context vertxContext, Map<String, String> okapiHeaders) {
    return PostgresClient.getInstance(vertxContext.owner(), TenantTool.tenantId(okapiHeaders));
  }

}
