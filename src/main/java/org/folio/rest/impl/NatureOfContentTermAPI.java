package org.folio.rest.impl;


import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.Response;

import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.rest.jaxrs.model.NatureOfContentTerm;
import org.folio.rest.jaxrs.model.NatureOfContentTerms;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
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

/**
 *
 * @author ne
 */
public class NatureOfContentTermAPI implements org.folio.rest.jaxrs.resource.NatureOfContentTerms {

  public static final String REFERENCE_TABLE  = "nature_of_content_term";

  private static final String LOCATION_PREFIX = "/nature-of-content-terms/";
  private static final Logger log             = LoggerFactory.getLogger(NatureOfContentTermAPI.class);
  private final Messages messages             = Messages.getInstance();


  @Override
  public void getNatureOfContentTerms(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    /**
     * http://host:port/nature-of-content-terms
     */
    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.tenantId(okapiHeaders);
        CQLWrapper cql = getCQL(query, limit, offset);
        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(REFERENCE_TABLE, NatureOfContentTerm.class,
            new String[]{"*"}, cql, true, true,
            reply -> {
              if (reply.succeeded()) {
                NatureOfContentTerms records = new NatureOfContentTerms();
                List<NatureOfContentTerm> record = reply.result().getResults();
                records.setNatureOfContentTerms(record);
                records.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetNatureOfContentTermsResponse.respond200WithApplicationJson(records)));
              }
              else{
                log.error(reply.cause().getMessage(), reply.cause());
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetNatureOfContentTermsResponse
                    .respond400WithTextPlain(reply.cause().getMessage())));
              }
            });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        String message = messages.getMessage(lang, MessageConsts.InternalServerError);
        if (e.getCause() instanceof CQLParseException) {
          message = " CQL parse error " + e.getLocalizedMessage();
        }
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetNatureOfContentTermsResponse
            .respond500WithTextPlain(message)));
      }
    });
  }

  @Override
  public void postNatureOfContentTerms(String lang, NatureOfContentTerm entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
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
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostNatureOfContentTermsResponse
                  .respond201WithApplicationJson(entity, PostNatureOfContentTermsResponse.headersFor201().withLocation(LOCATION_PREFIX + ret))));
              } else {
                String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                msg = (msg == null) ? "Internal server problem: Error message missing" : msg;
                log.info(msg);
                asyncResultHandler.handle(Future.succeededFuture(PostNatureOfContentTermsResponse
                    .respond400WithTextPlain(msg)));
              }
            });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        asyncResultHandler.handle(Future.succeededFuture(PostNatureOfContentTermsResponse.respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  @Override
  public void getNatureOfContentTermsById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(REFERENCE_TABLE, NatureOfContentTerm.class, id, okapiHeaders,
      vertxContext, GetNatureOfContentTermsByIdResponse.class, asyncResultHandler);
  }

  @Override
  public void deleteNatureOfContentTermsById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
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
                asyncResultHandler.handle(Future.succeededFuture(DeleteNatureOfContentTermsByIdResponse
                    .respond400WithTextPlain((msg))));
                return;
              }
              int updated = reply.result().rowCount();
              if (updated != 1) {
                String msg = messages.getMessage(lang, MessageConsts.DeletedCountError, 1, updated);
                log.error(msg);
                asyncResultHandler.handle(Future.succeededFuture(DeleteNatureOfContentTermsByIdResponse
                    .respond404WithTextPlain(msg)));
                return;
              }
              asyncResultHandler.handle(Future.succeededFuture(DeleteNatureOfContentTermsByIdResponse
                      .respond204()));
            });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        asyncResultHandler.handle(Future.succeededFuture(DeleteNatureOfContentTermsByIdResponse.respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  @Override
  public void putNatureOfContentTermsById(String id, String lang, NatureOfContentTerm entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
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
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutNatureOfContentTermsByIdResponse
                      .respond404WithTextPlain(messages.getMessage(lang, MessageConsts.NoRecordsUpdated))));
                } else{
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutNatureOfContentTermsByIdResponse
                      .respond204()));
                }
              } else {
                String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                msg =  (msg == null) ? "Internal server problem: Error message missing" : msg;
                log.info(msg);
                asyncResultHandler.handle(Future.succeededFuture(PutNatureOfContentTermsByIdResponse
                    .respond400WithTextPlain(msg)));
              }
            });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        asyncResultHandler.handle(Future.succeededFuture(PutNatureOfContentTermsByIdResponse.respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
      }

    });
  }

  private CQLWrapper getCQL(String query, int limit, int offset) throws FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(REFERENCE_TABLE+".jsonb");
    return new CQLWrapper(cql2pgJson, query).setLimit(new Limit(limit)).setOffset(new Offset(offset));
  }

}
