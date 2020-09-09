package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import javax.ws.rs.core.Response;

import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.rest.RestVerticle;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;

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

  protected static <T> void postSync(String table, List<T> entities, Map<String, String> okapiHeaders,
      boolean upsert,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext, Supplier<Response> respond201) {
    PostgresClient postgresClient = PgUtil.postgresClient(vertxContext, okapiHeaders);

    Handler<AsyncResult<RowSet<Row>>> replyHandler = result -> {
      if (result.failed()) {
        logger.error("postSync: " + result.cause().getMessage(), result.cause());
        ValidationHelper.handleError(result.cause(), asyncResultHandler);
        return;
      }
      asyncResultHandler.handle(Future.succeededFuture(respond201.get()));
    };
    if (upsert) {
      postgresClient.upsertBatch(table, entities, replyHandler);
    } else {
      postgresClient.saveBatch(table, entities, replyHandler);
    }
  }

  public static <T> Future<T> completeFuture(T id) {
    Promise<T> p = Promise.promise();
    p.complete(id);
    return p.future();
  }


}
