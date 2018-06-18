package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.resource.LocationUnitsResource;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.utils.TenantTool;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.FieldException;

/**
 * Small helpers for mod-inventory-storage
 */
public class StorageHelper {

  private static Logger logger = LoggerFactory.getLogger(LocationUnitAPI.class);

  private StorageHelper() {
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

  protected static Criterion idCriterion(String id, String schemaPath, Handler<AsyncResult<Response>> asyncResultHandler) {
    try {
      Criteria criteria = new Criteria(schemaPath);
      criteria.addField(LocationUnitAPI.ID_FIELD_NAME);
      criteria.setOperation("=");
      criteria.setValue(id);
      return new Criterion(criteria);
    } catch (Exception e) {
      String message = logAndSaveError(e);
      asyncResultHandler.handle(
        Future.succeededFuture(
          LocationUnitsResource.GetLocationUnitsInstitutionsByIdResponse
            .withPlainInternalServerError(message)));
      // This is a bit dirty, but all those wrappers return the same kind of
      // response for InternalServerError, so we can use this from anywhere
      return null;
    }
  }

}
