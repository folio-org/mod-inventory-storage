/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.folio.rest.impl;

import static org.folio.rest.support.EndpointFailureHandler.handleFailure;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.annotations.Validate;
import org.folio.rest.exceptions.BadRequestException;
import org.folio.rest.jaxrs.model.CallNumberType;
import org.folio.rest.jaxrs.model.CallNumberTypes;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.TenantTool;

public class CallNumberTypeApi implements org.folio.rest.jaxrs.resource.CallNumberTypes {

  private static final Logger log = LogManager.getLogger();

  private static final String REFERENCE_TABLE = "call_number_type";
  private static final String SYSTEM_CALL_NUMBER_TYPE_SOURCE = "system";

  private final Messages messages = Messages.getInstance();

  @Validate
  @Override
  public void getCallNumberTypes(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
                                 Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(REFERENCE_TABLE, CallNumberType.class, CallNumberTypes.class, query, offset, limit,
      okapiHeaders, vertxContext, GetCallNumberTypesResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void postCallNumberTypes(String lang, CallNumberType entity, Map<String, String> okapiHeaders,
                                  Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(REFERENCE_TABLE, entity, okapiHeaders, vertxContext, PostCallNumberTypesResponse.class,
      asyncResultHandler);
  }

  @Validate
  @Override
  public void getCallNumberTypesById(String id, String lang, Map<String, String> okapiHeaders,
                                     Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(REFERENCE_TABLE, CallNumberType.class, id,
      okapiHeaders, vertxContext, GetCallNumberTypesByIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteCallNumberTypesById(String id, String lang, Map<String, String> okapiHeaders,
                                        Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      String tenantId = TenantTool.tenantId(okapiHeaders);
      checkIfSystemCallNumberType(id, vertxContext, tenantId, "System call number type couldn't be deleted")
        .compose(callNumberType -> PgUtil.deleteById(REFERENCE_TABLE, id, okapiHeaders, vertxContext,
          DeleteCallNumberTypesByIdResponse.class))
        .onFailure(handleFailure(asyncResultHandler))
        .onSuccess(event -> asyncResultHandler.handle(Future.succeededFuture(event)));
    } catch (Exception e) {
      internalServerErrorDuringDelete(e, lang, asyncResultHandler);
    }
  }

  @Validate
  @Override
  public void putCallNumberTypesById(String id,
                                     String lang,
                                     CallNumberType entity,
                                     Map<String, String> okapiHeaders,
                                     Handler<AsyncResult<Response>> asyncResultHandler,
                                     Context vertxContext) {
    try {
      String tenantId = TenantTool.tenantId(okapiHeaders);
      checkIfSystemCallNumberType(id, vertxContext, tenantId, "System call number type couldn't be updated")
        .compose(callNumberType -> PgUtil.put(REFERENCE_TABLE,
          entity,
          id,
          okapiHeaders,
          vertxContext,
          PutCallNumberTypesByIdResponse.class))
        .onFailure(handleFailure(asyncResultHandler))
        .onSuccess(event -> asyncResultHandler.handle(Future.succeededFuture(event)));
    } catch (Exception e) {
      internalServerErrorDuringPut(e, lang, asyncResultHandler);
    }
  }

  private Future<CallNumberType> checkIfSystemCallNumberType(String id, Context vertxContext, String tenantId,
                                                             String message) {
    var instance = PostgresClient.getInstance(vertxContext.owner(), tenantId);
    return instance.getById(REFERENCE_TABLE, id, CallNumberType.class)
      .compose(callNumberType -> {
        if (isSystemSource(callNumberType)) {
          return Future.failedFuture(new BadRequestException(message));
        }
        return Future.succeededFuture(callNumberType);
      });
  }

  private boolean isSystemSource(CallNumberType callNumberType) {
    return SYSTEM_CALL_NUMBER_TYPE_SOURCE.equals(callNumberType.getSource());
  }

  private void internalServerErrorDuringDelete(Throwable e, String lang, Handler<AsyncResult<Response>> handler) {
    log.error(e.getMessage(), e);
    handler.handle(Future.succeededFuture(DeleteCallNumberTypesByIdResponse
      .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
  }

  private void internalServerErrorDuringPut(Throwable e, String lang, Handler<AsyncResult<Response>> handler) {
    log.error(e.getMessage(), e);
    handler.handle(Future.succeededFuture(PutCallNumberTypesByIdResponse
      .respond500WithTextPlain(messages.getMessage(lang, MessageConsts.InternalServerError))));
  }
}
