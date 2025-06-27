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
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.IdentifierType;
import org.folio.rest.jaxrs.model.IdentifierTypes;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.support.PostgresClientFactory;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.z3950.zing.cql.CQLParseException;

/**
 * Implements the instance identifier type persistency using postgres jsonb.
 */
public class IdentifierTypeApi implements org.folio.rest.jaxrs.resource.IdentifierTypes {

  public static final String IDENTIFIER_TYPE_TABLE = "identifier_type";

  private static final String LOCATION_PREFIX = "/identifier-types/";
  private static final Logger log = LogManager.getLogger();
  private final Messages messages = Messages.getInstance();

  @Validate
  @Override
  public void getIdentifierTypes(String query, String totalRecords, int offset, int limit,
                                 Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
                                 Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        CQLWrapper cql = getCql(query, limit, offset);
        PostgresClientFactory.getInstance(vertxContext, okapiHeaders).get(IDENTIFIER_TYPE_TABLE, IdentifierType.class,
          new String[] {"*"}, cql, true, true,
          reply -> {
            try {
              if (reply.succeeded()) {
                IdentifierTypes identifierTypes = new IdentifierTypes();
                List<IdentifierType> identifierType = reply.result().getResults();
                identifierTypes.setIdentifierTypes(identifierType);
                identifierTypes.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
                asyncResultHandler.handle(
                  io.vertx.core.Future.succeededFuture(GetIdentifierTypesResponse.respond200WithApplicationJson(
                    identifierTypes)));
              } else {
                log.error(reply.cause().getMessage(), reply.cause());
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetIdentifierTypesResponse
                  .respond400WithTextPlain(reply.cause().getMessage())));
              }
            } catch (Exception e) {
              log.error(e.getMessage(), e);
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetIdentifierTypesResponse
                .respond500WithTextPlain(messages.getMessage(
                  DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
            }
          });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        String message = messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError);
        if (e.getCause() instanceof CQLParseException) {
          message = " CQL parse error " + e.getLocalizedMessage();
        }
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetIdentifierTypesResponse
          .respond500WithTextPlain(message)));
      }
    });
  }

  @Validate
  @Override
  public void postIdentifierTypes(IdentifierType entity, Map<String, String> okapiHeaders,
                                  Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    vertxContext.runOnContext(v -> {
      try {
        String id = entity.getId();
        if (id == null) {
          id = UUID.randomUUID().toString();
          entity.setId(id);
        }

        PostgresClientFactory.getInstance(vertxContext, okapiHeaders).save(
          IDENTIFIER_TYPE_TABLE, id, entity,
          reply -> {
            try {
              if (reply.succeeded()) {
                String ret = reply.result();
                entity.setId(ret);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostIdentifierTypesResponse
                  .respond201WithApplicationJson(entity,
                    PostIdentifierTypesResponse.headersFor201().withLocation(LOCATION_PREFIX + ret))));
              } else {
                String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                if (msg == null) {
                  internalServerErrorDuringPost(reply.cause(), asyncResultHandler);
                  return;
                }
                log.info(msg);
                asyncResultHandler.handle(Future.succeededFuture(PostIdentifierTypesResponse
                  .respond400WithTextPlain(msg)));
              }
            } catch (Exception e) {
              internalServerErrorDuringPost(e, asyncResultHandler);
            }
          });
      } catch (Exception e) {
        internalServerErrorDuringPost(e, asyncResultHandler);
      }
    });
  }

  @Validate
  @Override
  public void getIdentifierTypesByIdentifierTypeId(String identifierTypeId,
                                                   Map<String, String> okapiHeaders,
                                                   Handler<AsyncResult<Response>> asyncResultHandler,
                                                   Context vertxContext) {
    PgUtil.getById(IDENTIFIER_TYPE_TABLE, IdentifierType.class, identifierTypeId,
      okapiHeaders, vertxContext, GetIdentifierTypesByIdentifierTypeIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteIdentifierTypesByIdentifierTypeId(String identifierTypeId,
                                                      Map<String, String> okapiHeaders,
                                                      Handler<AsyncResult<Response>> asyncResultHandler,
                                                      Context vertxContext) {

    vertxContext.runOnContext(v -> {
      try {
        PostgresClient postgres = PostgresClientFactory.getInstance(vertxContext, okapiHeaders);
        postgres.delete(IDENTIFIER_TYPE_TABLE, identifierTypeId,
          reply -> {
            try {
              if (reply.failed()) {
                String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                if (msg == null) {
                  internalServerErrorDuringDelete(reply.cause(), asyncResultHandler);
                  return;
                }
                log.info(msg);
                asyncResultHandler.handle(Future.succeededFuture(DeleteIdentifierTypesByIdentifierTypeIdResponse
                  .respond400WithTextPlain(msg)));
                return;
              }
              int updated = reply.result().rowCount();
              if (updated != 1) {
                String msg = messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.DeletedCountError, 1, updated);
                log.error(msg);
                asyncResultHandler.handle(Future.succeededFuture(DeleteIdentifierTypesByIdentifierTypeIdResponse
                  .respond404WithTextPlain(msg)));
                return;
              }
              asyncResultHandler.handle(Future.succeededFuture(DeleteIdentifierTypesByIdentifierTypeIdResponse
                .respond204()));
            } catch (Exception e) {
              internalServerErrorDuringDelete(e, asyncResultHandler);
            }
          });
      } catch (Exception e) {
        internalServerErrorDuringDelete(e, asyncResultHandler);
      }
    });
  }

  @Validate
  @Override
  public void putIdentifierTypesByIdentifierTypeId(String identifierTypeId, IdentifierType entity,
                                                   Map<String, String> okapiHeaders,
                                                   Handler<AsyncResult<Response>> asyncResultHandler,
                                                   Context vertxContext) {

    vertxContext.runOnContext(v -> {
      try {
        if (entity.getId() == null) {
          entity.setId(identifierTypeId);
        }
        PostgresClientFactory.getInstance(vertxContext, okapiHeaders).update(
          IDENTIFIER_TYPE_TABLE, entity, identifierTypeId,
          reply -> {
            try {
              if (reply.succeeded()) {
                if (reply.result().rowCount() == 0) {
                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(PutIdentifierTypesByIdentifierTypeIdResponse
                      .respond404WithTextPlain(messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.NoRecordsUpdated))));
                } else {
                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(PutIdentifierTypesByIdentifierTypeIdResponse
                      .respond204()));
                }
              } else {
                String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                if (msg == null) {
                  internalServerErrorDuringPut(reply.cause(), asyncResultHandler);
                  return;
                }
                log.info(msg);
                asyncResultHandler.handle(Future.succeededFuture(PutIdentifierTypesByIdentifierTypeIdResponse
                  .respond400WithTextPlain(msg)));
              }
            } catch (Exception e) {
              internalServerErrorDuringPut(e, asyncResultHandler);
            }
          });
      } catch (Exception e) {
        internalServerErrorDuringPut(e, asyncResultHandler);
      }
    });
  }

  private CQLWrapper getCql(String query, int limit, int offset) throws FieldException {
    return StorageHelper.getCql(query, limit, offset, IDENTIFIER_TYPE_TABLE);
  }

  private void internalServerErrorDuringPost(Throwable e, Handler<AsyncResult<Response>> handler) {
    log.error(e.getMessage(), e);
    handler.handle(Future.succeededFuture(PostIdentifierTypesResponse
      .respond500WithTextPlain(messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
  }

  private void internalServerErrorDuringDelete(Throwable e, Handler<AsyncResult<Response>> handler) {
    log.error(e.getMessage(), e);
    handler.handle(Future.succeededFuture(DeleteIdentifierTypesByIdentifierTypeIdResponse
      .respond500WithTextPlain(messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
  }

  private void internalServerErrorDuringPut(Throwable e, Handler<AsyncResult<Response>> handler) {
    log.error(e.getMessage(), e);
    handler.handle(Future.succeededFuture(PutIdentifierTypesByIdentifierTypeIdResponse
      .respond500WithTextPlain(messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
  }
}
