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
import org.folio.rest.jaxrs.model.Loantype;
import org.folio.rest.jaxrs.model.Loantypes;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.TenantTool;
import org.z3950.zing.cql.CQLParseException;

/**
 * Implements the loan type persistency using postgres jsonb.
 */
public class LoanTypeApi implements org.folio.rest.jaxrs.resource.LoanTypes {

  /**
   * postgresql table name of the loan type.
   */
  public static final String LOAN_TYPE_TABLE = "loan_type";

  private static final String LOCATION_PREFIX = "/loan-types/";
  private static final Logger log = LogManager.getLogger();
  private final Messages messages = Messages.getInstance();

  @Validate
  @Override
  public void getLoanTypes(String query, String totalRecords, int offset, int limit,
                           Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
                           Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        CQLWrapper cql = getCql(query, limit, offset);
        getPostgresClient(vertxContext, okapiHeaders).get(LOAN_TYPE_TABLE, Loantype.class,
          new String[] {"*"}, cql, true, true,
          reply -> {
            try {
              if (reply.succeeded()) {
                Loantypes loantypes = new Loantypes();
                List<Loantype> loantype = reply.result().getResults();
                loantypes.setLoantypes(loantype);
                loantypes.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
                asyncResultHandler.handle(
                  io.vertx.core.Future.succeededFuture(GetLoanTypesResponse.respond200WithApplicationJson(
                    loantypes)));
              } else {
                log.error(reply.cause().getMessage(), reply.cause());
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetLoanTypesResponse
                  .respond400WithTextPlain(reply.cause().getMessage())));
              }
            } catch (Exception e) {
              log.error(e.getMessage(), e);
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetLoanTypesResponse
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
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetLoanTypesResponse
          .respond500WithTextPlain(message)));
      }
    });
  }

  @Validate
  @Override
  public void deleteLoanTypes(Map<String, String> okapiHeaders,
                              Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    try {
      vertxContext.runOnContext(v -> {
        String tenantId = TenantTool.tenantId(okapiHeaders);
        getPostgresClient(vertxContext, okapiHeaders).execute(String.format("DELETE FROM %s_%s.%s",
            tenantId, "mod_inventory_storage", LOAN_TYPE_TABLE),
          reply -> {
            if (reply.succeeded()) {
              asyncResultHandler.handle(Future.succeededFuture(
                DeleteLoanTypesResponse.respond204()));
            } else {
              asyncResultHandler.handle(Future.succeededFuture(
                DeleteLoanTypesResponse.respond500WithTextPlain(reply.cause().getMessage())));
            }
          });
      });
    } catch (Exception e) {
      asyncResultHandler.handle(Future.succeededFuture(
        DeleteLoanTypesResponse.respond500WithTextPlain(e.getMessage())));
    }

  }

  @Validate
  @Override
  public void postLoanTypes(Loantype entity, Map<String, String> okapiHeaders,
                            Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    vertxContext.runOnContext(v -> {
      try {
        String id = entity.getId();
        if (id == null) {
          id = UUID.randomUUID().toString();
          entity.setId(id);
        }

        getPostgresClient(vertxContext, okapiHeaders).save(
          LOAN_TYPE_TABLE, id, entity,
          reply -> {
            try {
              if (reply.succeeded()) {
                String ret = reply.result();
                entity.setId(ret);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostLoanTypesResponse
                  .respond201WithApplicationJson(entity,
                    PostLoanTypesResponse.headersFor201().withLocation(LOCATION_PREFIX + ret))));
              } else {
                String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                if (msg == null) {
                  internalServerErrorDuringPost(reply.cause(), asyncResultHandler);
                  return;
                }
                log.info(msg);
                asyncResultHandler.handle(Future.succeededFuture(PostLoanTypesResponse
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
  public void getLoanTypesByLoantypeId(String loantypeId,
                                       Map<String, String> okapiHeaders,
                                       Handler<AsyncResult<Response>> asyncResultHandler,
                                       Context vertxContext) {
    PgUtil.getById(LOAN_TYPE_TABLE, Loantype.class, loantypeId, okapiHeaders, vertxContext,
      GetLoanTypesByLoantypeIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteLoanTypesByLoantypeId(String loantypeId,
                                          Map<String, String> okapiHeaders,
                                          Handler<AsyncResult<Response>> asyncResultHandler,
                                          Context vertxContext) {

    vertxContext.runOnContext(v -> {
      try {
        PostgresClient postgres = getPostgresClient(vertxContext, okapiHeaders);
        postgres.delete(LOAN_TYPE_TABLE, loantypeId,
          reply -> {
            try {
              if (reply.failed()) {
                String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                if (msg == null) {
                  internalServerErrorDuringDelete(reply.cause(), asyncResultHandler);
                  return;
                }
                log.info(msg);
                asyncResultHandler.handle(Future.succeededFuture(DeleteLoanTypesByLoantypeIdResponse
                  .respond400WithTextPlain(msg)));
                return;
              }
              int updated = reply.result().rowCount();
              if (updated != 1) {
                String msg = messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.DeletedCountError, 1, updated);
                log.error(msg);
                asyncResultHandler.handle(Future.succeededFuture(DeleteLoanTypesByLoantypeIdResponse
                  .respond404WithTextPlain(msg)));
                return;
              }
              asyncResultHandler.handle(Future.succeededFuture(DeleteLoanTypesByLoantypeIdResponse
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
  public void putLoanTypesByLoantypeId(String loantypeId, Loantype entity,
                                       Map<String, String> okapiHeaders,
                                       Handler<AsyncResult<Response>> asyncResultHandler,
                                       Context vertxContext) {

    vertxContext.runOnContext(v -> {
      try {
        if (entity.getId() == null) {
          entity.setId(loantypeId);
        }
        getPostgresClient(vertxContext, okapiHeaders).update(
          LOAN_TYPE_TABLE, entity, loantypeId,
          reply -> {
            try {
              if (reply.succeeded()) {
                if (reply.result().rowCount() == 0) {
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutLoanTypesByLoantypeIdResponse
                    .respond404WithTextPlain(messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.NoRecordsUpdated))));
                } else {
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutLoanTypesByLoantypeIdResponse
                    .respond204()));
                }
              } else {
                String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                if (msg == null) {
                  internalServerErrorDuringPut(reply.cause(), asyncResultHandler);
                  return;
                }
                log.info(msg);
                asyncResultHandler.handle(Future.succeededFuture(PutLoanTypesByLoantypeIdResponse
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

  /**
   * Return the PostgresClient instance for the vertx and the tenant.
   *
   * @param vertxContext context to take the vertx from
   * @param okapiHeaders headers to take the tenantId from
   * @return the PostgresClient
   */
  PostgresClient getPostgresClient(Context vertxContext, Map<String, String> okapiHeaders) {
    String tenantId = TenantTool.tenantId(okapiHeaders);
    return PostgresClient.getInstance(vertxContext.owner(), tenantId);
  }

  private void internalServerErrorDuringPost(Throwable e, Handler<AsyncResult<Response>> handler) {
    log.error(e.getMessage(), e);
    handler.handle(Future.succeededFuture(PostLoanTypesResponse
      .respond500WithTextPlain(messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
  }

  private void internalServerErrorDuringDelete(Throwable e, Handler<AsyncResult<Response>> handler) {
    log.error(e.getMessage(), e);
    handler.handle(Future.succeededFuture(DeleteLoanTypesByLoantypeIdResponse
      .respond500WithTextPlain(messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
  }

  private void internalServerErrorDuringPut(Throwable e, Handler<AsyncResult<Response>> handler) {
    log.error(e.getMessage(), e);
    handler.handle(Future.succeededFuture(PutLoanTypesByLoantypeIdResponse
      .respond500WithTextPlain(messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
  }

  private CQLWrapper getCql(String query, int limit, int offset) throws FieldException {
    return StorageHelper.getCql(query, limit, offset, LOAN_TYPE_TABLE);
  }
}
