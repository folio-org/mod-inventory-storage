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
import org.folio.rest.jaxrs.model.CatalogingLevel;
import org.folio.rest.jaxrs.model.CatalogingLevels;
import org.folio.rest.jaxrs.resource.CatalogingLevelsResource;
import org.folio.rest.jaxrs.resource.CatalogingLevelsResource.DeleteCatalogingLevelsResponse;
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
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 *
 * @author ne
 */
public class CatalogingLevelAPI implements CatalogingLevelsResource {

  public static final String RESOURCE_TABLE = "cataloging_level";

  private static final String LOCATION_PREFIX = "/cataloging-levels/";
  private static final Logger log = LoggerFactory.getLogger(CatalogingLevelAPI.class);
  private static final Messages messages = Messages.getInstance();
  private static final String idFieldName = "_id";

  public CatalogingLevelAPI(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName);
  }

  @Override
  public void deleteCatalogingLevels(String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

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
                            DeleteCatalogingLevelsResponse.noContent()
                                    .build()));
                  } else {
                    asyncResultHandler.handle(Future.succeededFuture(
                            DeleteCatalogingLevelsResponse.
                                    withPlainInternalServerError(reply.cause().getMessage())));
                  }
                });
      });
    } catch (Exception e) {
      asyncResultHandler.handle(Future.succeededFuture(
              DeleteCatalogingLevelsResponse.
                      withPlainInternalServerError(e.getMessage())));
    }
  }

  @Override
  public void getCatalogingLevels(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
        CQLWrapper cql = getCQL(query, limit, offset);
        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(RESOURCE_TABLE, CatalogingLevel.class,
                new String[]{"*"}, cql, true, true,
                reply -> {
                  try {
                    if (reply.succeeded()) {
                      CatalogingLevels catalogingLevels = new CatalogingLevels();
                      @SuppressWarnings("unchecked")
                      List<CatalogingLevel> levels = (List<CatalogingLevel>) reply.result().getResults();
                      catalogingLevels.setCatalogingLevels(levels);
                      catalogingLevels.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
                      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetCatalogingLevelsResponse.withJsonOK(
                              catalogingLevels)));
                    } else {
                      log.error(reply.cause().getMessage(), reply.cause());
                      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetCatalogingLevelsResponse
                              .withPlainBadRequest(reply.cause().getMessage())));
                    }
                  } catch (Exception e) {
                    log.error(e.getMessage(), e);
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetCatalogingLevelsResponse
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
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetCatalogingLevelsResponse
                .withPlainInternalServerError(message)));
      }
    });

  }

  @Override
  public void postCatalogingLevels(String lang, CatalogingLevel entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
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
                      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostCatalogingLevelsResponse.withJsonCreated(
                              LOCATION_PREFIX + ret, stream)));
                    } else {
                      log.error(reply.cause().getMessage(), reply.cause());
                      if (isDuplicate(reply.cause().getMessage())) {
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostCatalogingLevelsResponse
                                .withJsonUnprocessableEntity(
                                        org.folio.rest.tools.utils.ValidationHelper.createValidationErrorMessage(
                                                "name", entity.getName(), "Material Type exists"))));
                      } else {
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostCatalogingLevelsResponse
                                .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
                      }
                    }
                  } catch (Exception e) {
                    log.error(e.getMessage(), e);
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostCatalogingLevelsResponse
                            .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
                  }
                });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostCatalogingLevelsResponse
                .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  @Override
  public void getCatalogingLevelsByCatalogingLevelId(String catalogingLevelId, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));

        Criterion c = new Criterion(
                new Criteria().addField(idFieldName).setJSONB(false).setOperation("=").setValue("'" + catalogingLevelId + "'"));

        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(RESOURCE_TABLE, CatalogingLevel.class, c, true,
                reply -> {
                  try {
                    if (reply.succeeded()) {
                      @SuppressWarnings("unchecked")
                      List<CatalogingLevel> userGroup = (List<CatalogingLevel>) reply.result().getResults();
                      if (userGroup.isEmpty()) {
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetCatalogingLevelsByCatalogingLevelIdResponse
                                .withPlainNotFound(catalogingLevelId)));
                      } else {
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetCatalogingLevelsByCatalogingLevelIdResponse
                                .withJsonOK(userGroup.get(0))));
                      }
                    } else {
                      log.error(reply.cause().getMessage(), reply.cause());
                      if (isInvalidUUID(reply.cause().getMessage())) {
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetCatalogingLevelsByCatalogingLevelIdResponse
                                .withPlainNotFound(catalogingLevelId)));
                      } else {
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetCatalogingLevelsByCatalogingLevelIdResponse
                                .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
                      }
                    }
                  } catch (Exception e) {
                    log.error(e.getMessage(), e);
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetCatalogingLevelsByCatalogingLevelIdResponse
                            .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
                  }
                });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetCatalogingLevelsByCatalogingLevelIdResponse
                .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  @Override
  public void deleteCatalogingLevelsByCatalogingLevelId(String catalogingLevelId, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    vertxContext.runOnContext(v -> {
      String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
      try {
        PostgresClient.getInstance(vertxContext.owner(), tenantId).delete(RESOURCE_TABLE, catalogingLevelId,
                reply -> {
                  try {
                    if (reply.succeeded()) {
                      if (reply.result().getUpdated() == 1) {
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteCatalogingLevelsByCatalogingLevelIdResponse
                                .withNoContent()));
                      } else {
                        log.error(messages.getMessage(lang, MessageConsts.DeletedCountError, 1, reply.result().getUpdated()));
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteCatalogingLevelsByCatalogingLevelIdResponse
                                .withPlainNotFound(messages.getMessage(lang, MessageConsts.DeletedCountError, 1, reply.result().getUpdated()))));
                      }
                    } else {
                      log.error(reply.cause().getMessage(), reply.cause());
                      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteCatalogingLevelsByCatalogingLevelIdResponse
                              .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
                    }
                  } catch (Exception e) {
                    log.error(e.getMessage(), e);
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteCatalogingLevelsByCatalogingLevelIdResponse
                            .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
                  }
                });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteCatalogingLevelsByCatalogingLevelIdResponse
                .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  @Override
  public void putCatalogingLevelsByCatalogingLevelId(String catalogingLevelId, String lang, CatalogingLevel entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    vertxContext.runOnContext(v -> {
      String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
      try {
        if (entity.getId() == null) {
          entity.setId(catalogingLevelId);
        }
        PostgresClient.getInstance(vertxContext.owner(), tenantId).update(RESOURCE_TABLE, entity, catalogingLevelId,
                reply -> {
                  try {
                    if (reply.succeeded()) {
                      if (reply.result().getUpdated() == 0) {
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutCatalogingLevelsByCatalogingLevelIdResponse
                                .withPlainNotFound(messages.getMessage(lang, MessageConsts.NoRecordsUpdated))));
                      } else {
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutCatalogingLevelsByCatalogingLevelIdResponse
                                .withNoContent()));
                      }
                    } else {
                      log.error(reply.cause().getMessage());
                      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutCatalogingLevelsByCatalogingLevelIdResponse
                              .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
                    }
                  } catch (Exception e) {
                    log.error(e.getMessage(), e);
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutCatalogingLevelsByCatalogingLevelIdResponse
                            .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
                  }
                });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutCatalogingLevelsByCatalogingLevelIdResponse
                .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  private CQLWrapper getCQL(String query, int limit, int offset) throws FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(RESOURCE_TABLE + ".jsonb");
    return new CQLWrapper(cql2pgJson, query).setLimit(new Limit(limit)).setOffset(new Offset(offset));
  }

}
