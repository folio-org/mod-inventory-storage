package org.folio.rest.impl;

import static org.folio.rest.persist.PgUtil.get;
import static org.folio.rest.persist.PgUtil.getById;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.persist.ReindexJobRepository;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.ReindexJob;
import org.folio.rest.jaxrs.model.ReindexJobs;
import org.folio.rest.jaxrs.resource.AuthorityStorageReindex;
import org.folio.services.reindex.ReindexResourceName;
import org.folio.services.reindex.ReindexService;

public class ReindexAuthoritiesApi implements AuthorityStorageReindex {

  @Validate
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

  @Validate
  @Override
  public void getAuthorityStorageReindex(String query, int offset, int limit,
                                         Map<String, String> okapiHeaders,
                                         Handler<AsyncResult<Response>> asyncResultHandler,
                                         Context vertxContext) {

    get(ReindexJobRepository.TABLE_NAME, ReindexJob.class, ReindexJobs.class,
      query, offset, limit, okapiHeaders, vertxContext,
      GetAuthorityStorageReindexResponse.class, asyncResultHandler);

  }

  @Validate
  @Override
  public void getAuthorityStorageReindexById(String id, Map<String, String> okapiHeaders,
                                             Handler<AsyncResult<Response>> asyncResultHandler,
                                             Context vertxContext) {

    getById(ReindexJobRepository.TABLE_NAME, ReindexJob.class, id, okapiHeaders,
      vertxContext, GetAuthorityStorageReindexByIdResponse.class, asyncResultHandler);
  }

  @Validate
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
