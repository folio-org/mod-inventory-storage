package org.folio.rest.impl;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Loantype;
import org.folio.rest.jaxrs.model.Loantypes;
import org.folio.rest.jaxrs.resource.LoanTypesResource;
import org.folio.rest.persist.DatabaseExceptionUtils;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.tools.utils.TenantTool;
import org.z3950.zing.cql.CQLParseException;
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
 * Implements the loan type persistency using postgres jsonb.
 */
public class LoanTypeAPI implements LoanTypesResource {

  public static final String LOAN_TYPE_TABLE   = "loan_type";

  private static final String LOCATION_PREFIX       = "/loan-types/";
  private static final Logger log                 = LoggerFactory.getLogger(LoanTypeAPI.class);
  private final Messages messages                 = Messages.getInstance();
  private String idFieldName                      = "_id";


  public LoanTypeAPI(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName);
  }

  private CQLWrapper getCQL(String query, int limit, int offset) throws FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(LOAN_TYPE_TABLE+".jsonb");
    return new CQLWrapper(cql2pgJson, query).setLimit(new Limit(limit)).setOffset(new Offset(offset));
  }

  @Validate
  @Override
  public void getLoanTypes(String query, int offset, int limit, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) throws Exception {
    /**
     * http://host:port/loan-types
     */
    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.tenantId(okapiHeaders);
        CQLWrapper cql = getCQL(query, limit, offset);
        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(LOAN_TYPE_TABLE, Loantype.class,
            new String[]{"*"}, cql, true, true,
            reply -> {
              try {
                if (reply.succeeded()) {
                  Loantypes loantypes = new Loantypes();
                  @SuppressWarnings("unchecked")
                  List<Loantype> loantype = (List<Loantype>) reply.result()[0];
                  loantypes.setLoantypes(loantype);
                  loantypes.setTotalRecords((Integer)reply.result()[1]);
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetLoanTypesResponse.withJsonOK(
                      loantypes)));
                }
                else{
                  log.error(reply.cause().getMessage(), reply.cause());
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetLoanTypesResponse
                      .withPlainBadRequest(reply.cause().getMessage())));
                }
              } catch (Exception e) {
                log.error(e.getMessage(), e);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetLoanTypesResponse
                    .withPlainInternalServerError(messages.getMessage(
                        lang, MessageConsts.InternalServerError))));
              }
            });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        String message = messages.getMessage(lang, MessageConsts.InternalServerError);
        if (e.getCause() instanceof CQLParseException) {
          message = " CQL parse error " + e.getLocalizedMessage();
        }
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetLoanTypesResponse
            .withPlainInternalServerError(message)));
      }
    });
  }

  @Validate
  @Override
  public void postLoanTypes(String lang, Loantype entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

    vertxContext.runOnContext(v -> {
      try {
        String id = UUID.randomUUID().toString();
        if (entity.getId() == null) {
          entity.setId(id);
        }
        else{
          id = entity.getId();
        }

        String tenantId = TenantTool.tenantId(okapiHeaders);
        PostgresClient.getInstance(vertxContext.owner(), tenantId).save(
            LOAN_TYPE_TABLE, id, entity,
            reply -> {
              try {
                if (reply.succeeded()) {
                  Object ret = reply.result();
                  entity.setId((String) ret);
                  OutStream stream = new OutStream();
                  stream.setData(entity);
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostLoanTypesResponse.withJsonCreated(
                      LOCATION_PREFIX + ret, stream)));
                }
                else{
                  log.error(reply.cause().getMessage(), reply.cause());
                  if (isDuplicate(reply.cause().getMessage())) {
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostLoanTypesResponse
                        .withJsonUnprocessableEntity(
                            org.folio.rest.tools.utils.ValidationHelper.createValidationErrorMessage(
                                "name", entity.getName(), "Loan Type exists"))));
                  }
                  else{
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostLoanTypesResponse
                        .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
                  }
                }
              } catch (Exception e) {
                log.error(e.getMessage(), e);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostLoanTypesResponse
                    .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
              }
            });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PostLoanTypesResponse
            .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  @Validate
  @Override
  public void getLoanTypesByLoantypeId(String loantypeId, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) throws Exception {

    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.tenantId(okapiHeaders);

        Criterion c = new Criterion(
            new Criteria().addField(idFieldName).setJSONB(false).setOperation("=").setValue("'"+loantypeId+"'"));

        PostgresClient.getInstance(vertxContext.owner(), tenantId).get(LOAN_TYPE_TABLE, Loantype.class, c, true,
            reply -> {
              try {
                if (reply.succeeded()) {
                  @SuppressWarnings("unchecked")
                  List<Loantype> userGroup = (List<Loantype>) reply.result()[0];
                  if (userGroup.isEmpty()) {
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetLoanTypesByLoantypeIdResponse
                        .withPlainNotFound(loantypeId)));
                  }
                  else{
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetLoanTypesByLoantypeIdResponse
                        .withJsonOK(userGroup.get(0))));
                  }
                }
                else{
                  log.error(reply.cause().getMessage(), reply.cause());
                  if (isInvalidUUID(reply.cause().getMessage())) {
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetLoanTypesByLoantypeIdResponse
                        .withPlainNotFound(loantypeId)));
                  }
                  else{
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetLoanTypesByLoantypeIdResponse
                        .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
                  }
                }
              } catch (Exception e) {
                log.error(e.getMessage(), e);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetLoanTypesByLoantypeIdResponse
                    .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
              }
            });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetLoanTypesByLoantypeIdResponse
            .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  private void internalServerErrorDuringDelete(Throwable e, String lang, Handler<AsyncResult<Response>> handler) {
    log.error(e.getMessage(), e);
    handler.handle(Future.succeededFuture(DeleteLoanTypesByLoantypeIdResponse
        .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
  }

  @Validate
  @Override
  public void deleteLoanTypesByLoantypeId(String loantypeId, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) throws Exception {

    vertxContext.runOnContext(v -> {
      try {
        String tenantId = TenantTool.tenantId(okapiHeaders);
        PostgresClient postgres = PostgresClient.getInstance(vertxContext.owner(), tenantId);
        postgres.delete(LOAN_TYPE_TABLE, loantypeId,
            reply -> {
              try {
                if (reply.failed()) {
                  Throwable t = reply.cause();
                  String msg = DatabaseExceptionUtils.foreignKeyViolation(t);
                  if (msg == null) {
                    internalServerErrorDuringDelete(t, lang, asyncResultHandler);
                    return;
                  }
                  log.info(msg);
                  asyncResultHandler.handle(Future.succeededFuture(DeleteLoanTypesByLoantypeIdResponse
                      .withPlainBadRequest(msg)));
                  return;
                }
                int updated = reply.result().getUpdated();
                if (updated != 1) {
                  String msg = messages.getMessage(lang, MessageConsts.DeletedCountError, 1, updated);
                  log.error(msg);
                  asyncResultHandler.handle(Future.succeededFuture(DeleteLoanTypesByLoantypeIdResponse
                      .withPlainNotFound(msg)));
                  return;
                }
                asyncResultHandler.handle(Future.succeededFuture(DeleteLoanTypesByLoantypeIdResponse
                        .withNoContent()));
              } catch (Exception e) {
                internalServerErrorDuringDelete(e, lang, asyncResultHandler);
              }
            });
      } catch (Exception e) {
        internalServerErrorDuringDelete(e, lang, asyncResultHandler);
      }
    });
  }

  @Validate
  @Override
  public void putLoanTypesByLoantypeId(String loantypeId, String lang, Loantype entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) throws Exception {

    vertxContext.runOnContext(v -> {
      String tenantId = TenantTool.tenantId(okapiHeaders);
      try {
        if (entity.getId() == null) {
          entity.setId(loantypeId);
        }
        PostgresClient.getInstance(vertxContext.owner(), tenantId).update(
            LOAN_TYPE_TABLE, entity, loantypeId,
            reply -> {
              try {
                if (reply.succeeded()) {
                  if (reply.result().getUpdated() == 0) {
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutLoanTypesByLoantypeIdResponse
                        .withPlainNotFound(messages.getMessage(lang, MessageConsts.NoRecordsUpdated))));
                  }
                  else{
                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutLoanTypesByLoantypeIdResponse
                        .withNoContent()));
                  }
                }
                else{
                  log.error(reply.cause().getMessage());
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutLoanTypesByLoantypeIdResponse
                      .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
                }
              } catch (Exception e) {
                log.error(e.getMessage(), e);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutLoanTypesByLoantypeIdResponse
                    .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
              }
            });
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutLoanTypesByLoantypeIdResponse
            .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
      }
    });
  }

  private boolean isDuplicate(String errorMessage) {
    if (errorMessage != null && errorMessage.contains("duplicate key value violates unique constraint")) {
      return true;
    }
    return false;
  }

  private boolean isInvalidUUID(String errorMessage) {
    if (errorMessage != null && errorMessage.contains("invalid input syntax for uuid")) {
      return true;
    }
    else{
      return false;
    }
  }

}
