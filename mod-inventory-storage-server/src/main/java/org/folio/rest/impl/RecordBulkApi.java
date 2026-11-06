package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.persist.helper.RecordId;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.RecordBulkIdsGetField;
import org.folio.rest.jaxrs.model.RecordBulkIdsGetRecordType;
import org.folio.rest.jaxrs.resource.RecordBulk;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.support.StreamingJsonResponse;
import org.folio.services.referencedata.ReferenceDataService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings("java:S6813")
public class RecordBulkApi implements RecordBulk {

  public static final String INSTANCE_TABLE = "instance";
  public static final String HOLDING_TABLE = "holdings_record";
  public static final String HOLDING_TYPE = "HOLDING";

  private static final Logger LOG = LogManager.getLogger();

  @Autowired
  private ReferenceDataService referenceDataService;

  public RecordBulkApi() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Validate
  @Override
  public void getRecordBulkIds(RecordBulkIdsGetField field, RecordBulkIdsGetRecordType recordType,
                               int limit, String query, String totalRecords, int offset,
                               RoutingContext routingContext, Map<String, String> headers,
                               Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      if (StringUtils.isNotBlank(query)) {
        query = query.replace("items.effectiveLocationId", "item.effectiveLocationId");
      }
      var tableName = recordType.toString().equalsIgnoreCase(HOLDING_TYPE) ? HOLDING_TABLE : INSTANCE_TABLE;
      var wrapper = getCql(query, tableName, limit, offset);
      referenceDataService.stream(tableName, RecordId.class, wrapper, headers)
        .onSuccess(result -> StreamingJsonResponse.write(result, "ids", routingContext.response()))
        .onFailure(e -> StreamingJsonResponse.writeFailure(e, routingContext.response()));
    } catch (Exception e) {
      LOG.error(e.getMessage(), e);
      asyncResultHandler.handle(Future.succeededFuture(GetRecordBulkIdsResponse
        .respond500WithTextPlain(e.getMessage())));
    }
  }

  private CQLWrapper getCql(String query, String table, int limit, int offset) throws FieldException {
    return StorageHelper.getCql(query, limit, offset, table);
  }
}
