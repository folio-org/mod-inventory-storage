package org.folio.rest.impl;

import static org.folio.persist.ReindexJobRepository.INSTANCE_REINDEX_JOBS_QUERY;
import static org.folio.rest.persist.PgUtil.get;
import static org.folio.rest.persist.PgUtil.getById;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import java.util.Map;
import java.util.Objects;
import javax.ws.rs.core.Response;
import org.folio.persist.ReindexJobRepository;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.ReindexJob;
import org.folio.rest.jaxrs.model.ReindexJobs;
import org.folio.rest.jaxrs.resource.InstanceStorageReindex;
import org.folio.services.reindex.ReindexService;

public class ReindexInstanceApi implements InstanceStorageReindex {
  @Validate
  @Override
  public void postInstanceStorageReindex(Map<String, String> okapiHeaders,
                                         Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    new ReindexService(vertxContext, okapiHeaders).submitReindex(
        ReindexJob.ResourceName.INSTANCE)
      .onSuccess(response -> asyncResultHandler.handle(Future.succeededFuture(
        PostInstanceStorageReindexResponse.respond200WithApplicationJson(response))))
      .onFailure(error -> asyncResultHandler.handle(Future.succeededFuture(
        PostInstanceStorageReindexResponse.respond500WithTextPlain(error.getMessage()))));
  }

  @Validate
  @Override
  public void getInstanceStorageReindex(String query, int offset, int limit,
                                        String lang,
                                        Map<String, String> okapiHeaders,
                                        Handler<AsyncResult<Response>> asyncResultHandler,
                                        Context vertxContext) {

    var searchQuery = Objects.isNull(query) ? INSTANCE_REINDEX_JOBS_QUERY :
      INSTANCE_REINDEX_JOBS_QUERY + " and (" + query + ")";

    get(ReindexJobRepository.TABLE_NAME, ReindexJob.class, ReindexJobs.class,
      searchQuery, offset, limit, okapiHeaders, vertxContext,
      GetInstanceStorageReindexResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void getInstanceStorageReindexById(String id, Map<String, String> okapiHeaders,
                                            Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    getById(ReindexJobRepository.TABLE_NAME, ReindexJob.class, id, okapiHeaders,
      vertxContext, GetInstanceStorageReindexByIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteInstanceStorageReindexById(String id, Map<String, String> okapiHeaders,
                                               Handler<AsyncResult<Response>> asyncResultHandler,
                                               Context vertxContext) {

    new ReindexService(vertxContext, okapiHeaders).cancelReindex(id)
      .onSuccess(response -> asyncResultHandler.handle(Future.succeededFuture(
        DeleteInstanceStorageReindexByIdResponse.respond204())))
      .onFailure(error -> asyncResultHandler.handle(Future.succeededFuture(
        DeleteInstanceStorageReindexByIdResponse.respond500WithTextPlain(error.getMessage()))));
  }
}
