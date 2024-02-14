package org.folio.rest.impl;

import static java.util.Collections.singletonList;
import static org.folio.rest.persist.PgUtil.postgresClient;
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
import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.HoldingsType;
import org.folio.rest.jaxrs.model.HoldingsTypes;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.TenantTool;
import org.z3950.zing.cql.CQLParseException;

public class HoldingsTypeApi implements org.folio.rest.jaxrs.resource.HoldingsTypes {

  public static final String REFERENCE_TABLE = "holdings_type";

  private static final Logger log = LogManager.getLogger();
  private final Messages messages = Messages.getInstance();

  @Validate
  @Override
  public void getHoldingsTypes(String query, String totalRecords, int offset, int limit,
                               Map<String, String> okapiHeaders,
                               Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.tenantId(okapiHeaders);
        CQLWrapper cql = getCql(query, limit, offset);
        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(REFERENCE_TABLE, HoldingsType.class,
          new String[] {"*"}, cql, true, true,
          reply -> {
            try {
              if (reply.succeeded()) {
                HoldingsTypes records = new HoldingsTypes();
                List<HoldingsType> holdingsTypes = reply.result().getResults();
                records.setHoldingsTypes(holdingsTypes);
                records.setTotalRecords(reply.result().getResultInfo().getTotalRecords());
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                  GetHoldingsTypesResponse.respond200WithApplicationJson(records)));
              } else {
                log.error(reply.cause().getMessage(), reply.cause());
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetHoldingsTypesResponse
                  .respond400WithTextPlain(reply.cause().getMessage())));
              }
            } catch (Exception e) {
              log.error(e.getMessage(), e);
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetHoldingsTypesResponse
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
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetHoldingsTypesResponse
          .respond500WithTextPlain(message)));
      }
    });
  }

  @Validate
  @Override
  public void postHoldingsTypes(
    HoldingsType entity,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    if (entity.getId() == null) {
      entity.setId(UUID.randomUUID().toString());
    }

    saveHoldingsType(postgresClient(vertxContext, okapiHeaders), entity)
      .map(s -> PostHoldingsTypesResponse.respond201WithApplicationJson(
        entity, PostHoldingsTypesResponse.headersFor201()))
      .otherwise(this::handleSaveHoldingsTypeException)
      .map(Response.class::cast)
      .onComplete(asyncResultHandler);
  }

  @Validate
  @Override
  public void getHoldingsTypesById(String id, Map<String, String> okapiHeaders,
                                   Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(REFERENCE_TABLE, HoldingsType.class, id,
      okapiHeaders, vertxContext, GetHoldingsTypesByIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteHoldingsTypesById(String id, Map<String, String> okapiHeaders,
                                      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.tenantId(okapiHeaders);
        PostgresClient postgres = PostgresClient.getInstance(vertxContext.owner(), tenantId);
        postgres.delete(REFERENCE_TABLE, id,
          reply -> {
            try {
              if (reply.failed()) {
                String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                if (msg == null) {
                  internalServerErrorDuringDelete(reply.cause(), asyncResultHandler);
                  return;
                }
                log.info(msg);
                asyncResultHandler.handle(Future.succeededFuture(DeleteHoldingsTypesByIdResponse
                  .respond400WithTextPlain(msg)));
                return;
              }
              int updated = reply.result().rowCount();
              if (updated != 1) {
                String msg = messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.DeletedCountError, 1, updated);
                log.error(msg);
                asyncResultHandler.handle(Future.succeededFuture(DeleteHoldingsTypesByIdResponse
                  .respond404WithTextPlain(msg)));
                return;
              }
              asyncResultHandler.handle(Future.succeededFuture(DeleteHoldingsTypesByIdResponse
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
  public void putHoldingsTypesById(String id, HoldingsType entity, Map<String, String> okapiHeaders,
                                   Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      String tenantId = TenantTool.tenantId(okapiHeaders);
      try {
        if (entity.getId() == null) {
          entity.setId(id);
        }
        PostgresClient.getInstance(vertxContext.owner(), tenantId).update(REFERENCE_TABLE, entity, id,
          reply -> {
            try {
              if (reply.succeeded()) {
                if (reply.result().rowCount() == 0) {
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutHoldingsTypesByIdResponse
                    .respond404WithTextPlain(messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.NoRecordsUpdated))));
                } else {
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutHoldingsTypesByIdResponse
                    .respond204()));
                }
              } else {
                String msg = PgExceptionUtil.badRequestMessage(reply.cause());
                if (msg == null) {
                  internalServerErrorDuringPut(reply.cause(), asyncResultHandler);
                  return;
                }
                log.info(msg);
                asyncResultHandler.handle(Future.succeededFuture(PutHoldingsTypesByIdResponse
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
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(REFERENCE_TABLE + ".jsonb");
    return new CQLWrapper(cql2pgJson, query).setLimit(new Limit(limit)).setOffset(new Offset(offset));
  }

  private void internalServerErrorDuringDelete(Throwable e, Handler<AsyncResult<Response>> handler) {
    log.error(e.getMessage(), e);
    handler.handle(Future.succeededFuture(DeleteHoldingsTypesByIdResponse
      .respond500WithTextPlain(messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
  }

  private void internalServerErrorDuringPut(Throwable e, Handler<AsyncResult<Response>> handler) {
    log.error(e.getMessage(), e);
    handler.handle(Future.succeededFuture(PutHoldingsTypesByIdResponse
      .respond500WithTextPlain(messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
  }

  private Future<String> saveHoldingsType(PostgresClient pgClient, HoldingsType entity) {
    return Future.future(promise
      -> pgClient.save(REFERENCE_TABLE, entity.getId(), entity, promise));
  }

  private PostHoldingsTypesResponse handleSaveHoldingsTypeException(Throwable t) {
    if (PgExceptionUtil.isUniqueViolation(t)) {
      Error error = new Error()
        .withCode("name.duplicate")
        .withMessage("Cannot create entity; name is not unique")
        .withParameters(singletonList(new Parameter()
          .withKey("fieldLabel")
          .withValue("name")));

      return PostHoldingsTypesResponse
        .respond422WithApplicationJson(new Errors().withErrors(singletonList(error)));
    }

    String msg = PgExceptionUtil.badRequestMessage(t);
    if (msg != null) {
      return PostHoldingsTypesResponse.respond400WithTextPlain(msg);
    }

    return PostHoldingsTypesResponse.respond500WithTextPlain(
      "Internal Server Error, Please contact System Administrator or try again");
  }
}
