package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.HttpStatus;
import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.InstanceBulkIdsGetField;
import org.folio.rest.jaxrs.model.InstanceBulkIdsGetFormat;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.support.InstanceIDBase64;
import org.folio.rest.support.InstanceIDRaw;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;

public class InstanceBulkAPI implements org.folio.rest.jaxrs.resource.InstanceBulk {
  public static final String INSTANCE_TABLE = "instance";

  private static final Logger LOG = LoggerFactory.getLogger(InstanceBulkAPI.class);

  private CQLWrapper getCQL(String query) throws FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(INSTANCE_TABLE + ".jsonb");
    return new CQLWrapper(cql2pgJson, query);
  }

  @Validate
  @Override
  public void getInstanceBulkIds(InstanceBulkIdsGetField field,
      InstanceBulkIdsGetFormat format,
      String query, String lang, RoutingContext routingContext,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    HttpServerResponse response = routingContext.response();

    try {
      CQLWrapper wrapper = getCQL(query);
      PgUtil.streamGet(INSTANCE_TABLE, getIDClass(format), wrapper, null,
        "ids", routingContext, okapiHeaders, vertxContext);
    } catch (Exception e) {
      LOG.error(e.getMessage(), e);
      response.setStatusCode(HttpStatus.HTTP_INTERNAL_SERVER_ERROR.toInt());
      response.putHeader(HttpHeaders.CONTENT_TYPE, "text/plain");
      response.end(e.toString());
    }
  }

  private Class<?> getIDClass(InstanceBulkIdsGetFormat format) {
    if (format.compareTo(InstanceBulkIdsGetFormat.BASE64) == 0) {
      return InstanceIDBase64.class;
    }
    return InstanceIDRaw.class;
  }
}
