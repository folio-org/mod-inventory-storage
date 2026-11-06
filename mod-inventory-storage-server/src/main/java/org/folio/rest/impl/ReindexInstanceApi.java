package org.folio.rest.impl;

import static org.folio.persist.ReindexJobRepository.INSTANCE_REINDEX_JOBS_QUERY;
import static org.folio.rest.support.ResponseHandlerUtil.firstErrorMessage;
import static org.folio.rest.support.ResponseHandlerUtil.handleFailure;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import java.util.Map;
import java.util.Objects;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.ReindexJob;
import org.folio.rest.jaxrs.resource.InstanceStorageReindex;
import org.folio.services.reindex.ReindexService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings("java:S6813")
public class ReindexInstanceApi implements InstanceStorageReindex {

  @Autowired
  private ReindexService reindexService;

  public ReindexInstanceApi() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Validate
  @Override
  public void postInstanceStorageReindex(Map<String, String> headers,
                                         Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    reindexService.submitReindex(ReindexJob.ResourceName.INSTANCE, headers)
      .onSuccess(response -> asyncResultHandler.handle(Future.succeededFuture(
        PostInstanceStorageReindexResponse.respond200WithApplicationJson(response))))
      .onFailure(error -> asyncResultHandler.handle(Future.succeededFuture(
        PostInstanceStorageReindexResponse.respond500WithTextPlain(error.getMessage()))));
  }

  @Validate
  @Override
  public void getInstanceStorageReindex(String query, String totalRecords, int offset, int limit,

                                        Map<String, String> headers,
                                        Handler<AsyncResult<Response>> asyncResultHandler,
                                        Context vertxContext) {

    var searchQuery = Objects.isNull(query) ? INSTANCE_REINDEX_JOBS_QUERY :
                      INSTANCE_REINDEX_JOBS_QUERY + " and (" + query + ")";

    reindexService.getJobs(searchQuery, offset, limit, totalRecords, headers)
      .map(GetInstanceStorageReindexResponse::respond200WithApplicationJson)
      .onSuccess(response -> asyncResultHandler.handle(Future.succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler));
  }

  @Validate
  @Override
  public void getInstanceStorageReindexById(String id, Map<String, String> headers,
                                            Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    reindexService.getJob(id, headers)
      .map(GetInstanceStorageReindexByIdResponse::respond200WithApplicationJson)
      .onSuccess(response -> asyncResultHandler.handle(Future.succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler, errors ->
        GetInstanceStorageReindexByIdResponse.respond404WithTextPlain(firstErrorMessage(errors))));
  }

  @Validate
  @Override
  public void deleteInstanceStorageReindexById(String id, Map<String, String> headers,
                                               Handler<AsyncResult<Response>> asyncResultHandler,
                                               Context vertxContext) {

    reindexService.cancelReindex(id, headers)
      .onSuccess(response -> asyncResultHandler.handle(Future.succeededFuture(
        DeleteInstanceStorageReindexByIdResponse.respond204())))
      .onFailure(error -> asyncResultHandler.handle(Future.succeededFuture(
        DeleteInstanceStorageReindexByIdResponse.respond500WithTextPlain(error.getMessage()))));
  }
}
