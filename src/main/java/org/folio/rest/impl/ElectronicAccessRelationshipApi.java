package org.folio.rest.impl;

import static org.folio.rest.tools.messages.Messages.DEFAULT_LANGUAGE;
import static org.folio.rest.tools.utils.ValidationHelper.isDuplicate;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.ElectronicAccessRelationship;
import org.folio.rest.jaxrs.model.ElectronicAccessRelationships;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.support.PostgresClientFactory;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;

public class ElectronicAccessRelationshipApi implements org.folio.rest.jaxrs.resource.ElectronicAccessRelationships {

  public static final String RESOURCE_TABLE = "electronic_access_relationship";

  private static final String LOCATION_PREFIX = "/electronic-access-relationships/";
  private static final Logger LOG = LogManager.getLogger();
  private static final Messages MESSAGES = Messages.getInstance();

  @Validate
  @Override
  public void getElectronicAccessRelationships(String query, String totalRecords, int offset, int limit,
                                               Map<String, String> okapiHeaders,
                                               Handler<AsyncResult<Response>> asyncResultHandler,
                                               Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        CQLWrapper cql = getCql(query, limit, offset);
        PostgresClientFactory.getInstance(vertxContext, okapiHeaders)
          .get(RESOURCE_TABLE, ElectronicAccessRelationship.class,
            new String[] {"*"}, cql, true, true,
            reply -> {
              try {
                if (reply.succeeded()) {
                  ElectronicAccessRelationships electronicAccessRelationships = new ElectronicAccessRelationships();
                  List<ElectronicAccessRelationship> relationships = reply.result().getResults();
                  electronicAccessRelationships.setElectronicAccessRelationships(relationships);
                  electronicAccessRelationships.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                    GetElectronicAccessRelationshipsResponse.respond200WithApplicationJson(
                      electronicAccessRelationships)));
                } else {
                  LOG.error(reply.cause().getMessage(), reply.cause());
                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(GetElectronicAccessRelationshipsResponse
                      .respond400WithTextPlain(reply.cause().getMessage())));
                }
              } catch (Exception e) {
                LOG.error(e.getMessage(), e);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetElectronicAccessRelationshipsResponse
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
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetElectronicAccessRelationshipsResponse
          .respond500WithTextPlain(message)));
      }
    });
  }

  @Validate
  @Override
  public void postElectronicAccessRelationships(ElectronicAccessRelationship entity,
                                                Map<String, String> okapiHeaders,
                                                Handler<AsyncResult<Response>> asyncResultHandler,
                                                Context vertxContext) {
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
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostElectronicAccessRelationshipsResponse
                  .respond201WithApplicationJson(entity,
                    PostElectronicAccessRelationshipsResponse.headersFor201().withLocation(LOCATION_PREFIX + ret))));
              } else {
                LOG.error(reply.cause().getMessage(), reply.cause());
                if (isDuplicate(reply.cause().getMessage())) {
                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(PostElectronicAccessRelationshipsResponse
                      .respond422WithApplicationJson(
                        org.folio.rest.tools.utils.ValidationHelper.createValidationErrorMessage(
                          "name", entity.getName(), "Relationship type exists"))));
                } else {
                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(PostElectronicAccessRelationshipsResponse
                      .respond400WithTextPlain(MESSAGES.getMessage(DEFAULT_LANGUAGE,
                        MessageConsts.InternalServerError))));
                }
              }
            } catch (Exception e) {
              LOG.error(e.getMessage(), e);
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostElectronicAccessRelationshipsResponse
                .respond500WithTextPlain(MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
            }
          });
      } catch (Exception e) {
        LOG.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostElectronicAccessRelationshipsResponse
          .respond500WithTextPlain(MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
      }
    });
  }

  @Validate
  @Override
  public void getElectronicAccessRelationshipsByElectronicAccessRelationshipId(
    String electronicAccessRelationshipId,

    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {
    PgUtil.getById(RESOURCE_TABLE, ElectronicAccessRelationship.class, electronicAccessRelationshipId,
      okapiHeaders, vertxContext, GetElectronicAccessRelationshipsByElectronicAccessRelationshipIdResponse.class,
      asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteElectronicAccessRelationshipsByElectronicAccessRelationshipId(
    String electronicAccessRelationshipId,

    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        PostgresClientFactory.getInstance(vertxContext, okapiHeaders)
          .delete(RESOURCE_TABLE, electronicAccessRelationshipId,
            reply -> {
              try {
                if (reply.succeeded()) {
                  if (reply.result().rowCount() == 1) {
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                      DeleteElectronicAccessRelationshipsByElectronicAccessRelationshipIdResponse
                        .respond204()));
                  } else {
                    LOG.error(MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.DeletedCountError,
                      1, reply.result().rowCount()));
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                      DeleteElectronicAccessRelationshipsByElectronicAccessRelationshipIdResponse
                        .respond404WithTextPlain(
                          MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.DeletedCountError,
                            1, reply.result().rowCount()))));
                  }
                } else {
                  LOG.error(reply.cause().getMessage(), reply.cause());
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                    DeleteElectronicAccessRelationshipsByElectronicAccessRelationshipIdResponse
                      .respond400WithTextPlain(MESSAGES.getMessage(DEFAULT_LANGUAGE,
                        MessageConsts.InternalServerError))));
                }
              } catch (Exception e) {
                LOG.error(e.getMessage(), e);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                  DeleteElectronicAccessRelationshipsByElectronicAccessRelationshipIdResponse
                    .respond500WithTextPlain(
                      MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
              }
            });
      } catch (Exception e) {
        LOG.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
          DeleteElectronicAccessRelationshipsByElectronicAccessRelationshipIdResponse
            .respond500WithTextPlain(MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
      }
    });
  }

  @Validate
  @Override
  public void putElectronicAccessRelationshipsByElectronicAccessRelationshipId(
    String electronicAccessRelationshipId,

    ElectronicAccessRelationship entity,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        if (entity.getId() == null) {
          entity.setId(electronicAccessRelationshipId);
        }
        PostgresClientFactory.getInstance(vertxContext, okapiHeaders)
          .update(RESOURCE_TABLE, entity, electronicAccessRelationshipId,
            reply -> {
              try {
                if (reply.succeeded()) {
                  if (reply.result().rowCount() == 0) {
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                      PutElectronicAccessRelationshipsByElectronicAccessRelationshipIdResponse
                        .respond404WithTextPlain(
                          MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.NoRecordsUpdated))));
                  } else {
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                      PutElectronicAccessRelationshipsByElectronicAccessRelationshipIdResponse
                        .respond204()));
                  }
                } else {
                  LOG.error(reply.cause().getMessage());
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                    PutElectronicAccessRelationshipsByElectronicAccessRelationshipIdResponse
                      .respond400WithTextPlain(
                        MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
                }
              } catch (Exception e) {
                LOG.error(e.getMessage(), e);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                  PutElectronicAccessRelationshipsByElectronicAccessRelationshipIdResponse
                    .respond500WithTextPlain(
                      MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
              }
            });
      } catch (Exception e) {
        LOG.error(e.getMessage(), e);
        asyncResultHandler.handle(
          io.vertx.core.Future.succeededFuture(PutElectronicAccessRelationshipsByElectronicAccessRelationshipIdResponse
            .respond500WithTextPlain(MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
      }
    });
  }

  private CQLWrapper getCql(String query, int limit, int offset) throws FieldException {
    return StorageHelper.getCql(query, limit, offset, RESOURCE_TABLE);
  }
}
