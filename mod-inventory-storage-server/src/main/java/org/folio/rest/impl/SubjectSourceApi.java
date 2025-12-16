package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.support.EndpointFailureHandler.handleFailure;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.jaxrs.model.SubjectSource;
import org.folio.rest.jaxrs.resource.SubjectSources;
import org.folio.services.subjectsource.SubjectSourceService;

public class SubjectSourceApi implements SubjectSources {

  @Override
  public void getSubjectSources(String query, String totalRecords, int offset,
                                int limit,
                                Map<String, String> okapiHeaders,
                                Handler<AsyncResult<Response>> asyncResultHandler,
                                Context vertxContext) {
    new SubjectSourceService(vertxContext, okapiHeaders)
      .getByQuery(query, offset, limit)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Override
  public void postSubjectSources(SubjectSource entity,
                                 Map<String, String> okapiHeaders,
                                 Handler<AsyncResult<Response>> asyncResultHandler,
                                 Context vertxContext) {
    new SubjectSourceService(vertxContext, okapiHeaders)
      .create(entity)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Override
  public void getSubjectSourcesBySubjectSourceId(String subjectSourceId,
                                                 Map<String, String> okapiHeaders,
                                                 Handler<AsyncResult<Response>> asyncResultHandler,
                                                 Context vertxContext) {
    new SubjectSourceService(vertxContext, okapiHeaders)
      .getById(subjectSourceId)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Override
  public void deleteSubjectSourcesBySubjectSourceId(String subjectSourceId,
                                                    Map<String, String> okapiHeaders,
                                                    Handler<AsyncResult<Response>> asyncResultHandler,
                                                    Context vertxContext) {
    new SubjectSourceService(vertxContext, okapiHeaders)
      .delete(subjectSourceId)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Override
  public void putSubjectSourcesBySubjectSourceId(String subjectSourceId,
                                                 SubjectSource entity,
                                                 Map<String, String> okapiHeaders,
                                                 Handler<AsyncResult<Response>> asyncResultHandler,
                                                 Context vertxContext) {
    new SubjectSourceService(vertxContext, okapiHeaders)
      .update(subjectSourceId, entity)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }
}
