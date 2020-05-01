/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.folio.rest.impl;

import static org.folio.rest.tools.utils.ValidationHelper.isDuplicate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.Response;

import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.ElectronicAccessRelationship;
import org.folio.rest.jaxrs.model.ElectronicAccessRelationships;
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
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 *
 * @author ne
 */
public class ElectronicAccessRelationshipAPI implements org.folio.rest.jaxrs.resource.ElectronicAccessRelationships {

  public static final String RESOURCE_TABLE = "electronic_access_relationship";

  private static final String LOCATION_PREFIX = "/electronic-access-relationships/";
  private static final Logger LOG = LoggerFactory.getLogger(ElectronicAccessRelationshipAPI.class);
  private static final Messages MESSAGES = Messages.getInstance();

  @Override
  public void getElectronicAccessRelationships(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
        CQLWrapper cql = getCQL(query, limit, offset);
        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(RESOURCE_TABLE, ElectronicAccessRelationship.class,
                new String[]{"*"}, cql, true, true,
                reply -> {
                  try {
                    if (reply.succeeded()) {
                      ElectronicAccessRelationships electronicAccessRelationships = new ElectronicAccessRelationships();
                      List<ElectronicAccessRelationship> relationships = reply.result().getResults();
                      electronicAccessRelationships.setElectronicAccessRelationships(relationships);
                      electronicAccessRelationships.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
                      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetElectronicAccessRelationshipsResponse.respond200WithApplicationJson(
                              electronicAccessRelationships)));
                    } else {
                      LOG.error(reply.cause().getMessage(), reply.cause());
                      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetElectronicAccessRelationshipsResponse
                              .respond400WithTextPlain(reply.cause().getMessage())));
                    }
                  } catch (Exception e) {
                    LOG.error(e.getMessage(), e);
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetElectronicAccessRelationshipsResponse
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
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetElectronicAccessRelationshipsResponse
                .respond500WithTextPlain(message)));
      }
    });
  }

  @Override
  public void postElectronicAccessRelationships(String lang, ElectronicAccessRelationship entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
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
                      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostElectronicAccessRelationshipsResponse
                              .respond201WithApplicationJson(entity, PostElectronicAccessRelationshipsResponse.headersFor201().withLocation(LOCATION_PREFIX + ret))));
                    } else {
                      LOG.error(reply.cause().getMessage(), reply.cause());
                      if (isDuplicate(reply.cause().getMessage())) {
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostElectronicAccessRelationshipsResponse
                                .respond422WithApplicationJson(
                                        org.folio.rest.tools.utils.ValidationHelper.createValidationErrorMessage(
                                                "name", entity.getName(), "Relationship type exists"))));
                      } else {
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostElectronicAccessRelationshipsResponse
                                .respond400WithTextPlain(MESSAGES.getMessage(lang, MessageConsts.InternalServerError))));
                      }
                    }
                  } catch (Exception e) {
                    LOG.error(e.getMessage(), e);
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostElectronicAccessRelationshipsResponse
                            .respond500WithTextPlain(MESSAGES.getMessage(lang, MessageConsts.InternalServerError))));
                  }
                });
      } catch (Exception e) {
        LOG.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostElectronicAccessRelationshipsResponse
                .respond500WithTextPlain(MESSAGES.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  @Override
  public void getElectronicAccessRelationshipsByElectronicAccessRelationshipId(String electronicAccessRelationshipId, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(RESOURCE_TABLE, ElectronicAccessRelationship.class, electronicAccessRelationshipId,
        okapiHeaders, vertxContext, GetElectronicAccessRelationshipsByElectronicAccessRelationshipIdResponse.class, asyncResultHandler);
  }

  @Override
  public void deleteElectronicAccessRelationshipsByElectronicAccessRelationshipId(String electronicAccessRelationshipId, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
      try {
        PostgresClient.getInstance(vertxContext.owner(), tenantId).delete(RESOURCE_TABLE, electronicAccessRelationshipId,
                reply -> {
                  try {
                    if (reply.succeeded()) {
                      if (reply.result().rowCount() == 1) {
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteElectronicAccessRelationshipsByElectronicAccessRelationshipIdResponse
                                .respond204()));
                      } else {
                        LOG.error(MESSAGES.getMessage(lang, MessageConsts.DeletedCountError, 1, reply.result().rowCount()));
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteElectronicAccessRelationshipsByElectronicAccessRelationshipIdResponse
                                .respond404WithTextPlain(MESSAGES.getMessage(lang, MessageConsts.DeletedCountError, 1, reply.result().rowCount()))));
                      }
                    } else {
                      LOG.error(reply.cause().getMessage(), reply.cause());
                      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteElectronicAccessRelationshipsByElectronicAccessRelationshipIdResponse
                              .respond400WithTextPlain(MESSAGES.getMessage(lang, MessageConsts.InternalServerError))));
                    }
                  } catch (Exception e) {
                    LOG.error(e.getMessage(), e);
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteElectronicAccessRelationshipsByElectronicAccessRelationshipIdResponse
                            .respond500WithTextPlain(MESSAGES.getMessage(lang, MessageConsts.InternalServerError))));
                  }
                });
      } catch (Exception e) {
        LOG.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteElectronicAccessRelationshipsByElectronicAccessRelationshipIdResponse
                .respond500WithTextPlain(MESSAGES.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  @Override
  public void putElectronicAccessRelationshipsByElectronicAccessRelationshipId(String electronicAccessRelationshipId, String lang, ElectronicAccessRelationship entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
      try {
        if (entity.getId() == null) {
          entity.setId(electronicAccessRelationshipId);
        }
        PostgresClient.getInstance(vertxContext.owner(), tenantId).update(RESOURCE_TABLE, entity, electronicAccessRelationshipId,
                reply -> {
                  try {
                    if (reply.succeeded()) {
                      if (reply.result().rowCount() == 0) {
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutElectronicAccessRelationshipsByElectronicAccessRelationshipIdResponse
                                .respond404WithTextPlain(MESSAGES.getMessage(lang, MessageConsts.NoRecordsUpdated))));
                      } else {
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutElectronicAccessRelationshipsByElectronicAccessRelationshipIdResponse
                                .respond204()));
                      }
                    } else {
                      LOG.error(reply.cause().getMessage());
                      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutElectronicAccessRelationshipsByElectronicAccessRelationshipIdResponse
                              .respond400WithTextPlain(MESSAGES.getMessage(lang, MessageConsts.InternalServerError))));
                    }
                  } catch (Exception e) {
                    LOG.error(e.getMessage(), e);
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutElectronicAccessRelationshipsByElectronicAccessRelationshipIdResponse
                            .respond500WithTextPlain(MESSAGES.getMessage(lang, MessageConsts.InternalServerError))));
                  }
                });
      } catch (Exception e) {
        LOG.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutElectronicAccessRelationshipsByElectronicAccessRelationshipIdResponse
                .respond500WithTextPlain(MESSAGES.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  private CQLWrapper getCQL(String query, int limit, int offset) throws FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(RESOURCE_TABLE + ".jsonb");
    return new CQLWrapper(cql2pgJson, query).setLimit(new Limit(limit)).setOffset(new Offset(offset));
  }

}
