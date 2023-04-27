package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.RecordBulkIdsGetField;
import org.folio.rest.jaxrs.model.RecordBulkIdsGetRecordType;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.support.RecordId;

public class RecordBulkApi implements org.folio.rest.jaxrs.resource.RecordBulk {
  public static final String INSTANCE_TABLE = "instance";
  public static final String HOLDING_TABLE = "holdings_record";
  public static final String HOLDING_TYPE = "HOLDING";

  private static final Logger LOG = LogManager.getLogger();

  @Validate
  @Override
  public void getRecordBulkIds(RecordBulkIdsGetField field,
                               RecordBulkIdsGetRecordType recordType, int limit, String query,
                               int offset, String lang, RoutingContext routingContext,
                               Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
                               Context vertxContext) {
    try {
      if (StringUtils.isNotBlank(query)) {
        query = query.replace("items.effectiveLocationId", "item.effectiveLocationId");
      }
      var tableName = recordType.toString().equalsIgnoreCase(HOLDING_TYPE) ? HOLDING_TABLE : INSTANCE_TABLE;
      var wrapper = getCql(query, tableName, limit, offset);
      PgUtil.streamGet(tableName, RecordId.class, wrapper, null,
        "ids", routingContext, okapiHeaders, vertxContext);
    } catch (Exception e) {
      LOG.error(e.getMessage(), e);
      asyncResultHandler.handle(Future.succeededFuture(GetRecordBulkIdsResponse
        .respond500WithTextPlain(e.getMessage())));
    }
  }

  private CQLWrapper getCql(String query, String table, int limit, int offset) throws FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(table + ".jsonb");
    return new CQLWrapper(cql2pgJson, query)
      .setLimit(new Limit(limit))
      .setOffset(new Offset(offset));
  }
}
