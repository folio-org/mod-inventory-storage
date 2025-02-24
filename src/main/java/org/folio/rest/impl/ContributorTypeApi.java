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
import org.folio.rest.jaxrs.model.ContributorType;
import org.folio.rest.jaxrs.model.ContributorTypes;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.TenantTool;
import org.z3950.zing.cql.CQLParseException;

/**
 * Implements the instance contributor type persistency using postgres jsonb.
 */
public class ContributorTypeApi implements org.folio.rest.jaxrs.resource.ContributorTypes {

  public static final String CONTRIBUTOR_TYPE_TABLE = "contributor_type";

  private static final String LOCATION_PREFIX = "/contributor-types/";
  private static final Logger log = LogManager.getLogger();
  private final Messages messages = Messages.getInstance();

  @Validate
  @Override
  public void getContributorTypes(String query, String totalRecords, int offset, int limit,
                                  Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
                                  Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.tenantId(okapiHeaders);
        CQLWrapper cql = getCql(query, limit, offset);
        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(CONTRIBUTOR_TYPE_TABLE, ContributorType.class,
          new String[] {"*"}, cql, true, true,
          reply -> {
            try {
              if (reply.succeeded()) {
                ContributorTypes contributorTypes = new ContributorTypes();
                List<ContributorType> contributorType = reply.result().getResults();
                contributorTypes.setContributorTypes(contributorType);
                contributorTypes.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
                asyncResultHandler.handle(
                  io.vertx.core.Future.succeededFuture(GetContributorTypesResponse.respond200WithApplicationJson(
                    contributorTypes)));
              } else {
                log.error(reply.cause().getMessage(), reply.cause());
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetContributorTypesResponse
                  .respond400WithTextPlain(reply.cause().getMessage())));
              }
            } catch (Exception e) {
              log.error(e.getMessage(), e);
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetContributorTypesResponse
                .respond500WithTextPlain(messages.getMessage(
                  DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
            }
          });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        String message = messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError);
        if (e.getCause() instanceof CQLParseException) {
          message = " CQL parse error " + e.getLocalizedMessage();
        }
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetContributorTypesResponse
          .respond500WithTextPlain(message)));
      }
    });
  }

  @Validate
  @Override
  public void postContributorTypes(ContributorType entity, Map<String, String> okapiHeaders,
                                   Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    vertxContext.runOnContext(v -> {
      try {
        String id = entity.getId();
        if (id == null) {
          id = UUID.randomUUID().toString();
          entity.setId(id);
        }

        String tenantId = TenantTool.tenantId(okapiHeaders);
        PostgresClient.getInstance(vertxContext.owner(), tenantId).save(
          CONTRIBUTOR_TYPE_TABLE, id, entity,
          reply -> {
            try {
              if (reply.succeeded()) {
                String ret = reply.result();
                entity.setId(ret);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostContributorTypesResponse
                  .respond201WithApplicationJson(entity,
                    PostContributorTypesResponse.headersFor201().withLocation(LOCATION_PREFIX + ret))));
              } else {
                String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                if (msg == null) {
                  internalServerErrorDuringPost(reply.cause(), asyncResultHandler);
                  return;
                }
                log.info(msg);
                asyncResultHandler.handle(Future.succeededFuture(PostContributorTypesResponse
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
  public void getContributorTypesByContributorTypeId(String contributorTypeId,
                                                     Map<String, String> okapiHeaders,
                                                     Handler<AsyncResult<Response>> asyncResultHandler,
                                                     Context vertxContext) {
    PgUtil.getById(CONTRIBUTOR_TYPE_TABLE, ContributorType.class, contributorTypeId,
      okapiHeaders, vertxContext, GetContributorTypesByContributorTypeIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteContributorTypesByContributorTypeId(String contributorTypeId,
                                                        Map<String, String> okapiHeaders,
                                                        Handler<AsyncResult<Response>> asyncResultHandler,
                                                        Context vertxContext) {

    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.tenantId(okapiHeaders);
        PostgresClient postgres = PostgresClient.getInstance(vertxContext.owner(), tenantId);
        postgres.delete(CONTRIBUTOR_TYPE_TABLE, contributorTypeId,
          reply -> {
            try {
              if (reply.failed()) {
                String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                if (msg == null) {
                  internalServerErrorDuringDelete(reply.cause(), asyncResultHandler);
                  return;
                }
                log.info(msg);
                asyncResultHandler.handle(Future.succeededFuture(DeleteContributorTypesByContributorTypeIdResponse
                  .respond400WithTextPlain(msg)));
                return;
              }
              int updated = reply.result().rowCount();
              if (updated != 1) {
                String msg = messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.DeletedCountError, 1, updated);
                log.error(msg);
                asyncResultHandler.handle(Future.succeededFuture(DeleteContributorTypesByContributorTypeIdResponse
                  .respond404WithTextPlain(msg)));
                return;
              }
              asyncResultHandler.handle(Future.succeededFuture(DeleteContributorTypesByContributorTypeIdResponse
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
  public void putContributorTypesByContributorTypeId(String contributorTypeId, ContributorType entity,
                                                     Map<String, String> okapiHeaders,
                                                     Handler<AsyncResult<Response>> asyncResultHandler,
                                                     Context vertxContext) {

    vertxContext.runOnContext(v -> {
      String tenantId = TenantTool.tenantId(okapiHeaders);
      try {
        if (entity.getId() == null) {
          entity.setId(contributorTypeId);
        }
        PostgresClient.getInstance(vertxContext.owner(), tenantId).update(
          CONTRIBUTOR_TYPE_TABLE, entity, contributorTypeId,
          reply -> {
            try {
              if (reply.succeeded()) {
                if (reply.result().rowCount() == 0) {
                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(PutContributorTypesByContributorTypeIdResponse
                      .respond404WithTextPlain(messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.NoRecordsUpdated))));
                } else {
                  asyncResultHandler.handle(
                    io.vertx.core.Future.succeededFuture(PutContributorTypesByContributorTypeIdResponse
                      .respond204()));
                }
              } else {
                String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                if (msg == null) {
                  internalServerErrorDuringPut(reply.cause(), asyncResultHandler);
                  return;
                }
                log.info(msg);
                asyncResultHandler.handle(Future.succeededFuture(PutContributorTypesByContributorTypeIdResponse
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
    return StorageHelper.getCql(query, limit, offset, CONTRIBUTOR_TYPE_TABLE);
  }

  private void internalServerErrorDuringPost(Throwable e, Handler<AsyncResult<Response>> handler) {
    log.error(e.getMessage(), e);
    handler.handle(Future.succeededFuture(PostContributorTypesResponse
      .respond500WithTextPlain(messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
  }

  private void internalServerErrorDuringDelete(Throwable e, Handler<AsyncResult<Response>> handler) {
    log.error(e.getMessage(), e);
    handler.handle(Future.succeededFuture(DeleteContributorTypesByContributorTypeIdResponse
      .respond500WithTextPlain(messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
  }

  private void internalServerErrorDuringPut(Throwable e, Handler<AsyncResult<Response>> handler) {
    log.error(e.getMessage(), e);
    handler.handle(Future.succeededFuture(PutContributorTypesByContributorTypeIdResponse
      .respond500WithTextPlain(messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
  }
}
