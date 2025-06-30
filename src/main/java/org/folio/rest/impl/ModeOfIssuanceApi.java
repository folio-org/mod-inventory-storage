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
import org.folio.rest.jaxrs.model.IssuanceMode;
import org.folio.rest.jaxrs.model.IssuanceModes;
import org.folio.rest.jaxrs.resource.ModesOfIssuance;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.support.PostgresClientFactory;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;

public class ModeOfIssuanceApi implements ModesOfIssuance {
  public static final String RESOURCE_TABLE = "mode_of_issuance";

  private static final String LOCATION_PREFIX = "/modes-of-issuance/";
  private static final Logger LOG = LogManager.getLogger();
  private static final Messages MESSAGES = Messages.getInstance();

  @Validate
  @Override
  public void getModesOfIssuance(String query, String totalRecords, int offset, int limit,
                                 Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
                                 Context vertxContext) {

    vertxContext.runOnContext(v -> {
      try {
        CQLWrapper cql = getCql(query, limit, offset);
        PostgresClientFactory.getInstance(vertxContext, okapiHeaders).get(RESOURCE_TABLE, IssuanceMode.class,
          new String[] {"*"}, cql, true, true,
          reply -> {
            try {
              if (reply.succeeded()) {
                IssuanceModes issuanceModes = new IssuanceModes();
                List<IssuanceMode> modes = reply.result().getResults();
                issuanceModes.setIssuanceModes(modes);
                issuanceModes.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
                asyncResultHandler.handle(
                  io.vertx.core.Future.succeededFuture(GetModesOfIssuanceResponse.respond200WithApplicationJson(
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
                  DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
            }
          });
      } catch (Exception e) {
        LOG.error(e.getMessage(), e);
        String message = MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError);
        if (e.getCause() != null && e.getCause().getClass().getSimpleName().endsWith("CQLParseException")) {
          message = " CQL parse error " + e.getLocalizedMessage();
        }
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetModesOfIssuanceResponse
          .respond500WithTextPlain(message)));
      }
    });
  }

  @Validate
  @Override
  public void postModesOfIssuance(IssuanceMode entity, Map<String, String> okapiHeaders,
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

        PostgresClientFactory.getInstance(vertxContext, okapiHeaders)
          .save(RESOURCE_TABLE, id, entity,
            reply -> {
              try {
                if (reply.succeeded()) {
                  String ret = reply.result();
                  entity.setId(ret);
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostModesOfIssuanceResponse
                    .respond201WithApplicationJson(entity,
                      PostModesOfIssuanceResponse.headersFor201().withLocation(LOCATION_PREFIX + ret))));
                } else {
                  LOG.error(reply.cause().getMessage(), reply.cause());
                  if (isDuplicate(reply.cause().getMessage())) {
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostModesOfIssuanceResponse
                      .respond422WithApplicationJson(
                        org.folio.rest.tools.utils.ValidationHelper.createValidationErrorMessage(
                          "name", entity.getName(), "Mode of issuance exists"))));
                  } else {
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostModesOfIssuanceResponse
                      .respond400WithTextPlain(
                        MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
                  }
                }
              } catch (Exception e) {
                LOG.error(e.getMessage(), e);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostModesOfIssuanceResponse
                  .respond500WithTextPlain(MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
              }
            });
      } catch (Exception e) {
        LOG.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostModesOfIssuanceResponse
          .respond500WithTextPlain(MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
      }
    });
  }

  @Validate
  @Override
  public void deleteModesOfIssuance(Map<String, String> okapiHeaders,
                                    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    try {
      vertxContext.runOnContext(v -> PostgresClientFactory.getInstance(vertxContext, okapiHeaders)
        .delete(RESOURCE_TABLE, new Criterion(),
          reply -> {
            if (reply.succeeded()) {
              asyncResultHandler.handle(Future.succeededFuture(
                DeleteModesOfIssuanceResponse.respond204()));
            } else {
              asyncResultHandler.handle(Future.succeededFuture(
                DeleteModesOfIssuanceResponse.respond500WithTextPlain(reply.cause().getMessage())));
            }
          }));
    } catch (Exception e) {
      asyncResultHandler.handle(Future.succeededFuture(
        DeleteModesOfIssuanceResponse.respond500WithTextPlain(e.getMessage())));
    }
  }

  @Validate
  @Override
  public void getModesOfIssuanceByModeOfIssuanceId(String modeOfIssuanceId,
                                                   Map<String, String> okapiHeaders,
                                                   Handler<AsyncResult<Response>> asyncResultHandler,
                                                   Context vertxContext) {
    PgUtil.getById(RESOURCE_TABLE, IssuanceMode.class, modeOfIssuanceId, okapiHeaders, vertxContext,
      GetModesOfIssuanceByModeOfIssuanceIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteModesOfIssuanceByModeOfIssuanceId(String modeOfIssuanceId,
                                                      Map<String, String> okapiHeaders,
                                                      Handler<AsyncResult<Response>> asyncResultHandler,
                                                      Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        PostgresClientFactory.getInstance(vertxContext, okapiHeaders)
          .delete(RESOURCE_TABLE, modeOfIssuanceId,
            reply -> {
              try {
                if (reply.succeeded()) {
                  if (reply.result().rowCount() == 1) {
                    asyncResultHandler.handle(
                      io.vertx.core.Future.succeededFuture(DeleteModesOfIssuanceByModeOfIssuanceIdResponse
                        .respond204()));
                  } else {
                    LOG.error(MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.DeletedCountError, 1,
                      reply.result().rowCount()));
                    asyncResultHandler.handle(
                      io.vertx.core.Future.succeededFuture(DeleteModesOfIssuanceByModeOfIssuanceIdResponse
                        .respond404WithTextPlain(
                          MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.DeletedCountError, 1,
                            reply.result().rowCount()))));
                  }
                } else {
                  LOG.error(reply.cause().getMessage(), reply.cause());
                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(DeleteModesOfIssuanceByModeOfIssuanceIdResponse
                      .respond400WithTextPlain(
                        MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
                }
              } catch (Exception e) {
                LOG.error(e.getMessage(), e);
                asyncResultHandler.handle(
                  io.vertx.core.Future.succeededFuture(DeleteModesOfIssuanceByModeOfIssuanceIdResponse
                    .respond500WithTextPlain(
                      MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
              }
            });
      } catch (Exception e) {
        LOG.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteModesOfIssuanceByModeOfIssuanceIdResponse
          .respond500WithTextPlain(MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
      }
    });
  }

  @Validate
  @Override
  public void putModesOfIssuanceByModeOfIssuanceId(String modeOfIssuanceId, IssuanceMode entity,
                                                   Map<String, String> okapiHeaders,
                                                   Handler<AsyncResult<Response>> asyncResultHandler,
                                                   Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        if (entity.getId() == null) {
          entity.setId(modeOfIssuanceId);
        }
        PostgresClientFactory.getInstance(vertxContext, okapiHeaders)
          .update(RESOURCE_TABLE, entity, modeOfIssuanceId,
            reply -> {
              try {
                if (reply.succeeded()) {
                  if (reply.result().rowCount() == 0) {
                    asyncResultHandler.handle(
                      io.vertx.core.Future.succeededFuture(PutModesOfIssuanceByModeOfIssuanceIdResponse
                        .respond404WithTextPlain(
                          MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.NoRecordsUpdated))));
                  } else {
                    asyncResultHandler.handle(
                      io.vertx.core.Future.succeededFuture(PutModesOfIssuanceByModeOfIssuanceIdResponse
                        .respond204()));
                  }
                } else {
                  LOG.error(reply.cause().getMessage());
                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(PutModesOfIssuanceByModeOfIssuanceIdResponse
                      .respond400WithTextPlain(
                        MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
                }
              } catch (Exception e) {
                LOG.error(e.getMessage(), e);
                asyncResultHandler.handle(
                  io.vertx.core.Future.succeededFuture(PutModesOfIssuanceByModeOfIssuanceIdResponse
                    .respond500WithTextPlain(
                      MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
              }
            });
      } catch (Exception e) {
        LOG.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutModesOfIssuanceByModeOfIssuanceIdResponse
          .respond500WithTextPlain(MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
      }
    });
  }

  private CQLWrapper getCql(String query, int limit, int offset) throws FieldException {
    return StorageHelper.getCql(query, limit, offset, RESOURCE_TABLE);
  }
}
