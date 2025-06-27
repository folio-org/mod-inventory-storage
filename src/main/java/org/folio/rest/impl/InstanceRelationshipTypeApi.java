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
import org.folio.rest.jaxrs.model.InstanceRelationshipType;
import org.folio.rest.jaxrs.model.InstanceRelationshipTypes;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.support.PostgresClientFactory;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.z3950.zing.cql.CQLParseException;

public class InstanceRelationshipTypeApi implements org.folio.rest.jaxrs.resource.InstanceRelationshipTypes {

  public static final String INSTANCE_RELATIONSHIP_TYPE_TABLE = "instance_relationship_type";

  private static final String LOCATION_PREFIX = "/instance-relationship-types/";
  private static final Logger log = LogManager.getLogger();
  private final Messages messages = Messages.getInstance();

  @Validate
  @Override
  public void getInstanceRelationshipTypes(String query, String totalRecords, int offset, int limit,
                                           Map<String, String> okapiHeaders,
                                           Handler<AsyncResult<Response>> asyncResultHandler,
                                           Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        CQLWrapper cql = getCql(query, limit, offset);
        PostgresClientFactory.getInstance(vertxContext, okapiHeaders)
          .get(INSTANCE_RELATIONSHIP_TYPE_TABLE, InstanceRelationshipType.class,
            new String[] {"*"}, cql, true, true,
            reply -> {
              try {
                if (reply.succeeded()) {
                  InstanceRelationshipTypes instanceRelationshipTypes = new InstanceRelationshipTypes();
                  List<InstanceRelationshipType> instanceRelationshipType = reply.result().getResults();
                  instanceRelationshipTypes.setInstanceRelationshipTypes(instanceRelationshipType);
                  instanceRelationshipTypes.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                    GetInstanceRelationshipTypesResponse.respond200WithApplicationJson(
                      instanceRelationshipTypes)));
                } else {
                  log.error(reply.cause().getMessage(), reply.cause());
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetInstanceRelationshipTypesResponse
                    .respond400WithTextPlain(reply.cause().getMessage())));
                }
              } catch (Exception e) {
                log.error(e.getMessage(), e);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetInstanceRelationshipTypesResponse
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
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetInstanceRelationshipTypesResponse
          .respond500WithTextPlain(message)));
      }
    });
  }

  @Validate
  @Override
  public void postInstanceRelationshipTypes(InstanceRelationshipType entity,
                                            Map<String, String> okapiHeaders,
                                            Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    vertxContext.runOnContext(v -> {
      try {
        String id = entity.getId();
        if (id == null) {
          id = UUID.randomUUID().toString();
          entity.setId(id);
        }

        PostgresClientFactory.getInstance(vertxContext, okapiHeaders).save(INSTANCE_RELATIONSHIP_TYPE_TABLE, id, entity,
          reply -> {
            try {
              if (reply.succeeded()) {
                String ret = reply.result();
                entity.setId(ret);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostInstanceRelationshipTypesResponse
                  .respond201WithApplicationJson(entity,
                    PostInstanceRelationshipTypesResponse.headersFor201().withLocation(LOCATION_PREFIX + ret))));
              } else {
                String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                if (msg == null) {
                  internalServerErrorDuringPost(reply.cause(), asyncResultHandler);
                  return;
                }
                log.info(msg);
                asyncResultHandler.handle(Future.succeededFuture(PostInstanceRelationshipTypesResponse
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
  public void getInstanceRelationshipTypesByRelationshipTypeId(String relationshipTypeId,
                                                               Map<String, String> okapiHeaders,
                                                               Handler<AsyncResult<Response>> asyncResultHandler,
                                                               Context vertxContext) {
    PgUtil.getById(INSTANCE_RELATIONSHIP_TYPE_TABLE, InstanceRelationshipType.class, relationshipTypeId,
      okapiHeaders, vertxContext, GetInstanceRelationshipTypesByRelationshipTypeIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteInstanceRelationshipTypesByRelationshipTypeId(String relationshipTypeId,
                                                                  Map<String, String> okapiHeaders,
                                                                  Handler<AsyncResult<Response>> asyncResultHandler,
                                                                  Context vertxContext) {

    vertxContext.runOnContext(v -> {
      try {
        PostgresClientFactory.getInstance(vertxContext, okapiHeaders)
          .delete(INSTANCE_RELATIONSHIP_TYPE_TABLE, relationshipTypeId,
            reply -> {
              try {
                if (reply.failed()) {
                  String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                  if (msg == null) {
                    internalServerErrorDuringDelete(reply.cause(), asyncResultHandler);
                    return;
                  }
                  log.info(msg);
                  asyncResultHandler.handle(
                    Future.succeededFuture(DeleteInstanceRelationshipTypesByRelationshipTypeIdResponse
                      .respond400WithTextPlain(msg)));
                  return;
                }
                int updated = reply.result().rowCount();
                if (updated != 1) {
                  String msg = messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.DeletedCountError, 1, updated);
                  log.error(msg);
                  asyncResultHandler.handle(
                    Future.succeededFuture(DeleteInstanceRelationshipTypesByRelationshipTypeIdResponse
                      .respond404WithTextPlain(msg)));
                  return;
                }
                asyncResultHandler.handle(
                  Future.succeededFuture(DeleteInstanceRelationshipTypesByRelationshipTypeIdResponse
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
  public void putInstanceRelationshipTypesByRelationshipTypeId(String relationshipTypeId,
                                                               InstanceRelationshipType entity,
                                                               Map<String, String> okapiHeaders,
                                                               Handler<AsyncResult<Response>> asyncResultHandler,
                                                               Context vertxContext) {

    vertxContext.runOnContext(v -> {
      try {
        if (entity.getId() == null) {
          entity.setId(relationshipTypeId);
        }
        PostgresClientFactory.getInstance(vertxContext, okapiHeaders)
          .update(INSTANCE_RELATIONSHIP_TYPE_TABLE, entity, relationshipTypeId,
            reply -> {
              try {
                if (reply.succeeded()) {
                  if (reply.result().rowCount() == 0) {
                    asyncResultHandler.handle(
                      io.vertx.core.Future.succeededFuture(PutInstanceRelationshipTypesByRelationshipTypeIdResponse
                        .respond404WithTextPlain(
                          messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.NoRecordsUpdated))));
                  } else {
                    asyncResultHandler.handle(
                      io.vertx.core.Future.succeededFuture(PutInstanceRelationshipTypesByRelationshipTypeIdResponse
                        .respond204()));
                  }
                } else {
                  String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                  if (msg == null) {
                    internalServerErrorDuringPut(reply.cause(), asyncResultHandler);
                    return;
                  }
                  log.info(msg);
                  asyncResultHandler.handle(
                    Future.succeededFuture(PutInstanceRelationshipTypesByRelationshipTypeIdResponse
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
    return StorageHelper.getCql(query, limit, offset, INSTANCE_RELATIONSHIP_TYPE_TABLE);
  }

  private void internalServerErrorDuringPost(Throwable e, Handler<AsyncResult<Response>> handler) {
    log.error(e.getMessage(), e);
    handler.handle(Future.succeededFuture(PostInstanceRelationshipTypesResponse
      .respond500WithTextPlain(messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
  }

  private void internalServerErrorDuringDelete(Throwable e, Handler<AsyncResult<Response>> handler) {
    log.error(e.getMessage(), e);
    handler.handle(Future.succeededFuture(DeleteInstanceRelationshipTypesByRelationshipTypeIdResponse
      .respond500WithTextPlain(messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
  }

  private void internalServerErrorDuringPut(Throwable e, Handler<AsyncResult<Response>> handler) {
    log.error(e.getMessage(), e);
    handler.handle(Future.succeededFuture(PutInstanceRelationshipTypesByRelationshipTypeIdResponse
      .respond500WithTextPlain(messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
  }
}
