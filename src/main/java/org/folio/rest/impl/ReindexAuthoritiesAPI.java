package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.folio.persist.ReindexJobRepository;
import org.folio.rest.jaxrs.model.ReindexJob;
import org.folio.rest.jaxrs.resource.AuthorityStorageReindex;
import org.folio.services.reindex.ReindexResourceName;
import org.folio.services.reindex.ReindexService;

import javax.ws.rs.core.Response;
import java.util.Map;

import static org.folio.rest.persist.PgUtil.getById;

public class ReindexAuthoritiesAPI implements AuthorityStorageReindex {

  @Override
  public void postAuthorityStorageReindex(Map<String, String> okapiHeaders,
                                          Handler<AsyncResult<Response>> asyncResultHandler,
                                          Context vertxContext) {

    new ReindexService(vertxContext, okapiHeaders).submitReindex(ReindexResourceName.AUTHORITY)
      .onSuccess(response -> asyncResultHandler.handle(Future.succeededFuture(
        PostAuthorityStorageReindexResponse.respond200WithApplicationJson(response))))
      .onFailure(error -> asyncResultHandler.handle(Future.succeededFuture(
        PostAuthorityStorageReindexResponse.respond500WithTextPlain(error.getMessage()))));
  }

  @Override
  public void getAuthorityStorageReindexById(String id, Map<String, String> okapiHeaders,
                                             Handler<AsyncResult<Response>> asyncResultHandler,
                                             Context vertxContext) {

    getById(ReindexJobRepository.TABLE_NAME, ReindexJob.class, id, okapiHeaders,
      vertxContext, GetAuthorityStorageReindexByIdResponse.class, asyncResultHandler);
  }

  @Override
  public void deleteAuthorityStorageReindexById(String id, Map<String, String> okapiHeaders,
                                                Handler<AsyncResult<Response>> asyncResultHandler,
                                                Context vertxContext) {

    new ReindexService(vertxContext, okapiHeaders).cancelReindex(id)
      .onSuccess(response -> asyncResultHandler.handle(Future.succeededFuture(
        DeleteAuthorityStorageReindexByIdResponse.respond204())))
      .onFailure(error -> asyncResultHandler.handle(Future.succeededFuture(
        DeleteAuthorityStorageReindexByIdResponse.respond500WithTextPlain(error.getMessage()))));
  }
}
