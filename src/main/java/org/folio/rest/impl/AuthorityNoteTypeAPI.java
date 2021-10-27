package org.folio.rest.impl;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.ws.rs.core.Response;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.z3950.zing.cql.CQLParseException;

import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.rest.jaxrs.model.AuthorityNoteType;
import org.folio.rest.jaxrs.model.AuthorityNoteTypes;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.TenantTool;

public class AuthorityNoteTypeAPI implements org.folio.rest.jaxrs.resource.AuthorityNoteTypes {

  public static final String REFERENCE_TABLE  = "authority_note_type";

  private static final String LOCATION_PREFIX = "/authority-note-types/";
  private static final Logger log             = LogManager.getLogger();
  private final Messages messages             = Messages.getInstance();

  private static final String INTERNAL_SERVER_ERROR_MESSAGE = "Internal server problem: Error message missing";

  @Override
  public void getAuthorityNoteTypes(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    /**
     * http://host:port/authority-note-types
     */
    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.tenantId(okapiHeaders);
        CQLWrapper cql = getCQL(query, limit, offset);
        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(REFERENCE_TABLE, AuthorityNoteType.class,
          new String[]{"*"}, cql, true, true,
          reply -> {
            if (reply.succeeded()) {
              AuthorityNoteTypes records = new AuthorityNoteTypes();
              List<AuthorityNoteType> record = reply.result().getResults();
              records.setAuthorityNoteTypes(record);
              records.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(org.folio.rest.jaxrs.resource.AuthorityNoteTypes.GetAuthorityNoteTypesResponse.respond200WithApplicationJson(records)));
            }
            else{
              log.error(reply.cause().getMessage(), reply.cause());
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(org.folio.rest.jaxrs.resource.AuthorityNoteTypes.GetAuthorityNoteTypesResponse
                .respond400WithTextPlain(reply.cause().getMessage())));
            }
          });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        String message = messages.getMessage(lang, MessageConsts.InternalServerError);
        if (e.getCause() instanceof CQLParseException) {
          message = " CQL parse error " + e.getLocalizedMessage();
        }
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(org.folio.rest.jaxrs.resource.AuthorityNoteTypes.GetAuthorityNoteTypesResponse
          .respond500WithTextPlain(message)));
      }
    });
  }

  @Override
  public void postAuthorityNoteTypes(String lang, AuthorityNoteType entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        String id = entity.getId();
        if (Objects.isNull(id)) {
          entity.setId(UUID.randomUUID().toString());
        }

        String tenantId = TenantTool.tenantId(okapiHeaders);
        PostgresClient.getInstance(vertxContext.owner(), tenantId).save(REFERENCE_TABLE, id, entity,
          reply -> {
            if (reply.succeeded()) {
              String ret = reply.result();
              entity.setId(ret);
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostAuthorityNoteTypesResponse
                .respond201WithApplicationJson(entity, PostAuthorityNoteTypesResponse.headersFor201().withLocation(LOCATION_PREFIX + ret))));
            } else {
              String msg = PgExceptionUtil.badRequestMessage(reply.cause());
              msg = (msg == null) ? INTERNAL_SERVER_ERROR_MESSAGE : msg;
              log.info(msg);
              asyncResultHandler.handle(Future.succeededFuture(PostAuthorityNoteTypesResponse
                .respond400WithTextPlain(msg)));
            }
          });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        asyncResultHandler.handle(Future.succeededFuture(PostAuthorityNoteTypesResponse.respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  @Override
  public void getAuthorityNoteTypesById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(REFERENCE_TABLE, AuthorityNoteType.class, id,
      okapiHeaders, vertxContext, GetAuthorityNoteTypesByIdResponse.class, asyncResultHandler);
  }

  @Override
  public void deleteAuthorityNoteTypesById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.tenantId(okapiHeaders);
        PostgresClient postgres = PostgresClient.getInstance(vertxContext.owner(), tenantId);
        postgres.delete(REFERENCE_TABLE, id,
          reply -> {
            if (reply.failed()) {
              String msg = PgExceptionUtil.badRequestMessage(reply.cause());
              msg =  (msg == null) ? INTERNAL_SERVER_ERROR_MESSAGE : msg;
              log.info(msg);
              asyncResultHandler.handle(Future.succeededFuture(DeleteAuthorityNoteTypesByIdResponse
                .respond400WithTextPlain((msg))));
              return;
            }
            int updated = reply.result().rowCount();
            if (updated != 1) {
              String msg = messages.getMessage(lang, MessageConsts.DeletedCountError, 1, updated);
              log.error(msg);
              asyncResultHandler.handle(Future.succeededFuture(DeleteAuthorityNoteTypesByIdResponse
                .respond404WithTextPlain(msg)));
              return;
            }
            asyncResultHandler.handle(Future.succeededFuture(DeleteAuthorityNoteTypesByIdResponse
              .respond204()));
          });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        asyncResultHandler.handle(Future.succeededFuture(DeleteAuthorityNoteTypesByIdResponse.respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  @Override
  public void putAuthorityNoteTypesById(String id, String lang, AuthorityNoteType entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
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
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutAuthorityNoteTypesByIdResponse
                  .respond404WithTextPlain(messages.getMessage(lang, MessageConsts.NoRecordsUpdated))));
              } else{
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutAuthorityNoteTypesByIdResponse
                  .respond204()));
              }
            } else {
              String msg = PgExceptionUtil.badRequestMessage(reply.cause());
              msg =  (msg == null) ? INTERNAL_SERVER_ERROR_MESSAGE : msg;
              log.info(msg);
              asyncResultHandler.handle(Future.succeededFuture(PutAuthorityNoteTypesByIdResponse
                .respond400WithTextPlain(msg)));
            }
          });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        asyncResultHandler.handle(Future.succeededFuture(PutAuthorityNoteTypesByIdResponse.respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  private CQLWrapper getCQL(String query, int limit, int offset) throws FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(REFERENCE_TABLE+".jsonb");
    return new CQLWrapper(cql2pgJson, query).setLimit(new Limit(limit)).setOffset(new Offset(offset));
  }
}
