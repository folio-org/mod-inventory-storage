package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.tools.messages.Messages.DEFAULT_LANGUAGE;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.HoldingsRecordsSource;
import org.folio.rest.jaxrs.model.HoldingsRecordsSource.Source;
import org.folio.rest.jaxrs.model.HoldingsRecordsSources;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.TenantTool;

public class HoldingsRecordsSourceApi implements org.folio.rest.jaxrs.resource.HoldingsSources {

  public static final String HOLDINGS_RECORD_TABLE = "holdings_record";
  private static final String REFERENCE_TABLE = "holdings_records_source";
  private static final Logger log = LogManager.getLogger();
  private final Messages messages = Messages.getInstance();

  @Validate
  @Override
  public void getHoldingsSources(String query, String totalRecords, int offset, int limit,
                                 Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
                                 Context vertxContext) {
    PgUtil.get(REFERENCE_TABLE, HoldingsRecordsSource.class, HoldingsRecordsSources.class, query, offset, limit,
      okapiHeaders, vertxContext, GetHoldingsSourcesResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void postHoldingsSources(HoldingsRecordsSource entity,
                                  Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
                                  Context vertxContext) {
    PgUtil.post(REFERENCE_TABLE, entity, okapiHeaders, vertxContext, PostHoldingsSourcesResponse.class,
      asyncResultHandler);
  }

  @Validate
  @Override
  public void getHoldingsSourcesById(String id,
                                     Map<String, String> okapiHeaders,
                                     Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(REFERENCE_TABLE, HoldingsRecordsSource.class, id, okapiHeaders,
      vertxContext, GetHoldingsSourcesByIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteHoldingsSourcesById(String id,
                                        Map<String, String> okapiHeaders,
                                        Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
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
                PgUtil.deleteById(REFERENCE_TABLE, id, okapiHeaders, vertxContext,
                  DeleteHoldingsSourcesByIdResponse.class, asyncResultHandler);
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
          .respond500WithTextPlain(messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
      }
    });
  }

  @Validate
  @Override
  public void putHoldingsSourcesById(String id,
                                     HoldingsRecordsSource entity, Map<String, String> okapiHeaders,
                                     Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(REFERENCE_TABLE, entity, id, okapiHeaders, vertxContext, PutHoldingsSourcesByIdResponse.class,
      asyncResultHandler);
  }
}
