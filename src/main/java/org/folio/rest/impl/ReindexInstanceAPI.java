package org.folio.rest.impl;

import static org.folio.rest.persist.PgUtil.getById;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.persist.ReindexJobRepository;
import org.folio.rest.jaxrs.model.ReindexJob;
import org.folio.rest.jaxrs.resource.InstanceStorageReindex;
import org.folio.services.reindex.ReindexService;

public class ReindexInstanceAPI implements InstanceStorageReindex {
  @Override
  public void postInstanceStorageReindex(Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    new ReindexService(vertxContext, okapiHeaders).submitReindex()
      .onSuccess(response -> asyncResultHandler.handle(Future.succeededFuture(
        PostInstanceStorageReindexResponse.respond200WithApplicationJson(response))))
      .onFailure(error -> asyncResultHandler.handle(Future.succeededFuture(
        PostInstanceStorageReindexResponse.respond500WithTextPlain(error.getMessage()))));
  }

  @Override
  public void getInstanceStorageReindexById(String id, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    getById(ReindexJobRepository.TABLE_NAME, ReindexJob.class, id, okapiHeaders,
      vertxContext, GetInstanceStorageReindexByIdResponse.class, asyncResultHandler);
  }

  @Override
  public void deleteInstanceStorageReindexById(String id, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    new ReindexService(vertxContext, okapiHeaders).cancelReindex(id)
      .onSuccess(response -> asyncResultHandler.handle(Future.succeededFuture(
        DeleteInstanceStorageReindexByIdResponse.respond204())))
      .onFailure(error -> asyncResultHandler.handle(Future.succeededFuture(
        DeleteInstanceStorageReindexByIdResponse.respond500WithTextPlain(error.getMessage()))));
  }
}
