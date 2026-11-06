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
import org.folio.rest.jaxrs.model.SubjectSource;
import org.folio.rest.jaxrs.resource.SubjectSources;
import org.folio.services.subjectsource.SubjectSourceService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings("java:S6813")
public class SubjectSourceApi implements SubjectSources {

  @Autowired
  private SubjectSourceService service;

  public SubjectSourceApi() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Override
  public void getSubjectSources(String query, String totalRecords, int offset,
                                int limit,
                                Map<String, String> headers,
                                Handler<AsyncResult<Response>> asyncResultHandler,
                                Context vertxContext) {
    service.getByQuery(query, offset, limit, totalRecords, headers)
      .map(GetSubjectSourcesResponse::respond200WithApplicationJson)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Override
  public void postSubjectSources(SubjectSource entity,
                                 Map<String, String> headers,
                                 Handler<AsyncResult<Response>> asyncResultHandler,
                                 Context vertxContext) {
    service.create(entity, headers)
      .map(created -> PostSubjectSourcesResponse.respond201WithApplicationJson(created,
        PostSubjectSourcesResponse.headersFor201().withLocation(created.getId())))
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Override
  public void getSubjectSourcesBySubjectSourceId(String subjectSourceId,
                                                 Map<String, String> headers,
                                                 Handler<AsyncResult<Response>> asyncResultHandler,
                                                 Context vertxContext) {
    service.getById(subjectSourceId, headers)
      .map(GetSubjectSourcesBySubjectSourceIdResponse::respond200WithApplicationJson)
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler, errors ->
        GetSubjectSourcesBySubjectSourceIdResponse.respond404WithTextPlain(firstErrorMessage(errors))));
  }

  @Override
  public void deleteSubjectSourcesBySubjectSourceId(String subjectSourceId,
                                                    Map<String, String> headers,
                                                    Handler<AsyncResult<Response>> asyncResultHandler,
                                                    Context vertxContext) {
    service.delete(subjectSourceId, headers)
      .map(notUsed -> DeleteSubjectSourcesBySubjectSourceIdResponse.respond204())
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler, errors ->
        DeleteSubjectSourcesBySubjectSourceIdResponse.respond400WithTextPlain(firstErrorMessage(errors))));
  }

  @Override
  public void putSubjectSourcesBySubjectSourceId(String subjectSourceId,
                                                 SubjectSource entity,
                                                 Map<String, String> headers,
                                                 Handler<AsyncResult<Response>> asyncResultHandler,
                                                 Context vertxContext) {
    service.update(subjectSourceId, entity, headers)
      .map(notUsed -> PutSubjectSourcesBySubjectSourceIdResponse.respond204())
      .onSuccess(response -> asyncResultHandler.handle(succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }
}
