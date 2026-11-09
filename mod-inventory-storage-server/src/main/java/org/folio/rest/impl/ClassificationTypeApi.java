package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.support.ResponseHandlerUtil.firstErrorMessage;
import static org.folio.rest.support.ResponseHandlerUtil.handleFailure;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.ClassificationType;
import org.folio.services.classification.ClassificationTypeService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings("java:S6813")
public class ClassificationTypeApi implements org.folio.rest.jaxrs.resource.ClassificationTypes {

  @Autowired
  private ClassificationTypeService service;

  public ClassificationTypeApi() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Validate
  @Override
  public void getClassificationTypes(String query, String totalRecords, int offset, int limit,
                                     Map<String, String> headers,
                                     Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    service.getByQuery(query, offset, limit, totalRecords, headers)
      .map(GetClassificationTypesResponse::respond200WithApplicationJson)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void postClassificationTypes(ClassificationType entity, Map<String, String> headers,
                                      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    service.create(entity, headers)
      .map(created -> PostClassificationTypesResponse.respond201WithApplicationJson(created,
        PostClassificationTypesResponse.headersFor201().withLocation(created.getId())))
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void getClassificationTypesByClassificationTypeId(String recordId, Map<String, String> headers,
                                                           Handler<AsyncResult<Response>> asyncResultHandler,
                                                           Context vertxContext) {
    service.getByTypeId(recordId, headers)
      .map(GetClassificationTypesByClassificationTypeIdResponse::respond200WithApplicationJson)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler, errors ->
        GetClassificationTypesByClassificationTypeIdResponse.respond404WithTextPlain(firstErrorMessage(errors))));
  }

  @Validate
  @Override
  public void deleteClassificationTypesByClassificationTypeId(String recordId, Map<String, String> headers,
                                                              Handler<AsyncResult<Response>> asyncResultHandler,
                                                              Context vertxContext) {
    service.delete(recordId, headers)
      .map(notUsed -> DeleteClassificationTypesByClassificationTypeIdResponse.respond204())
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler, errors ->
        DeleteClassificationTypesByClassificationTypeIdResponse.respond400WithTextPlain(firstErrorMessage(errors))));
  }

  @Validate
  @Override
  public void putClassificationTypesByClassificationTypeId(String recordId, ClassificationType entity,
                                                           Map<String, String> headers,
                                                           Handler<AsyncResult<Response>> asyncResultHandler,
                                                           Context vertxContext) {
    service.update(recordId, entity, headers)
      .map(notUsed -> PutClassificationTypesByClassificationTypeIdResponse.respond204())
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler, errors ->
        PutClassificationTypesByClassificationTypeIdResponse.respond400WithTextPlain(firstErrorMessage(errors))));
  }
}
