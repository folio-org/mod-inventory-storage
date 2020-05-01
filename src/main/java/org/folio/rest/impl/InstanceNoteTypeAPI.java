package org.folio.rest.impl;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.Response;

import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.rest.jaxrs.model.InstanceNoteType;
import org.folio.rest.jaxrs.model.InstanceNoteTypes;
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
public class InstanceNoteTypeAPI implements org.folio.rest.jaxrs.resource.InstanceNoteTypes {

  public static final String REFERENCE_TABLE  = "instance_note_type";

  private static final String LOCATION_PREFIX = "/instance-note-types/";
  private static final Logger log             = LoggerFactory.getLogger(InstanceNoteTypeAPI.class);
  private final Messages messages             = Messages.getInstance();

  @Override
  public void getInstanceNoteTypes(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    /**
     * http://host:port/instance-note-types
     */
    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.tenantId(okapiHeaders);
        CQLWrapper cql = getCQL(query, limit, offset);
        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(REFERENCE_TABLE, InstanceNoteType.class,
            new String[]{"*"}, cql, true, true,
            reply -> {
              if (reply.succeeded()) {
                InstanceNoteTypes records = new InstanceNoteTypes();
                List<InstanceNoteType> record = reply.result().getResults();
                records.setInstanceNoteTypes(record);
                records.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetInstanceNoteTypesResponse.respond200WithApplicationJson(records)));
              }
              else{
                log.error(reply.cause().getMessage(), reply.cause());
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetInstanceNoteTypesResponse
                    .respond400WithTextPlain(reply.cause().getMessage())));
              }
            });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        String message = messages.getMessage(lang, MessageConsts.InternalServerError);
        if (e.getCause() instanceof CQLParseException) {
          message = " CQL parse error " + e.getLocalizedMessage();
        }
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetInstanceNoteTypesResponse
            .respond500WithTextPlain(message)));
      }
    });
  }

  @Override
  public void postInstanceNoteTypes(String lang, InstanceNoteType entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
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
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostInstanceNoteTypesResponse
                  .respond201WithApplicationJson(entity, PostInstanceNoteTypesResponse.headersFor201().withLocation(LOCATION_PREFIX + ret))));
              } else {
                String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                msg = (msg == null) ? "Internal server problem: Error message missing" : msg;
                log.info(msg);
                asyncResultHandler.handle(Future.succeededFuture(PostInstanceNoteTypesResponse
                    .respond400WithTextPlain(msg)));
              }
            });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        asyncResultHandler.handle(Future.succeededFuture(PostInstanceNoteTypesResponse.respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  @Override
  public void getInstanceNoteTypesById(String id, String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    PgUtil.getById(REFERENCE_TABLE, InstanceNoteType.class, id, okapiHeaders,
      vertxContext, GetInstanceNoteTypesByIdResponse.class, asyncResultHandler);
  }

  @Override
  public void deleteInstanceNoteTypesById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
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
                asyncResultHandler.handle(Future.succeededFuture(DeleteInstanceNoteTypesByIdResponse
                    .respond400WithTextPlain((msg))));
                return;
              }
              int updated = reply.result().rowCount();
              if (updated != 1) {
                String msg = messages.getMessage(lang, MessageConsts.DeletedCountError, 1, updated);
                log.error(msg);
                asyncResultHandler.handle(Future.succeededFuture(DeleteInstanceNoteTypesByIdResponse
                    .respond404WithTextPlain(msg)));
                return;
              }
              asyncResultHandler.handle(Future.succeededFuture(DeleteInstanceNoteTypesByIdResponse
                      .respond204()));
            });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        asyncResultHandler.handle(Future.succeededFuture(DeleteInstanceNoteTypesByIdResponse.respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  @Override
  public void putInstanceNoteTypesById(String id, String lang, InstanceNoteType entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
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
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutInstanceNoteTypesByIdResponse
                      .respond404WithTextPlain(messages.getMessage(lang, MessageConsts.NoRecordsUpdated))));
                } else{
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutInstanceNoteTypesByIdResponse
                      .respond204()));
                }
              } else {
                String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                msg =  (msg == null) ? "Internal server problem: Error message missing" : msg;
                log.info(msg);
                asyncResultHandler.handle(Future.succeededFuture(PutInstanceNoteTypesByIdResponse
                    .respond400WithTextPlain(msg)));
              }
            });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        asyncResultHandler.handle(Future.succeededFuture(PutInstanceNoteTypesByIdResponse.respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
      }

    });
  }

  private CQLWrapper getCQL(String query, int limit, int offset) throws FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(REFERENCE_TABLE+".jsonb");
    return new CQLWrapper(cql2pgJson, query).setLimit(new Limit(limit)).setOffset(new Offset(offset));
  }
}
