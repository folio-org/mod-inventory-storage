package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.RecordBulkIdsGetField;
import org.folio.rest.jaxrs.model.RecordBulkIdsGetRecordType;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.support.RecordID;

import javax.ws.rs.core.Response;
import java.util.Map;

public class RecordBulkAPI implements org.folio.rest.jaxrs.resource.RecordBulk {
  public static final String INSTANCE_TABLE = "instance";
  public static final String HOLDING_TABLE = "holdings_record";
  public static final String HOLDING_TYPE = "HOLDING";

  private static final Logger LOG = LogManager.getLogger();

  private CQLWrapper getCQL(String query, String table, int limit, int offset) throws FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(table + ".jsonb");
    return new CQLWrapper(cql2pgJson, query);
  }

  @Validate
  @Override
  public void getRecordBulkIds(RecordBulkIdsGetField field, RecordBulkIdsGetRecordType recordType, String query,
                               int offset, int limit, String lang, RoutingContext routingContext,
                               Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
                               Context vertxContext) {
      try {
        if (recordType.toString().equalsIgnoreCase(HOLDING_TYPE)) {
          CQLWrapper wrapper = getCQL(query, HOLDING_TABLE, limit, offset);
          PgUtil.streamGet(HOLDING_TABLE, RecordID.class, wrapper, null,
            "ids", routingContext, okapiHeaders, vertxContext);
        } else {
          CQLWrapper wrapper = getCQL(query, INSTANCE_TABLE, limit, offset);
          PgUtil.streamGet(INSTANCE_TABLE, RecordID.class, wrapper, null,
            "ids", routingContext, okapiHeaders, vertxContext);
        }
      } catch (Exception e) {
        LOG.error(e.getMessage(), e);
        asyncResultHandler.handle(Future.succeededFuture(GetRecordBulkIdsResponse
          .respond500WithTextPlain(e.getMessage())));
      }
  }
}
