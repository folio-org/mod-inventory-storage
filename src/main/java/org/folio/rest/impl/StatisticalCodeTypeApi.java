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
import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.rest.RestVerticle;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.StatisticalCodeType;
import org.folio.rest.jaxrs.model.StatisticalCodeTypes;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.TenantTool;

public class StatisticalCodeTypeApi implements org.folio.rest.jaxrs.resource.StatisticalCodeTypes {
  public static final String RESOURCE_TABLE = "statistical_code_type";

  private static final String LOCATION_PREFIX = "/statistical-code_types/";
  private static final Logger LOG = LogManager.getLogger();
  private static final Messages MESSAGES = Messages.getInstance();

  @Validate
  @Override
  public void getStatisticalCodeTypes(String query, String totalRecords, int offset, int limit,
                                      Map<String, String> okapiHeaders,
                                      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
        CQLWrapper cql = getCql(query, limit, offset);
        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(RESOURCE_TABLE, StatisticalCodeType.class,
          new String[] {"*"}, cql, true, true,
          reply -> {
            try {
              if (reply.succeeded()) {
                StatisticalCodeTypes statisticalCodeTypes = new StatisticalCodeTypes();
                List<StatisticalCodeType> codes = reply.result().getResults();
                statisticalCodeTypes.setStatisticalCodeTypes(codes);
                statisticalCodeTypes.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
                asyncResultHandler.handle(
                  io.vertx.core.Future.succeededFuture(GetStatisticalCodeTypesResponse.respond200WithApplicationJson(
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
                  DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
            }
          });
      } catch (Exception e) {
        LOG.error(e.getMessage(), e);
        String message = MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError);
        if (e.getCause() != null && e.getCause().getClass().getSimpleName().endsWith("CQLParseException")) {
          message = " CQL parse error " + e.getLocalizedMessage();
        }
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetStatisticalCodeTypesResponse
          .respond500WithTextPlain(message)));
      }
    });
  }

  @Validate
  @Override
  public void postStatisticalCodeTypes(StatisticalCodeType entity, Map<String, String> okapiHeaders,
                                       Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
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
                        "name", entity.getName(), "Statistical Code Type exists"))));
                } else {
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostStatisticalCodeTypesResponse
                    .respond400WithTextPlain(
                      MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
                }
              }
            } catch (Exception e) {
              LOG.error(e.getMessage(), e);
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostStatisticalCodeTypesResponse
                .respond500WithTextPlain(MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
            }
          });
      } catch (Exception e) {
        LOG.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostStatisticalCodeTypesResponse
          .respond500WithTextPlain(MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
      }
    });
  }

  @Validate
  @Override
  public void deleteStatisticalCodeTypes(Map<String, String> okapiHeaders,
                                         Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    String tenantId = TenantTool.tenantId(okapiHeaders);
    try {
      vertxContext.runOnContext(v -> {
        PostgresClient postgresClient = PostgresClient.getInstance(
          vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

        postgresClient.execute(String.format("DELETE FROM %s_%s.%s",
            tenantId, "mod_inventory_storage", RESOURCE_TABLE),
          reply -> {
            if (reply.succeeded()) {
              asyncResultHandler.handle(Future.succeededFuture(
                DeleteStatisticalCodeTypesResponse.respond204()));
            } else {
              asyncResultHandler.handle(Future.succeededFuture(
                DeleteStatisticalCodeTypesResponse.respond500WithTextPlain(reply.cause().getMessage())));
            }
          });
      });
    } catch (Exception e) {
      asyncResultHandler.handle(Future.succeededFuture(
        DeleteStatisticalCodeTypesResponse.respond500WithTextPlain(e.getMessage())));
    }
  }

  @Validate
  @Override
  public void getStatisticalCodeTypesByStatisticalCodeTypeId(String statisticalCodeTypeId,
                                                             Map<String, String> okapiHeaders,
                                                             Handler<AsyncResult<Response>> asyncResultHandler,
                                                             Context vertxContext) {
    PgUtil.getById(RESOURCE_TABLE, StatisticalCodeType.class, statisticalCodeTypeId, okapiHeaders, vertxContext,
      GetStatisticalCodeTypesByStatisticalCodeTypeIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteStatisticalCodeTypesByStatisticalCodeTypeId(String statisticalCodeTypeId,
                                                                Map<String, String> okapiHeaders,
                                                                Handler<AsyncResult<Response>> asyncResultHandler,
                                                                Context vertxContext) {
    vertxContext.runOnContext(v -> {
      String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
      try {
        PostgresClient.getInstance(vertxContext.owner(), tenantId).delete(RESOURCE_TABLE, statisticalCodeTypeId,
          reply -> {
            try {
              if (reply.succeeded()) {
                if (reply.result().rowCount() == 1) {
                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(DeleteStatisticalCodeTypesByStatisticalCodeTypeIdResponse
                      .respond204()));
                } else {
                  LOG.error(MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.DeletedCountError,
                    1, reply.result().rowCount()));
                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(DeleteStatisticalCodeTypesByStatisticalCodeTypeIdResponse
                      .respond404WithTextPlain(
                        MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.DeletedCountError,
                          1, reply.result().rowCount()))));
                }
              } else {
                LOG.error(reply.cause().getMessage(), reply.cause());
                asyncResultHandler.handle(
                  io.vertx.core.Future.succeededFuture(DeleteStatisticalCodeTypesByStatisticalCodeTypeIdResponse
                    .respond400WithTextPlain(
                      MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
              }
            } catch (Exception e) {
              LOG.error(e.getMessage(), e);
              asyncResultHandler.handle(
                io.vertx.core.Future.succeededFuture(DeleteStatisticalCodeTypesByStatisticalCodeTypeIdResponse
                  .respond500WithTextPlain(MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
            }
          });
      } catch (Exception e) {
        LOG.error(e.getMessage(), e);
        asyncResultHandler.handle(
          io.vertx.core.Future.succeededFuture(DeleteStatisticalCodeTypesByStatisticalCodeTypeIdResponse
            .respond500WithTextPlain(MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
      }
    });
  }

  @Validate
  @Override
  public void putStatisticalCodeTypesByStatisticalCodeTypeId(String statisticalCodeTypeId,
                                                             StatisticalCodeType entity,
                                                             Map<String, String> okapiHeaders,
                                                             Handler<AsyncResult<Response>> asyncResultHandler,
                                                             Context vertxContext) {
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
                if (reply.result().rowCount() == 0) {
                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(PutStatisticalCodeTypesByStatisticalCodeTypeIdResponse
                      .respond404WithTextPlain(MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.NoRecordsUpdated))));
                } else {
                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(PutStatisticalCodeTypesByStatisticalCodeTypeIdResponse
                      .respond204()));
                }
              } else {
                LOG.error(reply.cause().getMessage());
                asyncResultHandler.handle(
                  io.vertx.core.Future.succeededFuture(PutStatisticalCodeTypesByStatisticalCodeTypeIdResponse
                    .respond400WithTextPlain(
                      MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
              }
            } catch (Exception e) {
              LOG.error(e.getMessage(), e);
              asyncResultHandler.handle(
                io.vertx.core.Future.succeededFuture(PutStatisticalCodeTypesByStatisticalCodeTypeIdResponse
                  .respond500WithTextPlain(MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
            }
          });
      } catch (Exception e) {
        LOG.error(e.getMessage(), e);
        asyncResultHandler.handle(
          io.vertx.core.Future.succeededFuture(PutStatisticalCodeTypesByStatisticalCodeTypeIdResponse
            .respond500WithTextPlain(MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
      }
    });
  }

  private CQLWrapper getCql(String query, int limit, int offset) throws FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(RESOURCE_TABLE + ".jsonb");
    return new CQLWrapper(cql2pgJson, query).setLimit(new Limit(limit)).setOffset(new Offset(offset));
  }

}
