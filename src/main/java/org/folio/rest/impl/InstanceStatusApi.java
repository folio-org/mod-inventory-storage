package org.folio.rest.impl;

import static org.folio.rest.tools.messages.Messages.DEFAULT_LANGUAGE;
import static org.folio.rest.tools.utils.ValidationHelper.isDuplicate;

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
import org.folio.rest.jaxrs.model.InstanceStatus;
import org.folio.rest.jaxrs.model.InstanceStatuses;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.support.PostgresClientFactory;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;

public class InstanceStatusApi implements org.folio.rest.jaxrs.resource.InstanceStatuses {

  public static final String RESOURCE_TABLE = "instance_status";

  private static final String LOCATION_PREFIX = "/instance-statuses/";
  private static final Logger LOG = LogManager.getLogger();
  private static final Messages MESSAGES = Messages.getInstance();

  @Validate
  @Override
  public void getInstanceStatuses(String query, String totalRecords, int offset, int limit,
                                  Map<String, String> okapiHeaders,
                                  Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        CQLWrapper cql = getCql(query, limit, offset);
        PostgresClientFactory.getInstance(vertxContext, okapiHeaders).get(RESOURCE_TABLE, InstanceStatus.class,
          new String[] {"*"}, cql, true, true,
          reply -> {
            try {
              if (reply.succeeded()) {
                InstanceStatuses instanceStatuses = new InstanceStatuses();
                List<InstanceStatus> levels = reply.result().getResults();
                instanceStatuses.setInstanceStatuses(levels);
                instanceStatuses.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
                asyncResultHandler.handle(
                  io.vertx.core.Future.succeededFuture(GetInstanceStatusesResponse.respond200WithApplicationJson(
                    instanceStatuses)));
              } else {
                LOG.error(reply.cause().getMessage(), reply.cause());
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetInstanceStatusesResponse
                  .respond400WithTextPlain(reply.cause().getMessage())));
              }
            } catch (Exception e) {
              LOG.error(e.getMessage(), e);
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetInstanceStatusesResponse
                .respond500WithTextPlain(MESSAGES.getMessage(
                  DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
            }
          });
      } catch (Exception e) {
        LOG.error(e.getMessage(), e);
        String message = MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError);
        if (e.getCause() != null && e.getCause().getClass().getSimpleName().endsWith("CQLParseException")) {
          message = " CQL parse error " + e.getLocalizedMessage();
        }
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetInstanceStatusesResponse
          .respond500WithTextPlain(message)));
      }
    });
  }

  @Validate
  @Override
  public void postInstanceStatuses(InstanceStatus entity, Map<String, String> okapiHeaders,
                                   Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        String id = UUID.randomUUID().toString();
        if (entity.getId() == null) {
          entity.setId(id);
        } else {
          id = entity.getId();
        }

        PostgresClientFactory.getInstance(vertxContext, okapiHeaders).save(RESOURCE_TABLE, id, entity,
          reply -> {
            try {
              if (reply.succeeded()) {
                String ret = reply.result();
                entity.setId(ret);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostInstanceStatusesResponse
                  .respond201WithApplicationJson(entity,
                    PostInstanceStatusesResponse.headersFor201().withLocation(LOCATION_PREFIX + ret))));
              } else {
                LOG.error(reply.cause().getMessage(), reply.cause());
                if (isDuplicate(reply.cause().getMessage())) {
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostInstanceStatusesResponse
                    .respond422WithApplicationJson(
                      org.folio.rest.tools.utils.ValidationHelper.createValidationErrorMessage(
                        "name", entity.getName(), "Instance status exists"))));
                } else {
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostInstanceStatusesResponse
                    .respond400WithTextPlain(
                      MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
                }
              }
            } catch (Exception e) {
              LOG.error(e.getMessage(), e);
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostInstanceStatusesResponse
                .respond500WithTextPlain(MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
            }
          });
      } catch (Exception e) {
        LOG.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostInstanceStatusesResponse
          .respond500WithTextPlain(MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
      }
    });
  }

  @Validate
  @Override
  public void deleteInstanceStatuses(Map<String, String> okapiHeaders,
                                     Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      vertxContext.runOnContext(v -> PostgresClientFactory.getInstance(vertxContext, okapiHeaders)
        .delete(RESOURCE_TABLE, new Criterion(),
          reply -> {
            if (reply.succeeded()) {
              asyncResultHandler.handle(Future.succeededFuture(
                DeleteInstanceStatusesResponse.respond204()));
            } else {
              asyncResultHandler.handle(Future.succeededFuture(
                DeleteInstanceStatusesResponse.respond500WithTextPlain(reply.cause().getMessage())));
            }
          }));
    } catch (Exception e) {
      asyncResultHandler.handle(Future.succeededFuture(
        DeleteInstanceStatusesResponse.respond500WithTextPlain(e.getMessage())));
    }
  }

  @Validate
  @Override
  public void getInstanceStatusesByInstanceStatusId(String instanceStatusId,
                                                    Map<String, String> okapiHeaders,
                                                    Handler<AsyncResult<Response>> asyncResultHandler,
                                                    Context vertxContext) {
    PgUtil.getById(RESOURCE_TABLE, InstanceStatus.class, instanceStatusId,
      okapiHeaders, vertxContext, GetInstanceStatusesByInstanceStatusIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteInstanceStatusesByInstanceStatusId(String instanceStatusId,
                                                       Map<String, String> okapiHeaders,
                                                       Handler<AsyncResult<Response>> asyncResultHandler,
                                                       Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        PostgresClientFactory.getInstance(vertxContext, okapiHeaders).delete(RESOURCE_TABLE, instanceStatusId,
          reply -> {
            try {
              if (reply.succeeded()) {
                if (reply.result().rowCount() == 1) {
                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(DeleteInstanceStatusesByInstanceStatusIdResponse
                      .respond204()));
                } else {
                  LOG.error(MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.DeletedCountError,
                    1, reply.result().rowCount()));
                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(DeleteInstanceStatusesByInstanceStatusIdResponse
                      .respond404WithTextPlain(
                        MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.DeletedCountError,
                          1, reply.result().rowCount()))));
                }
              } else {
                LOG.error(reply.cause().getMessage(), reply.cause());
                asyncResultHandler.handle(
                  io.vertx.core.Future.succeededFuture(DeleteInstanceStatusesByInstanceStatusIdResponse
                    .respond400WithTextPlain(MESSAGES.getMessage(DEFAULT_LANGUAGE,
                      MessageConsts.InternalServerError))));
              }
            } catch (Exception e) {
              LOG.error(e.getMessage(), e);
              asyncResultHandler.handle(
                io.vertx.core.Future.succeededFuture(DeleteInstanceStatusesByInstanceStatusIdResponse
                  .respond500WithTextPlain(MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
            }
          });
      } catch (Exception e) {
        LOG.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteInstanceStatusesByInstanceStatusIdResponse
          .respond500WithTextPlain(MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
      }
    });
  }

  @Validate
  @Override
  public void putInstanceStatusesByInstanceStatusId(String instanceStatusId, InstanceStatus entity,
                                                    Map<String, String> okapiHeaders,
                                                    Handler<AsyncResult<Response>> asyncResultHandler,
                                                    Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        if (entity.getId() == null) {
          entity.setId(instanceStatusId);
        }
        PostgresClientFactory.getInstance(vertxContext, okapiHeaders).update(RESOURCE_TABLE, entity, instanceStatusId,
          reply -> {
            try {
              if (reply.succeeded()) {
                if (reply.result().rowCount() == 0) {
                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(PutInstanceStatusesByInstanceStatusIdResponse
                      .respond404WithTextPlain(MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.NoRecordsUpdated))));
                } else {
                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(PutInstanceStatusesByInstanceStatusIdResponse
                      .respond204()));
                }
              } else {
                LOG.error(reply.cause().getMessage());
                asyncResultHandler.handle(
                  io.vertx.core.Future.succeededFuture(PutInstanceStatusesByInstanceStatusIdResponse
                    .respond400WithTextPlain(MESSAGES.getMessage(DEFAULT_LANGUAGE,
                      MessageConsts.InternalServerError))));
              }
            } catch (Exception e) {
              LOG.error(e.getMessage(), e);
              asyncResultHandler.handle(
                io.vertx.core.Future.succeededFuture(PutInstanceStatusesByInstanceStatusIdResponse
                  .respond500WithTextPlain(MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
            }
          });
      } catch (Exception e) {
        LOG.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutInstanceStatusesByInstanceStatusIdResponse
          .respond500WithTextPlain(MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
      }
    });
  }

  private CQLWrapper getCql(String query, int limit, int offset) throws FieldException {
    return StorageHelper.getCql(query, limit, offset, RESOURCE_TABLE);
  }
}
