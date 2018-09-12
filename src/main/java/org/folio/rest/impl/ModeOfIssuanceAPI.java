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
import org.folio.rest.jaxrs.model.IssuanceMode;
import org.folio.rest.jaxrs.model.IssuanceModes;
import org.folio.rest.jaxrs.resource.ModesOfIssuanceResource;
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
public class ModeOfIssuanceAPI implements ModesOfIssuanceResource {
  public static final String RESOURCE_TABLE = "mode_of_issuance";

  private static final String LOCATION_PREFIX = "/modes-of-issuance/";
  private static final Logger LOG = LoggerFactory.getLogger(ModeOfIssuanceAPI.class);
  private static final Messages MESSAGES = Messages.getInstance();
  private static final String IDFIELDNAME = "_id";


  @Override
  public void deleteModesOfIssuance(String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
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
                            DeleteModesOfIssuanceResponse.noContent()
                                    .build()));
                  } else {
                    asyncResultHandler.handle(Future.succeededFuture(
                            DeleteModesOfIssuanceResponse.
                                    withPlainInternalServerError(reply.cause().getMessage())));
                  }
                });
      });
    } catch (Exception e) {
      asyncResultHandler.handle(Future.succeededFuture(
              DeleteModesOfIssuanceResponse.
                      withPlainInternalServerError(e.getMessage())));
    }
  }

  @Override
  public void getModesOfIssuance(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
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
                      @SuppressWarnings("unchecked")
                      List<IssuanceMode> modes = (List<IssuanceMode>) reply.result().getResults();
                      issuanceModes.setIssuanceModes(modes);
                      issuanceModes.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
                      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetModesOfIssuanceResponse.withJsonOK(
                              issuanceModes)));
                    } else {
                      LOG.error(reply.cause().getMessage(), reply.cause());
                      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetModesOfIssuanceResponse
                              .withPlainBadRequest(reply.cause().getMessage())));
                    }
                  } catch (Exception e) {
                    LOG.error(e.getMessage(), e);
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetModesOfIssuanceResponse
                            .withPlainInternalServerError(MESSAGES.getMessage(
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
                .withPlainInternalServerError(message)));
      }
    });
  }

  @Override
  public void postModesOfIssuance(String lang, IssuanceMode entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
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
                      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostModesOfIssuanceResponse.withJsonCreated(
                              LOCATION_PREFIX + ret, stream)));
                    } else {
                      LOG.error(reply.cause().getMessage(), reply.cause());
                      if (isDuplicate(reply.cause().getMessage())) {
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostModesOfIssuanceResponse
                                .withJsonUnprocessableEntity(
                                        org.folio.rest.tools.utils.ValidationHelper.createValidationErrorMessage(
                                                "name", entity.getName(), "Mode of issuance exists"))));
                      } else {
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostModesOfIssuanceResponse
                                .withPlainInternalServerError(MESSAGES.getMessage(lang, MessageConsts.InternalServerError))));
                      }
                    }
                  } catch (Exception e) {
                    LOG.error(e.getMessage(), e);
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostModesOfIssuanceResponse
                            .withPlainInternalServerError(MESSAGES.getMessage(lang, MessageConsts.InternalServerError))));
                  }
                });
      } catch (Exception e) {
        LOG.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostModesOfIssuanceResponse
                .withPlainInternalServerError(MESSAGES.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  @Override
  public void getModesOfIssuanceByModeOfIssuanceId(String modeOfIssuanceId, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));

        Criterion c = new Criterion(
                new Criteria().addField(IDFIELDNAME).setJSONB(false).setOperation("=").setValue("'" + modeOfIssuanceId + "'"));

        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(RESOURCE_TABLE, IssuanceModes.class, c, true,
                reply -> {
                  try {
                    if (reply.succeeded()) {
                      @SuppressWarnings("unchecked")
                      List<IssuanceMode> userGroup = (List<IssuanceMode>) reply.result().getResults();
                      if (userGroup.isEmpty()) {
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetModesOfIssuanceByModeOfIssuanceIdResponse
                                .withPlainNotFound(modeOfIssuanceId)));
                      } else {
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetModesOfIssuanceByModeOfIssuanceIdResponse
                                .withJsonOK(userGroup.get(0))));
                      }
                    } else {
                      LOG.error(reply.cause().getMessage(), reply.cause());
                      if (isInvalidUUID(reply.cause().getMessage())) {
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetModesOfIssuanceByModeOfIssuanceIdResponse
                                .withPlainNotFound(modeOfIssuanceId)));
                      } else {
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetModesOfIssuanceByModeOfIssuanceIdResponse
                                .withPlainInternalServerError(MESSAGES.getMessage(lang, MessageConsts.InternalServerError))));
                      }
                    }
                  } catch (Exception e) {
                    LOG.error(e.getMessage(), e);
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetModesOfIssuanceByModeOfIssuanceIdResponse
                            .withPlainInternalServerError(MESSAGES.getMessage(lang, MessageConsts.InternalServerError))));
                  }
                });
      } catch (Exception e) {
        LOG.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetModesOfIssuanceByModeOfIssuanceIdResponse
                .withPlainInternalServerError(MESSAGES.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  @Override
  public void deleteModesOfIssuanceByModeOfIssuanceId(String modeOfIssuanceId, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    vertxContext.runOnContext(v -> {
      String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
      try {
        PostgresClient.getInstance(vertxContext.owner(), tenantId).delete(RESOURCE_TABLE, modeOfIssuanceId,
                reply -> {
                  try {
                    if (reply.succeeded()) {
                      if (reply.result().getUpdated() == 1) {
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteModesOfIssuanceByModeOfIssuanceIdResponse
                                .withNoContent()));
                      } else {
                        LOG.error(MESSAGES.getMessage(lang, MessageConsts.DeletedCountError, 1, reply.result().getUpdated()));
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteModesOfIssuanceByModeOfIssuanceIdResponse
                                .withPlainNotFound(MESSAGES.getMessage(lang, MessageConsts.DeletedCountError, 1, reply.result().getUpdated()))));
                      }
                    } else {
                      LOG.error(reply.cause().getMessage(), reply.cause());
                      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteModesOfIssuanceByModeOfIssuanceIdResponse
                              .withPlainInternalServerError(MESSAGES.getMessage(lang, MessageConsts.InternalServerError))));
                    }
                  } catch (Exception e) {
                    LOG.error(e.getMessage(), e);
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteModesOfIssuanceByModeOfIssuanceIdResponse
                            .withPlainInternalServerError(MESSAGES.getMessage(lang, MessageConsts.InternalServerError))));
                  }
                });
      } catch (Exception e) {
        LOG.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteModesOfIssuanceByModeOfIssuanceIdResponse
                .withPlainInternalServerError(MESSAGES.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  @Override
  public void putModesOfIssuanceByModeOfIssuanceId(String modeOfIssuanceId, String lang, IssuanceMode entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
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
                      if (reply.result().getUpdated() == 0) {
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutModesOfIssuanceByModeOfIssuanceIdResponse
                                .withPlainNotFound(MESSAGES.getMessage(lang, MessageConsts.NoRecordsUpdated))));
                      } else {
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutModesOfIssuanceByModeOfIssuanceIdResponse
                                .withNoContent()));
                      }
                    } else {
                      LOG.error(reply.cause().getMessage());
                      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutModesOfIssuanceByModeOfIssuanceIdResponse
                              .withPlainInternalServerError(MESSAGES.getMessage(lang, MessageConsts.InternalServerError))));
                    }
                  } catch (Exception e) {
                    LOG.error(e.getMessage(), e);
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutModesOfIssuanceByModeOfIssuanceIdResponse
                            .withPlainInternalServerError(MESSAGES.getMessage(lang, MessageConsts.InternalServerError))));
                  }
                });
      } catch (Exception e) {
        LOG.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutModesOfIssuanceByModeOfIssuanceIdResponse
                .withPlainInternalServerError(MESSAGES.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  private CQLWrapper getCQL(String query, int limit, int offset) throws FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(RESOURCE_TABLE + ".jsonb");
    return new CQLWrapper(cql2pgJson, query).setLimit(new Limit(limit)).setOffset(new Offset(offset));
  }
  
}
