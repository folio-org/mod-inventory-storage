package org.folio.rest.impl;

import static org.folio.rest.persist.PgUtil.getById;
import static org.folio.services.migration.async.AsyncMigrationJobService.getAvailableMigrations;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.persist.AsyncMigrationJobRepository;
import org.folio.rest.jaxrs.model.AsyncMigrationJob;
import org.folio.rest.jaxrs.model.AsyncMigrationJobRequest;
import org.folio.rest.jaxrs.resource.InventoryStorageMigrations;
import org.folio.services.migration.async.AsyncMigrationJobService;

public class InventoryStorageMigrationsApi implements InventoryStorageMigrations {

  @Override
  public void getInventoryStorageMigrations(Map<String, String> okapiHeaders,
                                            Handler<AsyncResult<Response>> asyncResultHandler,
                                            Context vertxContext) {

    asyncResultHandler.handle(Future.succeededFuture(
      GetInventoryStorageMigrationsResponse.respond200WithApplicationJson(getAvailableMigrations())));
  }

  @Override
  public void getInventoryStorageMigrationsJobs(Map<String, String> okapiHeaders,
                                                Handler<AsyncResult<Response>> asyncResultHandler,
                                                Context vertxContext) {

    new AsyncMigrationJobService(vertxContext, okapiHeaders).getAllAsyncJobs()
      .onSuccess(response -> asyncResultHandler.handle(Future.succeededFuture(
        GetInventoryStorageMigrationsJobsResponse.respond200WithApplicationJson(response))))
      .onFailure(error -> asyncResultHandler.handle(Future.succeededFuture(
        GetInventoryStorageMigrationsJobsResponse.respond500WithTextPlain(error.getMessage()))));
  }

  @Override
  public void postInventoryStorageMigrationsJobs(AsyncMigrationJobRequest entity,
                                                 Map<String, String> okapiHeaders,
                                                 Handler<AsyncResult<Response>> asyncResultHandler,
                                                 Context vertxContext) {

    new AsyncMigrationJobService(vertxContext, okapiHeaders).submitAsyncMigration(entity)
      .onSuccess(response -> asyncResultHandler.handle(Future.succeededFuture(
        PostInventoryStorageMigrationsJobsResponse.respond200WithApplicationJson(response))))
      .onFailure(error -> asyncResultHandler.handle(Future.succeededFuture(
        PostInventoryStorageMigrationsJobsResponse.respond500WithTextPlain(error.getMessage()))));
  }

  @Override
  public void getInventoryStorageMigrationsJobsById(String id, Map<String, String> okapiHeaders,
                                                    Handler<AsyncResult<Response>> asyncResultHandler,
                                                    Context vertxContext) {

    getById(AsyncMigrationJobRepository.TABLE_NAME, AsyncMigrationJob.class, id, okapiHeaders,
      vertxContext, GetInventoryStorageMigrationsJobsByIdResponse.class, asyncResultHandler);
  }

  @Override
  public void deleteInventoryStorageMigrationsJobsById(String id, Map<String, String> okapiHeaders,
                                                       Handler<AsyncResult<Response>> asyncResultHandler,
                                                       Context vertxContext) {

    new AsyncMigrationJobService(vertxContext, okapiHeaders).cancelAsyncMigration(id)
      .onSuccess(response -> asyncResultHandler.handle(Future.succeededFuture(
        DeleteInventoryStorageMigrationsJobsByIdResponse.respond204())))
      .onFailure(error -> asyncResultHandler.handle(Future.succeededFuture(
        DeleteInventoryStorageMigrationsJobsByIdResponse.respond500WithTextPlain(error.getMessage()))));
  }
}
