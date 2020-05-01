package org.folio.rest.impl;

import static org.folio.rest.tools.utils.ValidationHelper.isDuplicate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.Response;

import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.IssuanceMode;
import org.folio.rest.jaxrs.model.IssuanceModes;
import org.folio.rest.jaxrs.resource.ModesOfIssuance;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.TenantTool;
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
public class ModeOfIssuanceAPI implements ModesOfIssuance {
  public static final String RESOURCE_TABLE = "mode_of_issuance";

  private static final String LOCATION_PREFIX = "/modes-of-issuance/";
  private static final Logger LOG = LoggerFactory.getLogger(ModeOfIssuanceAPI.class);
  private static final Messages MESSAGES = Messages.getInstance();

  @Override
  public void deleteModesOfIssuance(String lang, Map<String, String> okapiHeaders,
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
                            DeleteModesOfIssuanceResponse.noContent()
                                    .build()));
                  } else {
                    asyncResultHandler.handle(Future.succeededFuture(
                            DeleteModesOfIssuanceResponse.
                                    respond500WithTextPlain(reply.cause().getMessage())));
                  }
                });
      });
    } catch (Exception e) {
      asyncResultHandler.handle(Future.succeededFuture(
              DeleteModesOfIssuanceResponse.
                      respond500WithTextPlain(e.getMessage())));
    }
  }

  @Override
  public void getModesOfIssuance(String query, int offset, int limit, String lang,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
        CQLWrapper cql = getCQL(query, limit, offset);
        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(RESOURCE_TABLE, IssuanceMode.class,
          new String[]{"*"}, cql, true, true,
          reply -> {
            try {
              if (reply.succeeded()) {
                IssuanceModes issuanceModes = new IssuanceModes();
                List<IssuanceMode> modes = reply.result().getResults();
                issuanceModes.setIssuanceModes(modes);
                issuanceModes.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetModesOfIssuanceResponse.respond200WithApplicationJson(
                  issuanceModes)));
              } else {
                LOG.error(reply.cause().getMessage(), reply.cause());
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetModesOfIssuanceResponse
                  .respond400WithTextPlain(reply.cause().getMessage())));
              }
            } catch (Exception e) {
              LOG.error(e.getMessage(), e);
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetModesOfIssuanceResponse
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
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetModesOfIssuanceResponse
          .respond500WithTextPlain(message)));
      }
    });
  }

  @Override
  public void postModesOfIssuance(String lang, IssuanceMode entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

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
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostModesOfIssuanceResponse
                  .respond201WithApplicationJson(entity, PostModesOfIssuanceResponse.headersFor201().withLocation(LOCATION_PREFIX + ret))));
              } else {
                LOG.error(reply.cause().getMessage(), reply.cause());
                if (isDuplicate(reply.cause().getMessage())) {
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostModesOfIssuanceResponse
                    .respond422WithApplicationJson(
                      org.folio.rest.tools.utils.ValidationHelper.createValidationErrorMessage(
                        "name", entity.getName(), "Mode of issuance exists"))));
                } else {
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostModesOfIssuanceResponse
                    .respond400WithTextPlain(MESSAGES.getMessage(lang, MessageConsts.InternalServerError))));
                }
              }
            } catch (Exception e) {
              LOG.error(e.getMessage(), e);
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostModesOfIssuanceResponse
                .respond500WithTextPlain(MESSAGES.getMessage(lang, MessageConsts.InternalServerError))));
            }
          });
      } catch (Exception e) {
        LOG.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostModesOfIssuanceResponse
          .respond500WithTextPlain(MESSAGES.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  @Override
  public void getModesOfIssuanceByModeOfIssuanceId(String modeOfIssuanceId, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(RESOURCE_TABLE, IssuanceMode.class, modeOfIssuanceId, okapiHeaders, vertxContext,
        GetModesOfIssuanceByModeOfIssuanceIdResponse.class, asyncResultHandler);
  }

  @Override
  public void deleteModesOfIssuanceByModeOfIssuanceId(String modeOfIssuanceId, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
      try {
        PostgresClient.getInstance(vertxContext.owner(), tenantId).delete(RESOURCE_TABLE, modeOfIssuanceId,
                reply -> {
                  try {
                    if (reply.succeeded()) {
                      if (reply.result().rowCount() == 1) {
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteModesOfIssuanceByModeOfIssuanceIdResponse
                                .respond204()));
                      } else {
                        LOG.error(MESSAGES.getMessage(lang, MessageConsts.DeletedCountError, 1, reply.result().rowCount()));
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteModesOfIssuanceByModeOfIssuanceIdResponse
                                .respond404WithTextPlain(MESSAGES.getMessage(lang, MessageConsts.DeletedCountError, 1, reply.result().rowCount()))));
                      }
                    } else {
                      LOG.error(reply.cause().getMessage(), reply.cause());
                      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteModesOfIssuanceByModeOfIssuanceIdResponse
                              .respond400WithTextPlain(MESSAGES.getMessage(lang, MessageConsts.InternalServerError))));
                    }
                  } catch (Exception e) {
                    LOG.error(e.getMessage(), e);
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteModesOfIssuanceByModeOfIssuanceIdResponse
                            .respond500WithTextPlain(MESSAGES.getMessage(lang, MessageConsts.InternalServerError))));
                  }
                });
      } catch (Exception e) {
        LOG.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteModesOfIssuanceByModeOfIssuanceIdResponse
                .respond500WithTextPlain(MESSAGES.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  @Override
  public void putModesOfIssuanceByModeOfIssuanceId(String modeOfIssuanceId, String lang, IssuanceMode entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
      try {
        if (entity.getId() == null) {
          entity.setId(modeOfIssuanceId);
        }
        PostgresClient.getInstance(vertxContext.owner(), tenantId).update(RESOURCE_TABLE, entity, modeOfIssuanceId,
                reply -> {
                  try {
                    if (reply.succeeded()) {
                      if (reply.result().rowCount() == 0) {
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutModesOfIssuanceByModeOfIssuanceIdResponse
                                .respond404WithTextPlain(MESSAGES.getMessage(lang, MessageConsts.NoRecordsUpdated))));
                      } else {
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutModesOfIssuanceByModeOfIssuanceIdResponse
                                .respond204()));
                      }
                    } else {
                      LOG.error(reply.cause().getMessage());
                      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutModesOfIssuanceByModeOfIssuanceIdResponse
                              .respond400WithTextPlain(MESSAGES.getMessage(lang, MessageConsts.InternalServerError))));
                    }
                  } catch (Exception e) {
                    LOG.error(e.getMessage(), e);
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutModesOfIssuanceByModeOfIssuanceIdResponse
                            .respond500WithTextPlain(MESSAGES.getMessage(lang, MessageConsts.InternalServerError))));
                  }
                });
      } catch (Exception e) {
        LOG.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutModesOfIssuanceByModeOfIssuanceIdResponse
                .respond500WithTextPlain(MESSAGES.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  private CQLWrapper getCQL(String query, int limit, int offset) throws FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(RESOURCE_TABLE + ".jsonb");
    return new CQLWrapper(cql2pgJson, query).setLimit(new Limit(limit)).setOffset(new Offset(offset));
  }

}
