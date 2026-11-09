package org.folio.rest.impl;

import static org.folio.rest.support.ResponseHandlerUtil.firstErrorMessage;
import static org.folio.rest.support.ResponseHandlerUtil.handleFailure;
import static org.folio.services.migration.async.AsyncMigrationJobService.getAvailableMigrations;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.jaxrs.model.AsyncMigrationJobRequest;
import org.folio.rest.jaxrs.resource.InventoryStorageMigrations;
import org.folio.services.migration.async.AsyncMigrationJobService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings("java:S6813")
public class InventoryStorageMigrationsApi implements InventoryStorageMigrations {

  @Autowired
  private AsyncMigrationJobService migrationJobService;

  public InventoryStorageMigrationsApi() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Override
  public void getInventoryStorageMigrations(Map<String, String> headers,
                                            Handler<AsyncResult<Response>> asyncResultHandler,
                                            Context vertxContext) {

    asyncResultHandler.handle(Future.succeededFuture(
      GetInventoryStorageMigrationsResponse.respond200WithApplicationJson(getAvailableMigrations())));
  }

  @Override
  public void getInventoryStorageMigrationsJobs(Map<String, String> headers,
                                                Handler<AsyncResult<Response>> asyncResultHandler,
                                                Context vertxContext) {

    migrationJobService.getAllAsyncJobs(headers)
      .onSuccess(response -> asyncResultHandler.handle(Future.succeededFuture(
        GetInventoryStorageMigrationsJobsResponse.respond200WithApplicationJson(response))))
      .onFailure(error -> asyncResultHandler.handle(Future.succeededFuture(
        GetInventoryStorageMigrationsJobsResponse.respond500WithTextPlain(error.getMessage()))));
  }

  @Override
  public void postInventoryStorageMigrationsJobs(AsyncMigrationJobRequest entity,
                                                 Map<String, String> headers,
                                                 Handler<AsyncResult<Response>> asyncResultHandler,
                                                 Context vertxContext) {

    migrationJobService.submitAsyncMigration(entity, headers)
      .onSuccess(response -> asyncResultHandler.handle(Future.succeededFuture(
        PostInventoryStorageMigrationsJobsResponse.respond200WithApplicationJson(response))))
      .onFailure(error -> asyncResultHandler.handle(Future.succeededFuture(
        PostInventoryStorageMigrationsJobsResponse.respond500WithTextPlain(error.getMessage()))));
  }

  @Override
  public void getInventoryStorageMigrationsJobsById(String id, Map<String, String> headers,
                                                    Handler<AsyncResult<Response>> asyncResultHandler,
                                                    Context vertxContext) {

    migrationJobService.getAsyncJob(id, headers)
      .map(GetInventoryStorageMigrationsJobsByIdResponse::respond200WithApplicationJson)
      .onSuccess(response -> asyncResultHandler.handle(Future.succeededFuture(response)))
      .onFailure(handleFailure(asyncResultHandler, errors ->
        GetInventoryStorageMigrationsJobsByIdResponse.respond404WithTextPlain(firstErrorMessage(errors))));
  }

  @Override
  public void deleteInventoryStorageMigrationsJobsById(String id, Map<String, String> headers,
                                                       Handler<AsyncResult<Response>> asyncResultHandler,
                                                       Context vertxContext) {

    migrationJobService.cancelAsyncMigration(id, headers)
      .onSuccess(response -> asyncResultHandler.handle(Future.succeededFuture(
        DeleteInventoryStorageMigrationsJobsByIdResponse.respond204())))
      .onFailure(error -> asyncResultHandler.handle(Future.succeededFuture(
        DeleteInventoryStorageMigrationsJobsByIdResponse.respond500WithTextPlain(error.getMessage()))));
  }
}
