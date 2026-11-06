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
import org.folio.rest.jaxrs.model.SubjectType;
import org.folio.rest.jaxrs.resource.SubjectTypes;
import org.folio.services.subjecttype.SubjectTypeService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings("java:S6813")
public class SubjectTypeApi implements SubjectTypes {

  @Autowired
  private SubjectTypeService service;

  public SubjectTypeApi() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Override
  public void getSubjectTypes(String query, String totalRecords, int offset, int limit,
                              Map<String, String> headers,
                              Handler<AsyncResult<Response>> asyncResultHandler,
                              Context vertxContext) {
    service.getByQuery(query, offset, limit, totalRecords, headers)
      .map(GetSubjectTypesResponse::respond200WithApplicationJson)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Override
  public void postSubjectTypes(SubjectType entity,
                               Map<String, String> headers,
                               Handler<AsyncResult<Response>> asyncResultHandler,
                               Context vertxContext) {
    service.create(entity, headers)
      .map(created -> PostSubjectTypesResponse.respond201WithApplicationJson(created,
        PostSubjectTypesResponse.headersFor201().withLocation(created.getId())))
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Override
  public void putSubjectTypesBySubjectTypeId(String subjectTypeId, SubjectType entity,
                                             Map<String, String> headers,
                                             Handler<AsyncResult<Response>> asyncResultHandler,
                                             Context vertxContext) {
    service.update(subjectTypeId, entity, headers)
      .map(notUsed -> PutSubjectTypesBySubjectTypeIdResponse.respond204())
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Override
  public void getSubjectTypesBySubjectTypeId(String subjectTypeId,
                                             Map<String, String> headers,
                                             Handler<AsyncResult<Response>> asyncResultHandler,
                                             Context vertxContext) {
    service.getById(subjectTypeId, headers)
      .map(GetSubjectTypesBySubjectTypeIdResponse::respond200WithApplicationJson)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler, errors ->
        GetSubjectTypesBySubjectTypeIdResponse.respond404WithTextPlain(firstErrorMessage(errors))));
  }

  @Override
  public void deleteSubjectTypesBySubjectTypeId(String subjectTypeId,
                                                Map<String, String> headers,
                                                Handler<AsyncResult<Response>> asyncResultHandler,
                                                Context vertxContext) {
    service.delete(subjectTypeId, headers)
      .map(notUsed -> DeleteSubjectTypesBySubjectTypeIdResponse.respond204())
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler, errors ->
        DeleteSubjectTypesBySubjectTypeIdResponse.respond400WithTextPlain(firstErrorMessage(errors))));
  }
}
