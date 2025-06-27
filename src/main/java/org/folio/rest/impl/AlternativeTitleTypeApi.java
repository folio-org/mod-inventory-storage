package org.folio.rest.impl;

import static org.folio.rest.tools.messages.Messages.DEFAULT_LANGUAGE;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import java.util.Map;
import java.util.UUID;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.AlternativeTitleType;
import org.folio.rest.jaxrs.model.AlternativeTitleTypes;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.support.PostgresClientFactory;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;

public class AlternativeTitleTypeApi implements org.folio.rest.jaxrs.resource.AlternativeTitleTypes {
  public static final String REFERENCE_TABLE = "alternative_title_type";

  private static final String LOCATION_PREFIX = "/alternative-title-types/";
  private static final Logger log = LogManager.getLogger();
  private final Messages messages = Messages.getInstance();

  @Validate
  @Override
  public void getAlternativeTitleTypes(String query, String totalRecords, int offset, int limit,
                                       Map<String, String> okapiHeaders,
                                       Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(REFERENCE_TABLE, AlternativeTitleType.class, AlternativeTitleTypes.class, query, offset, limit,
      okapiHeaders, vertxContext, GetAlternativeTitleTypesResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void postAlternativeTitleTypes(AlternativeTitleType entity, Map<String, String> okapiHeaders,
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
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostAlternativeTitleTypesResponse
                  .respond201WithApplicationJson(entity,
                    PostAlternativeTitleTypesResponse.headersFor201().withLocation(LOCATION_PREFIX + ret))));
              } else {
                String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                if (msg == null) {
                  internalServerErrorDuringPost(reply.cause(), asyncResultHandler);
                  return;
                }
                log.info(msg);
                asyncResultHandler.handle(Future.succeededFuture(PostAlternativeTitleTypesResponse
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
  public void getAlternativeTitleTypesById(String id,  Map<String, String> okapiHeaders,
                                           Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(REFERENCE_TABLE, AlternativeTitleType.class, id,
      okapiHeaders, vertxContext, GetAlternativeTitleTypesByIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteAlternativeTitleTypesById(String id,  Map<String, String> okapiHeaders,
                                              Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        PostgresClientFactory.getInstance(vertxContext, okapiHeaders).delete(REFERENCE_TABLE, id,
          reply -> {
            try {
              if (reply.failed()) {
                String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                if (msg == null) {
                  internalServerErrorDuringDelete(reply.cause(), asyncResultHandler);
                  return;
                }
                log.info(msg);
                asyncResultHandler.handle(Future.succeededFuture(DeleteAlternativeTitleTypesByIdResponse
                  .respond400WithTextPlain(msg)));
                return;
              }
              int updated = reply.result().rowCount();
              if (updated != 1) {
                String msg = messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.DeletedCountError, 1, updated);
                log.error(msg);
                asyncResultHandler.handle(Future.succeededFuture(DeleteAlternativeTitleTypesByIdResponse
                  .respond404WithTextPlain(msg)));
                return;
              }
              asyncResultHandler.handle(Future.succeededFuture(DeleteAlternativeTitleTypesByIdResponse
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
  public void putAlternativeTitleTypesById(String id,  AlternativeTitleType entity,
                                           Map<String, String> okapiHeaders,
                                           Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
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
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutAlternativeTitleTypesByIdResponse
                    .respond404WithTextPlain(messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.NoRecordsUpdated))));
                } else {
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutAlternativeTitleTypesByIdResponse
                    .respond204()));
                }
              } else {
                String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                if (msg == null) {
                  internalServerErrorDuringPut(reply.cause(), asyncResultHandler);
                  return;
                }
                log.info(msg);
                asyncResultHandler.handle(Future.succeededFuture(PutAlternativeTitleTypesByIdResponse
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

  private void internalServerErrorDuringPost(Throwable e,  Handler<AsyncResult<Response>> handler) {
    log.error(e.getMessage(), e);
    handler.handle(Future.succeededFuture(PostAlternativeTitleTypesResponse
      .respond500WithTextPlain(messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
  }

  private void internalServerErrorDuringDelete(Throwable e,  Handler<AsyncResult<Response>> handler) {
    log.error(e.getMessage(), e);
    handler.handle(Future.succeededFuture(DeleteAlternativeTitleTypesByIdResponse
      .respond500WithTextPlain(messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
  }

  private void internalServerErrorDuringPut(Throwable e,  Handler<AsyncResult<Response>> handler) {
    log.error(e.getMessage(), e);
    handler.handle(Future.succeededFuture(PutAlternativeTitleTypesByIdResponse
      .respond500WithTextPlain(messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
  }
}
