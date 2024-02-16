package org.folio.rest.impl;

import static org.folio.rest.tools.messages.Messages.DEFAULT_LANGUAGE;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.rest.annotations.Validate;
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

public class InstanceNoteTypeApi implements org.folio.rest.jaxrs.resource.InstanceNoteTypes {

  public static final String REFERENCE_TABLE = "instance_note_type";

  private static final Logger log = LogManager.getLogger();
  private static final String LOCATION_PREFIX = "/instance-note-types/";
  private static final String INTERNAL_SERVER_ERROR_MSG = "Internal server problem: Error message missing";
  private final Messages messages = Messages.getInstance();

  @Validate
  @Override
  public void getInstanceNoteTypes(String query, String totalRecords, int offset, int limit,
                                   Map<String, String> okapiHeaders,
                                   Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.tenantId(okapiHeaders);
        CQLWrapper cql = getCql(query, limit, offset);
        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(REFERENCE_TABLE, InstanceNoteType.class,
          new String[] {"*"}, cql, true, true,
          reply -> {
            if (reply.succeeded()) {
              InstanceNoteTypes records = new InstanceNoteTypes();
              List<InstanceNoteType> instanceNoteTypes = reply.result().getResults();
              records.setInstanceNoteTypes(instanceNoteTypes);
              records.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                GetInstanceNoteTypesResponse.respond200WithApplicationJson(records)));
            } else {
              log.error(reply.cause().getMessage(), reply.cause());
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetInstanceNoteTypesResponse
                .respond400WithTextPlain(reply.cause().getMessage())));
            }
          });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        String message = messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError);
        if (e.getCause() instanceof CQLParseException) {
          message = " CQL parse error " + e.getLocalizedMessage();
        }
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetInstanceNoteTypesResponse
          .respond500WithTextPlain(message)));
      }
    });
  }

  @Validate
  @Override
  public void postInstanceNoteTypes(InstanceNoteType entity, Map<String, String> okapiHeaders,
                                    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
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
                .respond201WithApplicationJson(entity,
                  PostInstanceNoteTypesResponse.headersFor201().withLocation(LOCATION_PREFIX + ret))));
            } else {
              String msg = PgExceptionUtil.badRequestMessage(reply.cause());
              msg = (msg == null) ? INTERNAL_SERVER_ERROR_MSG : msg;
              log.info(msg);
              asyncResultHandler.handle(Future.succeededFuture(PostInstanceNoteTypesResponse
                .respond400WithTextPlain(msg)));
            }
          });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        asyncResultHandler.handle(Future.succeededFuture(PostInstanceNoteTypesResponse.respond500WithTextPlain(
          messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
      }
    });
  }

  @Validate
  @Override
  public void getInstanceNoteTypesById(String id, Map<String, String> okapiHeaders,
                                       Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    PgUtil.getById(REFERENCE_TABLE, InstanceNoteType.class, id, okapiHeaders,
      vertxContext, GetInstanceNoteTypesByIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteInstanceNoteTypesById(String id, Map<String, String> okapiHeaders,
                                          Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.tenantId(okapiHeaders);
        PostgresClient postgres = PostgresClient.getInstance(vertxContext.owner(), tenantId);
        postgres.delete(REFERENCE_TABLE, id,
          reply -> {
            if (reply.failed()) {
              String msg = PgExceptionUtil.badRequestMessage(reply.cause());
              msg = (msg == null) ? INTERNAL_SERVER_ERROR_MSG : msg;
              log.info(msg);
              asyncResultHandler.handle(Future.succeededFuture(DeleteInstanceNoteTypesByIdResponse
                .respond400WithTextPlain(msg)));
              return;
            }
            int updated = reply.result().rowCount();
            if (updated != 1) {
              String msg = messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.DeletedCountError, 1, updated);
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
        asyncResultHandler.handle(Future.succeededFuture(DeleteInstanceNoteTypesByIdResponse.respond500WithTextPlain(
          messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
      }
    });
  }

  @Validate
  @Override
  public void putInstanceNoteTypesById(String id, InstanceNoteType entity,
                                       Map<String, String> okapiHeaders,
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
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutInstanceNoteTypesByIdResponse
                  .respond404WithTextPlain(messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.NoRecordsUpdated))));
              } else {
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutInstanceNoteTypesByIdResponse
                  .respond204()));
              }
            } else {
              String msg = PgExceptionUtil.badRequestMessage(reply.cause());
              msg = (msg == null) ? INTERNAL_SERVER_ERROR_MSG : msg;
              log.info(msg);
              asyncResultHandler.handle(Future.succeededFuture(PutInstanceNoteTypesByIdResponse
                .respond400WithTextPlain(msg)));
            }
          });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        asyncResultHandler.handle(Future.succeededFuture(PutInstanceNoteTypesByIdResponse.respond500WithTextPlain(
          messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
      }

    });
  }

  private CQLWrapper getCql(String query, int limit, int offset) throws FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(REFERENCE_TABLE + ".jsonb");
    return new CQLWrapper(cql2pgJson, query).setLimit(new Limit(limit)).setOffset(new Offset(offset));
  }
}
