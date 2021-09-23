package org.folio.rest.impl;

import static org.folio.rest.persist.PgUtil.getById;

import javax.ws.rs.core.Response;
import java.util.Map;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.folio.persist.IterationJobRepository;
import org.folio.rest.jaxrs.model.IterationJob;
import org.folio.rest.jaxrs.model.IterationJobParams;
import org.folio.rest.jaxrs.resource.InstanceStorageInstancesIteration;
import org.folio.services.iteration.IterationService;

public class InstanceIterationAPI implements InstanceStorageInstancesIteration {

  @Override
  public void postInstanceStorageInstancesIteration(IterationJobParams jobParams, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    new IterationService(vertxContext, okapiHeaders).submitIteration(jobParams)
      .onSuccess(response -> asyncResultHandler.handle(Future.succeededFuture(
          PostInstanceStorageInstancesIterationResponse.respond201WithApplicationJson(response))))
      .onFailure(error -> asyncResultHandler.handle(Future.succeededFuture(
          PostInstanceStorageInstancesIterationResponse.respond500WithTextPlain(error.getMessage()))));
  }

  @Override
  public void getInstanceStorageInstancesIterationById(String id, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    getById(IterationJobRepository.TABLE_NAME, IterationJob.class, id, okapiHeaders,
        vertxContext, GetInstanceStorageInstancesIterationByIdResponse.class, asyncResultHandler);
  }

  @Override
  public void deleteInstanceStorageInstancesIterationById(String id, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    new IterationService(vertxContext, okapiHeaders).cancelIteration(id)
      .onSuccess(response -> asyncResultHandler.handle(Future.succeededFuture(
          DeleteInstanceStorageInstancesIterationByIdResponse.respond204())))
      .onFailure(error -> asyncResultHandler.handle(Future.succeededFuture(
          DeleteInstanceStorageInstancesIterationByIdResponse.respond500WithTextPlain(error.getMessage()))));
  }

}
