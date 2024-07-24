package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.rest.jaxrs.model.SubjectType;
import org.folio.rest.jaxrs.resource.SubjectTypes;
import org.folio.services.subjecttype.SubjectTypeService;

import javax.ws.rs.core.Response;
import java.util.Map;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.support.EndpointFailureHandler.handleFailure;

public class SubjectTypeApi implements SubjectTypes {

  @Override
  public void getSubjectTypes(String query, String totalRecords, int offset, int limit,
                              Map<String, String> okapiHeaders,
                              Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    new SubjectTypeService(vertxContext, okapiHeaders)
      .getByQuery(query, offset, limit)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Override
  public void postSubjectTypes(SubjectType entity,
                               Map<String, String> okapiHeaders,
                               Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    new SubjectTypeService(vertxContext, okapiHeaders)
      .create(entity)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Override
  public void getSubjectTypesBySubjectTypeId(String subjectTypeId, Map<String, String> okapiHeaders,
                                             Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    new SubjectTypeService(vertxContext, okapiHeaders)
      .getById(subjectTypeId)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Override
  public void deleteSubjectTypesBySubjectTypeId(String subjectTypeId, Map<String, String> okapiHeaders,
                                                Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    new SubjectTypeService(vertxContext, okapiHeaders)
      .delete(subjectTypeId)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Override
  public void putSubjectTypesBySubjectTypeId(String subjectTypeId, SubjectType entity, Map<String, String> okapiHeaders,
                                             Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    new SubjectTypeService(vertxContext, okapiHeaders)
      .update(subjectTypeId, entity)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }
}
