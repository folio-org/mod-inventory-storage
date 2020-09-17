package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.model.HoldingsRecordsSource;
import org.folio.rest.jaxrs.model.HoldingsRecordsSource.Source;
import org.folio.rest.jaxrs.model.HoldingsRecordsSources;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.TenantTool;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class HoldingsRecordsSourceAPI implements org.folio.rest.jaxrs.resource.HoldingsSources {

  private static final String REFERENCE_TABLE = "holdings_records_source";
  public static final String HOLDINGS_RECORD_TABLE = "holdings_record";
  private static final Logger log = LoggerFactory.getLogger(HoldingsRecordsSourceAPI.class);
  private final Messages messages = Messages.getInstance();

  @Override
  public void getHoldingsSources(String query, int offset, int limit, String lang,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
      PgUtil.get(REFERENCE_TABLE, HoldingsRecordsSource.class, HoldingsRecordsSources.class, query, offset, limit, okapiHeaders, vertxContext, GetHoldingsSourcesResponse.class, asyncResultHandler);
  }

  @Override
  public void postHoldingsSources(String lang, HoldingsRecordsSource entity,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
      PgUtil.post(REFERENCE_TABLE, entity, okapiHeaders, vertxContext, PostHoldingsSourcesResponse.class, asyncResultHandler);
  }

  @Override
  public void getHoldingsSourcesById(String id, String lang,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
      PgUtil.getById(REFERENCE_TABLE, HoldingsRecordsSource.class, id, okapiHeaders,
        vertxContext, GetHoldingsSourcesByIdResponse.class, asyncResultHandler);
  }

  @Override
  public void deleteHoldingsSourcesById(String id, String lang,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.tenantId(okapiHeaders);
        PostgresClient pgClient = PostgresClient.getInstance(vertxContext.owner(), tenantId);
        pgClient.getById(REFERENCE_TABLE,
            id, HoldingsRecordsSource.class,
            reply -> {
              if (reply.succeeded()) {
                Source source = reply.result().getSource();
                if (source == null || source.ordinal() != Source.FOLIO.ordinal()) {
                  PgUtil.deleteById(REFERENCE_TABLE, id, okapiHeaders, vertxContext, DeleteHoldingsSourcesByIdResponse.class, asyncResultHandler);
                } else {
                  log.error("Holdings Records Sources with source of folio can not be deleted");
                  asyncResultHandler.handle(succeededFuture(GetHoldingsSourcesResponse
                    .respond400WithTextPlain("Holdings Records Sources with source of folio can not be deleted")));
                }
              } else {
                log.error(reply.cause().getMessage(), reply.cause());
                asyncResultHandler.handle(succeededFuture(GetHoldingsSourcesResponse
                  .respond400WithTextPlain(reply.cause().getMessage())));
              }
            });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        asyncResultHandler.handle(succeededFuture(DeleteHoldingsSourcesByIdResponse
          .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  @Override
  public void putHoldingsSourcesById(String id, String lang,
    HoldingsRecordsSource entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(REFERENCE_TABLE, entity, id, okapiHeaders, vertxContext, PutHoldingsSourcesByIdResponse.class, asyncResultHandler);
  }
}
