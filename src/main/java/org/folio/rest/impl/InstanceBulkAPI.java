package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.InstanceBulkIdsGetField;
import org.folio.rest.jaxrs.model.InstanceBulkIdsGetRecordType;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.support.RecordID;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;

public class InstanceBulkAPI implements org.folio.rest.jaxrs.resource.InstanceBulk {
  public static final String INSTANCE_TABLE = "instance";
  public static final String HOLDING_TABLE = "holdings_record";
  public static final String HOLDING_TYPE = "HOLDINGS";

  private static final Logger LOG = LoggerFactory.getLogger(InstanceBulkAPI.class);

  private CQLWrapper getCQL(String query, String table) throws FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(table + ".jsonb");
    return new CQLWrapper(cql2pgJson, query);
  }

  @Validate
  @Override
  public void getInstanceBulkIds(InstanceBulkIdsGetField field,
        InstanceBulkIdsGetRecordType recordType, String query, String lang,
        RoutingContext routingContext, Map<String, String> okapiHeaders,
        Handler<AsyncResult<Response>> asyncResultHandler,
        Context vertxContext) {

    try {
      if (recordType.toString().equalsIgnoreCase(HOLDING_TYPE)) {
        CQLWrapper wrapper = getCQL(query, HOLDING_TABLE);
        PgUtil.streamGet(HOLDING_TABLE, RecordID.class, wrapper, null,
          "ids", routingContext, okapiHeaders, vertxContext);
      } else {
        asyncResultHandler.handle(Future.succeededFuture());
        CQLWrapper wrapper = getCQL(query, INSTANCE_TABLE);
        PgUtil.streamGet(INSTANCE_TABLE, RecordID.class, wrapper, null,
          "ids", routingContext, okapiHeaders, vertxContext);
      }
    } catch (Exception e) {
      LOG.error(e.getMessage(), e);
      asyncResultHandler.handle(Future.succeededFuture(GetInstanceBulkIdsResponse
        .respond500WithTextPlain(e.getMessage())));
    }
  }
}
