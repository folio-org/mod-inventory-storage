package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.Response;

import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.rest.jaxrs.model.HoldingsRecordsSource;
import org.folio.rest.jaxrs.model.HoldingsRecordsSources;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.TenantTool;
import org.z3950.zing.cql.CQLParseException;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class HoldingsRecordsSourceAPI implements org.folio.rest.jaxrs.resource.HoldingsRecordsSources {

  private static final String REFERENCE_TABLE = "holdings_records_source";
  private static final String LOCATION_PREFIX = "/holdings-records-sources/";
  private static final Logger log = LoggerFactory.getLogger(HoldingsRecordsSourceAPI.class);
  private final Messages messages = Messages.getInstance();

  @Override
  public void getHoldingsRecordsSources(String query, int offset, int limit, String lang,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
      vertxContext.runOnContext(v -> {
        try {
          String tenantId = TenantTool.tenantId(okapiHeaders);
          CQLWrapper cql = getCQL(query, limit, offset);
          PostgresClient.getInstance(vertxContext.owner(), tenantId).get(REFERENCE_TABLE,
            HoldingsRecordsSource.class,new String[]{"*"}, cql, true, true,
            reply -> {
              if (reply.succeeded()) {
                HoldingsRecordsSources sources = new HoldingsRecordsSources();
                List<HoldingsRecordsSource> source = reply.result().getResults();
                sources.setHoldingsRecordsSources(source);
                sources.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
                asyncResultHandler.handle(succeededFuture(GetHoldingsRecordsSourcesResponse
                  .respond200WithApplicationJson(sources)));
              } else {
                log.error(reply.cause().getMessage(), reply.cause());
                asyncResultHandler.handle(succeededFuture(GetHoldingsRecordsSourcesResponse
                  .respond400WithTextPlain(reply.cause().getMessage())));
              }
            });
        } catch (Exception e) {
          log.error(e.getMessage(), e);
          String message = messages.getMessage(lang, MessageConsts.InternalServerError);
          if (e.getCause() instanceof CQLParseException) {
            message = " CQL parse error " + e.getLocalizedMessage();
            asyncResultHandler.handle(succeededFuture(GetHoldingsRecordsSourcesResponse
              .respond500WithTextPlain(message)));
          }
        }
      });
  }

  @Override
  public void postHoldingsRecordsSources(String lang, HoldingsRecordsSource entity,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
      vertxContext.runOnContext(v -> {
        try {
          String id = entity.getId();
          if (id == null) {
            id = UUID.randomUUID().toString();
            entity.setId(id);
          }
          String tenantId = TenantTool.tenantId(okapiHeaders);
          PostgresClient.getInstance(vertxContext.owner(), tenantId).save(REFERENCE_TABLE, id, entity,
            reply -> {
              if (reply.succeeded()) {
                String ret = reply.result();
                entity.setId(ret);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostHoldingsRecordsSourcesResponse
                  .respond201WithApplicationJson(entity, PostHoldingsRecordsSourcesResponse
                  .headersFor201().withLocation(LOCATION_PREFIX + ret))));
              } else {
                String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                msg = (msg == null) ? "Internal server problem: Error message missing" : msg;
                log.info(msg);
                asyncResultHandler.handle(Future.succeededFuture(PostHoldingsRecordsSourcesResponse
                  .respond400WithTextPlain(msg)));
              }
            });
        } catch (Exception e) {
          log.error(e.getMessage(), e);
          asyncResultHandler.handle(Future.succeededFuture(PostHoldingsRecordsSourcesResponse
            .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
        }
      });
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
        try {
          String tenantId = TenantTool.tenantId(okapiHeaders);
          PostgresClient postgres = PostgresClient.getInstance(vertxContext.owner(), tenantId);
          postgres.delete(REFERENCE_TABLE, id,
            reply -> {
              if (reply.failed()) {
                String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                msg =  (msg == null) ? "Internal server problem: Error message missing" : msg;
                log.info(msg);
                asyncResultHandler.handle(succeededFuture(DeleteHoldingsRecordsSourcesByIdResponse
                  .respond400WithTextPlain((msg))));
                return;
              }
              int updated = reply.result().rowCount();
              if (updated != 1) {
                String msg = messages.getMessage(lang, MessageConsts.DeletedCountError, 1, updated);
                log.error(msg);
                asyncResultHandler.handle(succeededFuture(DeleteHoldingsRecordsSourcesByIdResponse
                  .respond404WithTextPlain(msg)));
                return;
              }
              asyncResultHandler.handle(succeededFuture(DeleteHoldingsRecordsSourcesByIdResponse
                .respond204()));
            });
        } catch (Exception e) {
          log.error(e.getMessage(), e);
          asyncResultHandler.handle(succeededFuture(DeleteHoldingsRecordsSourcesByIdResponse
            .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
        }
      });
  }

  @Override
  public void putHoldingsRecordsSourcesById(String id, String lang,
    HoldingsRecordsSource entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
      vertxContext.runOnContext(v -> {
        String tenantId = TenantTool.tenantId(okapiHeaders);
        try {
          if (entity.getId() == null) {
            entity.setId(id);
          }
          PostgresClient.getInstance(vertxContext.owner(), tenantId).update(REFERENCE_TABLE, entity, id,
            reply -> {
              if (reply.succeeded()) {
                if (reply.result().rowCount() == 0) {
                  asyncResultHandler.handle(succeededFuture(PutHoldingsRecordsSourcesByIdResponse
                      .respond404WithTextPlain(messages.getMessage(lang, MessageConsts.NoRecordsUpdated))));
                } else{
                  asyncResultHandler.handle(succeededFuture(PutHoldingsRecordsSourcesByIdResponse
                      .respond204()));
                }
              } else {
                String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                msg =  (msg == null) ? "Internal server problem: Error message missing" : msg;
                log.info(msg);
                asyncResultHandler.handle(succeededFuture(PutHoldingsRecordsSourcesByIdResponse
                    .respond400WithTextPlain(msg)));
              }
            });
        } catch (Exception e) {
          log.error(e.getMessage(), e);
          asyncResultHandler.handle(succeededFuture(PutHoldingsRecordsSourcesByIdResponse
            .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
        }
      });
  }

  private CQLWrapper getCQL(String query, int limit, int offset) throws FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(REFERENCE_TABLE+".jsonb");
    return new CQLWrapper(cql2pgJson, query).setLimit(new Limit(limit)).setOffset(new Offset(offset));
  }
}
