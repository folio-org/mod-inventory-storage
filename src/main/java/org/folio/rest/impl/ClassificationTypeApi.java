package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.support.EndpointFailureHandler.handleFailure;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.ClassificationType;
import org.folio.services.classification.ClassificationTypeService;

/**
 * Implements the instance classification type persistency using postgres jsonb.
 */
public class ClassificationTypeApi implements org.folio.rest.jaxrs.resource.ClassificationTypes {

  @Validate
  @Override
  public void getClassificationTypes(String query, String totalRecords, int offset, int limit,
                                     Map<String, String> okapiHeaders,
                                     Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    new ClassificationTypeService(vertxContext, okapiHeaders).getByQuery(query, offset, limit)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void postClassificationTypes(ClassificationType entity, Map<String, String> okapiHeaders,
                                      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    new ClassificationTypeService(vertxContext, okapiHeaders).create(entity)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void getClassificationTypesByClassificationTypeId(String recordId, Map<String, String> okapiHeaders,
                                                           Handler<AsyncResult<Response>> asyncResultHandler,
                                                           Context vertxContext) {
    new ClassificationTypeService(vertxContext, okapiHeaders).getById(recordId)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void deleteClassificationTypesByClassificationTypeId(String recordId, Map<String, String> okapiHeaders,
                                                              Handler<AsyncResult<Response>> asyncResultHandler,
                                                              Context vertxContext) {
    new ClassificationTypeService(vertxContext, okapiHeaders).delete(recordId)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void putClassificationTypesByClassificationTypeId(String recordId, ClassificationType entity,
                                                           Map<String, String> okapiHeaders,
                                                           Handler<AsyncResult<Response>> asyncResultHandler,
                                                           Context vertxContext) {
    new ClassificationTypeService(vertxContext, okapiHeaders).update(recordId, entity)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

}
