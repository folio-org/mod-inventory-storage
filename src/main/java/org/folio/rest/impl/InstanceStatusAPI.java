/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.folio.rest.impl;

import static org.folio.rest.tools.utils.ValidationHelper.isDuplicate;
import static org.folio.rest.tools.utils.ValidationHelper.isInvalidUUID;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.Response;

import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.InstanceStatus;
import org.folio.rest.jaxrs.model.InstanceStatuses;
import org.folio.rest.jaxrs.resource.InstanceStatusesResource;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.tools.utils.TenantTool;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.FieldException;

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
public class InstanceStatusAPI implements InstanceStatusesResource {

  public static final String RESOURCE_TABLE = "instance_status";

  private static final String LOCATION_PREFIX = "/instance-statuses/";
  private static final Logger log = LoggerFactory.getLogger(InstanceStatusAPI.class);
  private final Messages messages = Messages.getInstance();
  private final String idFieldName = "_id";

  @Override
  public void deleteInstanceStatuses(String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    String tenantId = TenantTool.tenantId(okapiHeaders);

    try {
      vertxContext.runOnContext(v -> {
        PostgresClient postgresClient = PostgresClient.getInstance(
                vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

        postgresClient.mutate(String.format("DELETE FROM %s_%s.%s",
                tenantId, "mod_inventory_storage", RESOURCE_TABLE),
                reply -> {
                  if (reply.succeeded()) {
                    asyncResultHandler.handle(Future.succeededFuture(
                            DeleteInstanceStatusesResponse.noContent()
                                    .build()));
                  } else {
                    asyncResultHandler.handle(Future.succeededFuture(
                            DeleteInstanceStatusesResponse.
                                    withPlainInternalServerError(reply.cause().getMessage())));
                  }
                });
      });
    } catch (Exception e) {
      asyncResultHandler.handle(Future.succeededFuture(
              DeleteInstanceStatusesResponse.
                      withPlainInternalServerError(e.getMessage())));
    }
  }

  @Override
  public void getInstanceStatuses(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
        CQLWrapper cql = getCQL(query, limit, offset);
        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(RESOURCE_TABLE, InstanceStatus.class,
                new String[]{"*"}, cql, true, true,
                reply -> {
                  try {
                    if (reply.succeeded()) {
                      InstanceStatuses instanceStatuses = new InstanceStatuses();
                      @SuppressWarnings("unchecked")
                      List<InstanceStatus> levels = (List<InstanceStatus>) reply.result().getResults();
                      instanceStatuses.setInstanceStatuses(levels);
                      instanceStatuses.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
                      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetInstanceStatusesResponse.withJsonOK(
                              instanceStatuses)));
                    } else {
                      log.error(reply.cause().getMessage(), reply.cause());
                      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetInstanceStatusesResponse
                              .withPlainBadRequest(reply.cause().getMessage())));
                    }
                  } catch (Exception e) {
                    log.error(e.getMessage(), e);
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetInstanceStatusesResponse
                            .withPlainInternalServerError(messages.getMessage(
                                    lang, MessageConsts.InternalServerError))));
                  }
                });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        String message = messages.getMessage(lang, MessageConsts.InternalServerError);
        if (e.getCause() != null && e.getCause().getClass().getSimpleName().endsWith("CQLParseException")) {
          message = " CQL parse error " + e.getLocalizedMessage();
        }
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetInstanceStatusesResponse
                .withPlainInternalServerError(message)));
      }
    });
  }

  @Override
  public void postInstanceStatuses(String lang, InstanceStatus entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    vertxContext.runOnContext(v -> {
      try {
        String id = UUID.randomUUID().toString();
        if (entity.getId() == null) {
          entity.setId(id);
        } else {
          id = entity.getId();
        }

        String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
        PostgresClient.getInstance(vertxContext.owner(), tenantId).save(RESOURCE_TABLE, id, entity,
                reply -> {
                  try {
                    if (reply.succeeded()) {
                      Object ret = reply.result();
                      entity.setId((String) ret);
                      OutStream stream = new OutStream();
                      stream.setData(entity);
                      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostInstanceStatusesResponse.withJsonCreated(
                              LOCATION_PREFIX + ret, stream)));
                    } else {
                      log.error(reply.cause().getMessage(), reply.cause());
                      if (isDuplicate(reply.cause().getMessage())) {
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostInstanceStatusesResponse
                                .withJsonUnprocessableEntity(
                                        org.folio.rest.tools.utils.ValidationHelper.createValidationErrorMessage(
                                                "name", entity.getName(), "Material Type exists"))));
                      } else {
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostInstanceStatusesResponse
                                .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
                      }
                    }
                  } catch (Exception e) {
                    log.error(e.getMessage(), e);
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostInstanceStatusesResponse
                            .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
                  }
                });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostInstanceStatusesResponse
                .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  @Override
  public void getInstanceStatusesByInstanceStatusId(String instanceStatusId, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));

        Criterion c = new Criterion(
                new Criteria().addField(idFieldName).setJSONB(false).setOperation("=").setValue("'" + instanceStatusId + "'"));

        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(RESOURCE_TABLE, InstanceStatus.class, c, true,
                reply -> {
                  try {
                    if (reply.succeeded()) {
                      @SuppressWarnings("unchecked")
                      List<InstanceStatus> userGroup = (List<InstanceStatus>) reply.result().getResults();
                      if (userGroup.isEmpty()) {
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetInstanceStatusesByInstanceStatusIdResponse
                                .withPlainNotFound(instanceStatusId)));
                      } else {
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetInstanceStatusesByInstanceStatusIdResponse
                                .withJsonOK(userGroup.get(0))));
                      }
                    } else {
                      log.error(reply.cause().getMessage(), reply.cause());
                      if (isInvalidUUID(reply.cause().getMessage())) {
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetInstanceStatusesByInstanceStatusIdResponse
                                .withPlainNotFound(instanceStatusId)));
                      } else {
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetInstanceStatusesByInstanceStatusIdResponse
                                .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
                      }
                    }
                  } catch (Exception e) {
                    log.error(e.getMessage(), e);
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetInstanceStatusesByInstanceStatusIdResponse
                            .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
                  }
                });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetInstanceStatusesByInstanceStatusIdResponse
                .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  @Override
  public void deleteInstanceStatusesByInstanceStatusId(String instanceStatusId, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    vertxContext.runOnContext(v -> {
      String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
      try {
        Instance instance = new Instance();
        instance.setStatusId(instanceStatusId);
        /**
         * check if any item is using this material type *
         */
        try {
          PostgresClient.getInstance(vertxContext.owner(), tenantId).get(InstanceStorageAPI.INSTANCE_TABLE, instance, new String[]{idFieldName}, true, false, 0, 1, replyHandler -> {
            if (replyHandler.succeeded()) {
              List<Instance> instanceList = (List<Instance>) replyHandler.result().getResults();
              if (!instanceList.isEmpty()) {
                String message = "Can not delete cataloging level, " + instanceStatusId + ". "
                        + instanceList.size() + " instances associated with it";
                log.error(message);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteInstanceStatusesByInstanceStatusIdResponse
                        .withPlainBadRequest(message)));
                return;
              } else {
                log.info("Attemping delete of unused cataloging level, " + instanceStatusId);
              }
              try {
                PostgresClient.getInstance(vertxContext.owner(), tenantId).delete(RESOURCE_TABLE, instanceStatusId,
                        reply -> {
                          try {
                            if (reply.succeeded()) {
                              if (reply.result().getUpdated() == 1) {
                                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteInstanceStatusesByInstanceStatusIdResponse
                                        .withNoContent()));
                              } else {
                                log.error(messages.getMessage(lang, MessageConsts.DeletedCountError, 1, reply.result().getUpdated()));
                                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteInstanceStatusesByInstanceStatusIdResponse
                                        .withPlainNotFound(messages.getMessage(lang, MessageConsts.DeletedCountError, 1, reply.result().getUpdated()))));
                              }
                            } else {
                              log.error(reply.cause().getMessage(), reply.cause());
                              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteInstanceStatusesByInstanceStatusIdResponse
                                      .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
                            }
                          } catch (Exception e) {
                            log.error(e.getMessage(), e);
                            asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteInstanceStatusesByInstanceStatusIdResponse
                                    .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
                          }
                        });
              } catch (Exception e) {
                log.error(e.getMessage(), e);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteInstanceStatusesByInstanceStatusIdResponse
                        .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
              }
            } else {
              log.error(replyHandler.cause().getMessage(), replyHandler.cause());
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteInstanceStatusesByInstanceStatusIdResponse
                      .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
            }
          });
        } catch (Exception e) {
          log.error(e.getMessage(), e);
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteInstanceStatusesByInstanceStatusIdResponse
                  .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
        }
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteInstanceStatusesByInstanceStatusIdResponse
                .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  @Override
  public void putInstanceStatusesByInstanceStatusId(String instanceStatusId, String lang, InstanceStatus entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    vertxContext.runOnContext(v -> {
      String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
      try {
        if (entity.getId() == null) {
          entity.setId(instanceStatusId);
        }
        PostgresClient.getInstance(vertxContext.owner(), tenantId).update(RESOURCE_TABLE, entity, instanceStatusId,
                reply -> {
                  try {
                    if (reply.succeeded()) {
                      if (reply.result().getUpdated() == 0) {
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutInstanceStatusesByInstanceStatusIdResponse
                                .withPlainNotFound(messages.getMessage(lang, MessageConsts.NoRecordsUpdated))));
                      } else {
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutInstanceStatusesByInstanceStatusIdResponse
                                .withNoContent()));
                      }
                    } else {
                      log.error(reply.cause().getMessage());
                      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutInstanceStatusesByInstanceStatusIdResponse
                              .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
                    }
                  } catch (Exception e) {
                    log.error(e.getMessage(), e);
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutInstanceStatusesByInstanceStatusIdResponse
                            .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
                  }
                });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutInstanceStatusesByInstanceStatusIdResponse
                .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  private CQLWrapper getCQL(String query, int limit, int offset) throws FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(RESOURCE_TABLE + ".jsonb");
    return new CQLWrapper(cql2pgJson, query).setLimit(new Limit(limit)).setOffset(new Offset(offset));
  }

}
