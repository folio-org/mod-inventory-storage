package org.folio.rest.impl;

import static org.folio.rest.tools.utils.ValidationHelper.isDuplicate;
import static org.folio.rest.tools.utils.ValidationHelper.isInvalidUUID;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.Response;

import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.StatisticalCodeType;
import org.folio.rest.jaxrs.model.StatisticalCodeTypes;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
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
public class StatisticalCodeTypeAPI implements org.folio.rest.jaxrs.resource.StatisticalCodeTypes {
  public static final String RESOURCE_TABLE = "statistical_code_type";

  private static final String LOCATION_PREFIX = "/statistical-code_types/";
  private static final Logger LOG = LoggerFactory.getLogger(StatisticalCodeTypeAPI.class);
  private static final Messages MESSAGES = Messages.getInstance();
  private static final String IDFIELDNAME = "_id";

  @Override
  public void deleteStatisticalCodeTypes(String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
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
                            DeleteStatisticalCodeTypesResponse.noContent()
                                    .build()));
                  } else {
                    asyncResultHandler.handle(Future.succeededFuture(
                            DeleteStatisticalCodeTypesResponse.
                                    respond500WithTextPlain(reply.cause().getMessage())));
                  }
                });
      });
    } catch (Exception e) {
      asyncResultHandler.handle(Future.succeededFuture(
              DeleteStatisticalCodeTypesResponse.
                      respond500WithTextPlain(e.getMessage())));
    }
  }

  @Override
  public void getStatisticalCodeTypes(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
        CQLWrapper cql = getCQL(query, limit, offset);
        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(RESOURCE_TABLE, StatisticalCodeType.class,
                new String[]{"*"}, cql, true, true,
                reply -> {
                  try {
                    if (reply.succeeded()) {
                      StatisticalCodeTypes statisticalCodeTypes = new StatisticalCodeTypes();
                      @SuppressWarnings("unchecked")
                      List<StatisticalCodeType> codes = reply.result().getResults();
                      statisticalCodeTypes.setStatisticalCodeTypes(codes);
                      statisticalCodeTypes.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
                      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetStatisticalCodeTypesResponse.respond200WithApplicationJson(
                              statisticalCodeTypes)));
                    } else {
                      LOG.error(reply.cause().getMessage(), reply.cause());
                      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetStatisticalCodeTypesResponse
                              .respond400WithTextPlain(reply.cause().getMessage())));
                    }
                  } catch (Exception e) {
                    LOG.error(e.getMessage(), e);
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetStatisticalCodeTypesResponse
                            .respond500WithTextPlain(MESSAGES.getMessage(
                                    lang, MessageConsts.InternalServerError))));
                  }
                });
      } catch (Exception e) {
        LOG.error(e.getMessage(), e);
        String message = MESSAGES.getMessage(lang, MessageConsts.InternalServerError);
        if (e.getCause() != null && e.getCause().getClass().getSimpleName().endsWith("CQLParseException")) {
          message = " CQL parse error " + e.getLocalizedMessage();
        }
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetStatisticalCodeTypesResponse
                .respond500WithTextPlain(message)));
      }
    });
  }

  @Override
  public void postStatisticalCodeTypes(String lang, StatisticalCodeType entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
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
                String ret = reply.result();
                entity.setId(ret);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                  PostStatisticalCodeTypesResponse.respond201WithApplicationJson(entity,
                    PostStatisticalCodeTypesResponse.headersFor201().withLocation(LOCATION_PREFIX + ret))));
              } else {
                LOG.error(reply.cause().getMessage(), reply.cause());
                if (isDuplicate(reply.cause().getMessage())) {
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostStatisticalCodeTypesResponse
                    .respond422WithApplicationJson(
                      org.folio.rest.tools.utils.ValidationHelper.createValidationErrorMessage(
                        "name", entity.getName(), "Material Type exists"))));
                } else {
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostStatisticalCodeTypesResponse
                    .respond500WithTextPlain(MESSAGES.getMessage(lang, MessageConsts.InternalServerError))));
                }
              }
            } catch (Exception e) {
              LOG.error(e.getMessage(), e);
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostStatisticalCodeTypesResponse
                .respond500WithTextPlain(MESSAGES.getMessage(lang, MessageConsts.InternalServerError))));
            }
          });
      } catch (Exception e) {
        LOG.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostStatisticalCodeTypesResponse
          .respond500WithTextPlain(MESSAGES.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  @Override
  public void getStatisticalCodeTypesByStatisticalCodeTypeId(String statisticalCodeTypeId, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));

        Criterion c = new Criterion(
                new Criteria().addField(IDFIELDNAME).setJSONB(false).setOperation("=").setValue("'" + statisticalCodeTypeId + "'"));

        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(RESOURCE_TABLE, StatisticalCodeType.class, c, true,
                reply -> {
                  try {
                    if (reply.succeeded()) {
                      @SuppressWarnings("unchecked")
                      List<StatisticalCodeType> userGroup = reply.result().getResults();
                      if (userGroup.isEmpty()) {
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetStatisticalCodeTypesByStatisticalCodeTypeIdResponse
                                .respond404WithTextPlain(statisticalCodeTypeId)));
                      } else {
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetStatisticalCodeTypesByStatisticalCodeTypeIdResponse
                                .respond200WithApplicationJson(userGroup.get(0))));
                      }
                    } else {
                      LOG.error(reply.cause().getMessage(), reply.cause());
                      if (isInvalidUUID(reply.cause().getMessage())) {
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetStatisticalCodeTypesByStatisticalCodeTypeIdResponse
                                .respond404WithTextPlain(statisticalCodeTypeId)));
                      } else {
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetStatisticalCodeTypesByStatisticalCodeTypeIdResponse
                                .respond500WithTextPlain(MESSAGES.getMessage(lang, MessageConsts.InternalServerError))));
                      }
                    }
                  } catch (Exception e) {
                    LOG.error(e.getMessage(), e);
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetStatisticalCodeTypesByStatisticalCodeTypeIdResponse
                            .respond500WithTextPlain(MESSAGES.getMessage(lang, MessageConsts.InternalServerError))));
                  }
                });
      } catch (Exception e) {
        LOG.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetStatisticalCodeTypesByStatisticalCodeTypeIdResponse
                .respond500WithTextPlain(MESSAGES.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  @Override
  public void deleteStatisticalCodeTypesByStatisticalCodeTypeId(String statisticalCodeTypeId, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
      try {
        PostgresClient.getInstance(vertxContext.owner(), tenantId).delete(RESOURCE_TABLE, statisticalCodeTypeId,
                reply -> {
                  try {
                    if (reply.succeeded()) {
                      if (reply.result().getUpdated() == 1) {
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteStatisticalCodeTypesByStatisticalCodeTypeIdResponse
                                .respond204()));
                      } else {
                        LOG.error(MESSAGES.getMessage(lang, MessageConsts.DeletedCountError, 1, reply.result().getUpdated()));
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteStatisticalCodeTypesByStatisticalCodeTypeIdResponse
                                .respond404WithTextPlain(MESSAGES.getMessage(lang, MessageConsts.DeletedCountError, 1, reply.result().getUpdated()))));
                      }
                    } else {
                      LOG.error(reply.cause().getMessage(), reply.cause());
                      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteStatisticalCodeTypesByStatisticalCodeTypeIdResponse
                              .respond500WithTextPlain(MESSAGES.getMessage(lang, MessageConsts.InternalServerError))));
                    }
                  } catch (Exception e) {
                    LOG.error(e.getMessage(), e);
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteStatisticalCodeTypesByStatisticalCodeTypeIdResponse
                            .respond500WithTextPlain(MESSAGES.getMessage(lang, MessageConsts.InternalServerError))));
                  }
                });
      } catch (Exception e) {
        LOG.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteStatisticalCodeTypesByStatisticalCodeTypeIdResponse
                .respond500WithTextPlain(MESSAGES.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  @Override
  public void putStatisticalCodeTypesByStatisticalCodeTypeId(String statisticalCodeTypeId, String lang, StatisticalCodeType entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
      try {
        if (entity.getId() == null) {
          entity.setId(statisticalCodeTypeId);
        }
        PostgresClient.getInstance(vertxContext.owner(), tenantId).update(RESOURCE_TABLE, entity, statisticalCodeTypeId,
                reply -> {
                  try {
                    if (reply.succeeded()) {
                      if (reply.result().getUpdated() == 0) {
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutStatisticalCodeTypesByStatisticalCodeTypeIdResponse
                                .respond404WithTextPlain(MESSAGES.getMessage(lang, MessageConsts.NoRecordsUpdated))));
                      } else {
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutStatisticalCodeTypesByStatisticalCodeTypeIdResponse
                                .respond204()));
                      }
                    } else {
                      LOG.error(reply.cause().getMessage());
                      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutStatisticalCodeTypesByStatisticalCodeTypeIdResponse
                              .respond500WithTextPlain(MESSAGES.getMessage(lang, MessageConsts.InternalServerError))));
                    }
                  } catch (Exception e) {
                    LOG.error(e.getMessage(), e);
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutStatisticalCodeTypesByStatisticalCodeTypeIdResponse
                            .respond500WithTextPlain(MESSAGES.getMessage(lang, MessageConsts.InternalServerError))));
                  }
                });
      } catch (Exception e) {
        LOG.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutStatisticalCodeTypesByStatisticalCodeTypeIdResponse
                .respond500WithTextPlain(MESSAGES.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  private CQLWrapper getCQL(String query, int limit, int offset) throws FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(RESOURCE_TABLE + ".jsonb");
    return new CQLWrapper(cql2pgJson, query).setLimit(new Limit(limit)).setOffset(new Offset(offset));
  }

}
