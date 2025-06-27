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
import org.folio.rest.jaxrs.model.ContributorNameType;
import org.folio.rest.jaxrs.model.ContributorNameTypes;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.PostgresClientFactory;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;

/**
 * Implements the instance contributor name type persistency using postgres jsonb.
 */
public class ContributorNameTypeApi implements org.folio.rest.jaxrs.resource.ContributorNameTypes {

  public static final String CONTRIBUTOR_NAME_TYPE_TABLE = "contributor_name_type";

  private static final String LOCATION_PREFIX = "/contributor-name-types/";
  private static final Logger log = LogManager.getLogger();
  private final Messages messages = Messages.getInstance();

  @Validate
  @Override
  public void getContributorNameTypes(String query, String totalRecords, int offset, int limit,
                                      Map<String, String> okapiHeaders,
                                      Handler<AsyncResult<Response>> asyncResultHandler,
                                      Context vertxContext) {
    PgUtil.get(CONTRIBUTOR_NAME_TYPE_TABLE, ContributorNameType.class, ContributorNameTypes.class, query, offset, limit,
      okapiHeaders, vertxContext, GetContributorNameTypesResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void postContributorNameTypes(ContributorNameType entity, Map<String, String> okapiHeaders,
                                       Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    vertxContext.runOnContext(v -> {
      try {
        String id = entity.getId();
        if (id == null) {
          id = UUID.randomUUID().toString();
          entity.setId(id);
        }

        PostgresClientFactory.getInstance(vertxContext, okapiHeaders).save(
          CONTRIBUTOR_NAME_TYPE_TABLE, id, entity,
          reply -> {
            try {
              if (reply.succeeded()) {
                String ret = reply.result();
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostContributorNameTypesResponse
                  .respond201WithApplicationJson(entity,
                    PostContributorNameTypesResponse.headersFor201().withLocation(LOCATION_PREFIX + ret))));
              } else {
                String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                if (msg == null) {
                  internalServerErrorDuringPost(reply.cause(), asyncResultHandler);
                  return;
                }
                log.info(msg);
                asyncResultHandler.handle(Future.succeededFuture(PostContributorNameTypesResponse
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
  public void getContributorNameTypesByContributorNameTypeId(String contributorNameTypeId,
                                                             Map<String, String> okapiHeaders,
                                                             Handler<AsyncResult<Response>> asyncResultHandler,
                                                             Context vertxContext) {

    PgUtil.getById(CONTRIBUTOR_NAME_TYPE_TABLE, ContributorNameType.class, contributorNameTypeId,
      okapiHeaders, vertxContext, GetContributorNameTypesByContributorNameTypeIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteContributorNameTypesByContributorNameTypeId(String contributorNameTypeId,
                                                                Map<String, String> okapiHeaders,
                                                                Handler<AsyncResult<Response>> asyncResultHandler,
                                                                Context vertxContext) {

    vertxContext.runOnContext(v -> {
      try {
        PostgresClient postgres = PostgresClientFactory.getInstance(vertxContext, okapiHeaders);
        postgres.delete(CONTRIBUTOR_NAME_TYPE_TABLE, contributorNameTypeId,
          reply -> {
            try {
              if (reply.failed()) {
                String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                if (msg == null) {
                  internalServerErrorDuringDelete(reply.cause(), asyncResultHandler);
                  return;
                }
                log.info(msg);
                asyncResultHandler.handle(
                  Future.succeededFuture(DeleteContributorNameTypesByContributorNameTypeIdResponse
                    .respond400WithTextPlain(msg)));
                return;
              }
              int updated = reply.result().rowCount();
              if (updated != 1) {
                String msg = messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.DeletedCountError, 1, updated);
                log.error(msg);
                asyncResultHandler.handle(
                  Future.succeededFuture(DeleteContributorNameTypesByContributorNameTypeIdResponse
                    .respond404WithTextPlain(msg)));
                return;
              }
              asyncResultHandler.handle(Future.succeededFuture(DeleteContributorNameTypesByContributorNameTypeIdResponse
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
  public void putContributorNameTypesByContributorNameTypeId(String contributorNameTypeId,
                                                             ContributorNameType entity,
                                                             Map<String, String> okapiHeaders,
                                                             Handler<AsyncResult<Response>> asyncResultHandler,
                                                             Context vertxContext) {

    vertxContext.runOnContext(v -> {
      try {
        if (entity.getId() == null) {
          entity.setId(contributorNameTypeId);
        }
        PostgresClientFactory.getInstance(vertxContext, okapiHeaders).update(
          CONTRIBUTOR_NAME_TYPE_TABLE, entity, contributorNameTypeId,
          reply -> {
            try {
              if (reply.succeeded()) {
                if (reply.result().rowCount() == 0) {
                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(PutContributorNameTypesByContributorNameTypeIdResponse
                      .respond404WithTextPlain(messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.NoRecordsUpdated))));
                } else {
                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(PutContributorNameTypesByContributorNameTypeIdResponse
                      .respond204()));
                }
              } else {
                String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                if (msg == null) {
                  internalServerErrorDuringPut(reply.cause(), asyncResultHandler);
                  return;
                }
                log.info(msg);
                asyncResultHandler.handle(Future.succeededFuture(PutContributorNameTypesByContributorNameTypeIdResponse
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

  private void internalServerErrorDuringPost(Throwable e, Handler<AsyncResult<Response>> handler) {
    log.error(e.getMessage(), e);
    handler.handle(Future.succeededFuture(PostContributorNameTypesResponse
      .respond500WithTextPlain(messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
  }

  private void internalServerErrorDuringDelete(Throwable e, Handler<AsyncResult<Response>> handler) {
    log.error(e.getMessage(), e);
    handler.handle(Future.succeededFuture(DeleteContributorNameTypesByContributorNameTypeIdResponse
      .respond500WithTextPlain(messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
  }

  private void internalServerErrorDuringPut(Throwable e, Handler<AsyncResult<Response>> handler) {
    log.error(e.getMessage(), e);
    handler.handle(Future.succeededFuture(PutContributorNameTypesByContributorNameTypeIdResponse
      .respond500WithTextPlain(messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
  }
}
