package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.rest.jaxrs.model.HoldingsRecordsSource;
import org.folio.rest.jaxrs.model.HoldingsRecordsSources;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.z3950.zing.cql.CQLParseException;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class HoldingsRecordsSourceAPI implements org.folio.rest.jaxrs.resource.HoldingsRecordsSources {

  private static final String REFERENCE_TABLE = "holdings_records_source";
  private static final Logger log = LoggerFactory.getLogger(HoldingsRecordsSourceAPI.class);
  private final Messages messages = Messages.getInstance();

  @Override
  public void getHoldingsRecordsSources(String query, int offset, int limit, String lang,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    CQLWrapper cqlWrapper;
    try {
      cqlWrapper = getCQL(query, limit, offset);
      PgUtil.get(REFERENCE_TABLE, HoldingsRecordsSource.class, HoldingsRecordsSources.class, cqlWrapper.getQuery(), offset, limit, okapiHeaders, vertxContext, GetHoldingsRecordsSourcesResponse.class, asyncResultHandler);
    } catch (FieldException e) {
      log.error(e.getMessage(), e);
      String message = messages.getMessage(lang, MessageConsts.InternalServerError);
      if (e.getCause() instanceof CQLParseException) {
        message = " CQL parse error " + e.getLocalizedMessage();
        asyncResultHandler.handle(succeededFuture(GetHoldingsRecordsSourcesResponse
          .respond500WithTextPlain(message)));
      }
    }
  }

  @Override
  public void postHoldingsRecordsSources(String lang, HoldingsRecordsSource entity,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
      PgUtil.post(REFERENCE_TABLE, entity, okapiHeaders, vertxContext, PostHoldingsRecordsSourcesResponse.class, asyncResultHandler);
  }

  @Override
  public void getHoldingsRecordsSourcesById(String id, String lang,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
      PgUtil.getById(REFERENCE_TABLE, HoldingsRecordsSource.class, id, okapiHeaders,
        vertxContext, GetHoldingsRecordsSourcesByIdResponse.class, asyncResultHandler);
  }

  @Override
  public void deleteHoldingsRecordsSourcesById(String id, String lang,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      PgUtil.deleteById(REFERENCE_TABLE, id, okapiHeaders, vertxContext, DeleteHoldingsRecordsSourcesByIdResponse.class, asyncResultHandler);
    });
  }

  @Override
  public void putHoldingsRecordsSourcesById(String id, String lang,
    HoldingsRecordsSource entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(REFERENCE_TABLE, entity, id, okapiHeaders, vertxContext, PutHoldingsRecordsSourcesByIdResponse.class, asyncResultHandler);
  }

  private CQLWrapper getCQL(String query, int limit, int offset) throws FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(REFERENCE_TABLE+".jsonb");
    return new CQLWrapper(cql2pgJson, query).setLimit(new Limit(limit)).setOffset(new Offset(offset));
  }
}
