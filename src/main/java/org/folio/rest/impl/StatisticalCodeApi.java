package org.folio.rest.impl;

import static org.folio.rest.tools.messages.Messages.DEFAULT_LANGUAGE;

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
import org.folio.rest.jaxrs.model.StatisticalCode;
import org.folio.rest.jaxrs.model.StatisticalCodes;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.support.PostgresClientFactory;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.z3950.zing.cql.CQLParseException;

public class StatisticalCodeApi implements org.folio.rest.jaxrs.resource.StatisticalCodes {
  public static final String REFERENCE_TABLE = "statistical_code";

  private static final String LOCATION_PREFIX = "/statistical-codes/";
  private static final Logger LOG = LogManager.getLogger();
  private static final Messages MESSAGES = Messages.getInstance();

  @Validate
  @Override
  public void getStatisticalCodes(String query, String totalRecords, int offset, int limit,
                                  Map<String, String> okapiHeaders,
                                  Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        CQLWrapper cql = getCql(query, limit, offset);
        PostgresClientFactory.getInstance(vertxContext, okapiHeaders).get(REFERENCE_TABLE, StatisticalCode.class,
          new String[] {"*"}, cql, true, true,
          reply -> {
            try {
              if (reply.succeeded()) {
                StatisticalCodes records = new StatisticalCodes();
                List<StatisticalCode> statisticalCodes = reply.result().getResults();
                records.setStatisticalCodes(statisticalCodes);
                records.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                  GetStatisticalCodesResponse.respond200WithApplicationJson(records)));
              } else {
                LOG.error(reply.cause().getMessage(), reply.cause());
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetStatisticalCodesResponse
                  .respond400WithTextPlain(reply.cause().getMessage())));
              }
            } catch (Exception e) {
              LOG.error(e.getMessage(), e);
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetStatisticalCodesResponse
                .respond500WithTextPlain(MESSAGES.getMessage(
                  DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
            }
          });
      } catch (Exception e) {
        LOG.error(e.getMessage(), e);
        String message = MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError);
        if (e.getCause() instanceof CQLParseException) {
          message = " CQL parse error " + e.getLocalizedMessage();
        }
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetStatisticalCodesResponse
          .respond500WithTextPlain(message)));
      }
    });
  }

  @Validate
  @Override
  public void postStatisticalCodes(StatisticalCode entity, Map<String, String> okapiHeaders,
                                   Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        String id = entity.getId();
        if (id == null) {
          id = UUID.randomUUID().toString();
          entity.setId(id);
        }

        PostgresClientFactory.getInstance(vertxContext, okapiHeaders).save(REFERENCE_TABLE, id, entity,
          reply -> {
            try {
              if (reply.succeeded()) {
                String ret = reply.result();
                entity.setId(ret);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostStatisticalCodesResponse
                  .respond201WithApplicationJson(entity,
                    PostStatisticalCodesResponse.headersFor201().withLocation(LOCATION_PREFIX + ret))));
              } else {
                String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                if (msg == null) {
                  internalServerErrorDuringPost(reply.cause(), asyncResultHandler);
                  return;
                }
                LOG.info(msg);
                asyncResultHandler.handle(Future.succeededFuture(PostStatisticalCodesResponse
                  .respond400WithTextPlain(msg)));
              }
            } catch (Exception e) {
              internalServerErrorDuringPost(e, asyncResultHandler);
            }
          });
      } catch (Exception e) {
        internalServerErrorDuringPost(e, asyncResultHandler);
      }
    });
  }

  @Validate
  @Override
  public void getStatisticalCodesByStatisticalCodeId(String id, Map<String, String> okapiHeaders,
                                                     Handler<AsyncResult<Response>> asyncResultHandler,
                                                     Context vertxContext) {
    PgUtil.getById(REFERENCE_TABLE, StatisticalCode.class, id, okapiHeaders, vertxContext,
      GetStatisticalCodesByStatisticalCodeIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteStatisticalCodesByStatisticalCodeId(String id, Map<String, String> okapiHeaders,
                                                        Handler<AsyncResult<Response>> asyncResultHandler,
                                                        Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        PostgresClientFactory.getInstance(vertxContext, okapiHeaders)
          .delete(REFERENCE_TABLE, id,
            reply -> {
              try {
                if (reply.failed()) {
                  String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                  if (msg == null) {
                    internalServerErrorDuringDelete(reply.cause(), asyncResultHandler);
                    return;
                  }
                  LOG.info(msg);
                  asyncResultHandler.handle(Future.succeededFuture(DeleteStatisticalCodesByStatisticalCodeIdResponse
                    .respond400WithTextPlain(msg)));
                  return;
                }
                int updated = reply.result().rowCount();
                if (updated != 1) {
                  String msg = MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.DeletedCountError, 1, updated);
                  LOG.error(msg);
                  asyncResultHandler.handle(Future.succeededFuture(DeleteStatisticalCodesByStatisticalCodeIdResponse
                    .respond404WithTextPlain(msg)));
                  return;
                }
                asyncResultHandler.handle(Future.succeededFuture(DeleteStatisticalCodesByStatisticalCodeIdResponse
                  .respond204()));
              } catch (Exception e) {
                internalServerErrorDuringDelete(e, asyncResultHandler);
              }
            });
      } catch (Exception e) {
        internalServerErrorDuringDelete(e, asyncResultHandler);
      }
    });
  }

  @Validate
  @Override
  public void putStatisticalCodesByStatisticalCodeId(String id, StatisticalCode entity,
                                                     Map<String, String> okapiHeaders,
                                                     Handler<AsyncResult<Response>> asyncResultHandler,
                                                     Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        if (entity.getId() == null) {
          entity.setId(id);
        }
        PostgresClientFactory.getInstance(vertxContext, okapiHeaders).update(REFERENCE_TABLE, entity, id,
          reply -> {
            try {
              if (reply.succeeded()) {
                if (reply.result().rowCount() == 0) {
                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(PutStatisticalCodesByStatisticalCodeIdResponse
                      .respond404WithTextPlain(MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.NoRecordsUpdated))));
                } else {
                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(PutStatisticalCodesByStatisticalCodeIdResponse
                      .respond204()));
                }
              } else {
                String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                if (msg == null) {
                  internalServerErrorDuringPut(reply.cause(), asyncResultHandler);
                  return;
                }
                LOG.info(msg);
                asyncResultHandler.handle(Future.succeededFuture(PutStatisticalCodesByStatisticalCodeIdResponse
                  .respond400WithTextPlain(msg)));
              }
            } catch (Exception e) {
              internalServerErrorDuringPut(e, asyncResultHandler);
            }
          });
      } catch (Exception e) {
        internalServerErrorDuringPut(e, asyncResultHandler);
      }
    });
  }

  private CQLWrapper getCql(String query, int limit, int offset) throws FieldException {
    return StorageHelper.getCql(query, limit, offset, REFERENCE_TABLE);
  }

  private void internalServerErrorDuringPost(Throwable e, Handler<AsyncResult<Response>> handler) {
    LOG.error(e.getMessage(), e);
    handler.handle(Future.succeededFuture(PostStatisticalCodesResponse
      .respond500WithTextPlain(MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
  }

  private void internalServerErrorDuringDelete(Throwable e, Handler<AsyncResult<Response>> handler) {
    LOG.error(e.getMessage(), e);
    handler.handle(Future.succeededFuture(DeleteStatisticalCodesByStatisticalCodeIdResponse
      .respond500WithTextPlain(MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
  }

  private void internalServerErrorDuringPut(Throwable e, Handler<AsyncResult<Response>> handler) {
    LOG.error(e.getMessage(), e);
    handler.handle(Future.succeededFuture(PutStatisticalCodesByStatisticalCodeIdResponse
      .respond500WithTextPlain(MESSAGES.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
  }
}
