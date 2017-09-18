package org.folio.rest.impl;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.IdentifierType;
import org.folio.rest.jaxrs.model.IdentifierTypes;
import org.folio.rest.jaxrs.resource.IdentifierTypesResource;
import org.folio.rest.persist.DatabaseExceptionUtils;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.tools.utils.TenantTool;
import org.z3950.zing.cql.CQLParseException;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.FieldException;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * Implements the instance identifier type persistency using postgres jsonb.
 */
public class IdentifierTypeAPI implements IdentifierTypesResource {

  public static final String IDENTIFIER_TYPE_TABLE   = "identifier_type";

  private static final String LOCATION_PREFIX       = "/identifier-types/";
  private static final Logger log                 = LoggerFactory.getLogger(IdentifierTypeAPI.class);
  private final Messages messages                 = Messages.getInstance();
  private String idFieldName                      = "_id";


  public IdentifierTypeAPI(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName);
  }

  private CQLWrapper getCQL(String query, int limit, int offset) throws FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(IDENTIFIER_TYPE_TABLE+".jsonb");
    return new CQLWrapper(cql2pgJson, query).setLimit(new Limit(limit)).setOffset(new Offset(offset));
  }

  @Validate
  @Override
  public void getIdentifierTypes(String query, int offset, int limit, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) throws Exception {
    /**
     * http://host:port/instances/identifier-types
     */
    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.tenantId(okapiHeaders);
        CQLWrapper cql = getCQL(query, limit, offset);
        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(IDENTIFIER_TYPE_TABLE, IdentifierType.class,
            new String[]{"*"}, cql, true, true,
            reply -> {
              try {
                if (reply.succeeded()) {
                  IdentifierTypes identifierTypes = new IdentifierTypes();
                  @SuppressWarnings("unchecked")
                  List<IdentifierType> identifierType = (List<IdentifierType>) reply.result()[0];
                  identifierTypes.setIdentifierTypes(identifierType);
                  identifierTypes.setTotalRecords((Integer)reply.result()[1]);
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetIdentifierTypesResponse.withJsonOK(
                      identifierTypes)));
                }
                else{
                  log.error(reply.cause().getMessage(), reply.cause());
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetIdentifierTypesResponse
                      .withPlainBadRequest(reply.cause().getMessage())));
                }
              } catch (Exception e) {
                log.error(e.getMessage(), e);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetIdentifierTypesResponse
                    .withPlainInternalServerError(messages.getMessage(
                        lang, MessageConsts.InternalServerError))));
              }
            });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        String message = messages.getMessage(lang, MessageConsts.InternalServerError);
        if (e.getCause() instanceof CQLParseException) {
          message = " CQL parse error " + e.getLocalizedMessage();
        }
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetIdentifierTypesResponse
            .withPlainInternalServerError(message)));
      }
    });
  }

  private void internalServerErrorDuringPost(Throwable e, String lang, Handler<AsyncResult<Response>> handler) {
    log.error(e.getMessage(), e);
    handler.handle(Future.succeededFuture(PostIdentifierTypesResponse
        .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
  }

  @Validate
  @Override
  public void postIdentifierTypes(String lang, IdentifierType entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

    vertxContext.runOnContext(v -> {
      try {
        String id = entity.getId();
        if (id == null) {
          id = UUID.randomUUID().toString();
          entity.setId(id);
        }

        String tenantId = TenantTool.tenantId(okapiHeaders);
        PostgresClient.getInstance(vertxContext.owner(), tenantId).save(
            IDENTIFIER_TYPE_TABLE, id, entity,
            reply -> {
              try {
                if (reply.succeeded()) {
                  Object ret = reply.result();
                  entity.setId((String) ret);
                  OutStream stream = new OutStream();
                  stream.setData(entity);
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostIdentifierTypesResponse.withJsonCreated(
                      LOCATION_PREFIX + ret, stream)));
                } else {
                  String msg = DatabaseExceptionUtils.badRequestMessage(reply.cause());
                  if (msg == null) {
                    internalServerErrorDuringPost(reply.cause(), lang, asyncResultHandler);
                    return;
                  }
                  log.info(msg);
                  asyncResultHandler.handle(Future.succeededFuture(PostIdentifierTypesResponse
                      .withPlainBadRequest(msg)));
                }
              } catch (Exception e) {
                internalServerErrorDuringPost(e, lang, asyncResultHandler);
              }
            });
      } catch (Exception e) {
        internalServerErrorDuringPost(e, lang, asyncResultHandler);
      }
    });
  }

  private void internalServerErrorDuringGetById(Throwable e, String lang, Handler<AsyncResult<Response>> handler) {
    log.error(e.getMessage(), e);
    handler.handle(Future.succeededFuture(GetIdentifierTypesByIdentifierTypeIdResponse
        .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
  }

  @Validate
  @Override
  public void getIdentifierTypesByIdentifierTypeId(String loantypeId, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) throws Exception {

    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.tenantId(okapiHeaders);

        Criterion c = new Criterion(
            new Criteria().addField(idFieldName).setJSONB(false).setOperation("=").setValue("'"+loantypeId+"'"));

        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(IDENTIFIER_TYPE_TABLE, IdentifierType.class, c, true,
            reply -> {
              try {
                if (reply.failed()) {
                  String msg = DatabaseExceptionUtils.badRequestMessage(reply.cause());
                  if (msg == null) {
                    internalServerErrorDuringGetById(reply.cause(), lang, asyncResultHandler);
                    return;
                  }
                  log.info(msg);
                  asyncResultHandler.handle(Future.succeededFuture(GetIdentifierTypesByIdentifierTypeIdResponse.
                      withPlainNotFound(msg)));
                  return;
                }
                @SuppressWarnings("unchecked")
                List<IdentifierType> identifierType = (List<IdentifierType>) reply.result()[0];
                if (identifierType.isEmpty()) {
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetIdentifierTypesByIdentifierTypeIdResponse
                      .withPlainNotFound(identifierTypeId)));
                }
                else{
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetIdentifierTypesByIdentifierTypeIdResponse
                      .withJsonOK(identifierType.get(0))));
                }
              } catch (Exception e) {
                internalServerErrorDuringGetById(e, lang, asyncResultHandler);
              }
            });
      } catch (Exception e) {
        internalServerErrorDuringGetById(e, lang, asyncResultHandler);
      }
    });
  }

  private void internalServerErrorDuringDelete(Throwable e, String lang, Handler<AsyncResult<Response>> handler) {
    log.error(e.getMessage(), e);
    handler.handle(Future.succeededFuture(DeleteIdentifierTypesByIdentifierTypeIdResponse
        .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
  }

  @Validate
  @Override
  public void deleteIdentifierTypesByIdentifierTypeId(String identifierTypeId, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) throws Exception {

    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.tenantId(okapiHeaders);
        PostgresClient postgres = PostgresClient.getInstance(vertxContext.owner(), tenantId);
        postgres.delete(IDENTIFIER_TYPE_TABLE, loantypeId,
            reply -> {
              try {
                if (reply.failed()) {
                  String msg = DatabaseExceptionUtils.badRequestMessage(reply.cause());
                  if (msg == null) {
                    internalServerErrorDuringDelete(reply.cause(), lang, asyncResultHandler);
                    return;
                  }
                  log.info(msg);
                  asyncResultHandler.handle(Future.succeededFuture(DeleteIdentifierTypesByIdentifierTypeIdResponse
                      .withPlainBadRequest(msg)));
                  return;
                }
                int updated = reply.result().getUpdated();
                if (updated != 1) {
                  String msg = messages.getMessage(lang, MessageConsts.DeletedCountError, 1, updated);
                  log.error(msg);
                  asyncResultHandler.handle(Future.succeededFuture(DeleteIdentifierTypesByIdentifierTypeIdResponse
                      .withPlainNotFound(msg)));
                  return;
                }
                asyncResultHandler.handle(Future.succeededFuture(DeleteIdentifierTypesByIdentifierTypeIdResponse
                        .withNoContent()));
              } catch (Exception e) {
                internalServerErrorDuringDelete(e, lang, asyncResultHandler);
              }
            });
      } catch (Exception e) {
        internalServerErrorDuringDelete(e, lang, asyncResultHandler);
      }
    });
  }

  private void internalServerErrorDuringPut(Throwable e, String lang, Handler<AsyncResult<Response>> handler) {
    log.error(e.getMessage(), e);
    handler.handle(Future.succeededFuture(PutIdentifierTypesByIdentifierTypeIdResponse
        .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
  }

  @Validate
  @Override
  public void putIdentifierTypesByIdentifierTypeId(String identifierTypeId, String lang, IdentifierType entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) throws Exception {

    vertxContext.runOnContext(v -> {
      String tenantId = TenantTool.tenantId(okapiHeaders);
      try {
        if (entity.getId() == null) {
          entity.setId(identifierTypeId);
        }
        PostgresClient.getInstance(vertxContext.owner(), tenantId).update(
            IDENTIFIER_TYPE_TABLE, entity, identifierTypeId,
            reply -> {
              try {
                if (reply.succeeded()) {
                  if (reply.result().getUpdated() == 0) {
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutIdentifierTypesByIdentifierTypeIdResponse
                        .withPlainNotFound(messages.getMessage(lang, MessageConsts.NoRecordsUpdated))));
                  } else{
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutIdentifierTypesByIdentifierTypeIdResponse
                        .withNoContent()));
                  }
                } else {
                  String msg = DatabaseExceptionUtils.badRequestMessage(reply.cause());
                  if (msg == null) {
                    internalServerErrorDuringPut(reply.cause(), lang, asyncResultHandler);
                    return;
                  }
                  log.info(msg);
                  asyncResultHandler.handle(Future.succeededFuture(PutIdentifierTypesByIdentifierTypeIdResponse
                      .withPlainBadRequest(msg)));
                }
              } catch (Exception e) {
                internalServerErrorDuringPut(e, lang, asyncResultHandler);
              }
            });
      } catch (Exception e) {
        internalServerErrorDuringPut(e, lang, asyncResultHandler);      }
    });
  }
}
