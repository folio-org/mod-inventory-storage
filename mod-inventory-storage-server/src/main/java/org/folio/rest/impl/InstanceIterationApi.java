package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.IterationJob;
import org.folio.rest.jaxrs.model.IterationJobParams;
import org.folio.rest.jaxrs.resource.InstanceStorageInstancesIteration;
import org.folio.services.iteration.IterationService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings("java:S6813")
public class InstanceIterationApi implements InstanceStorageInstancesIteration {

  @Autowired
  private IterationService iterationService;

  public InstanceIterationApi() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Validate
  @Override
  public void postInstanceStorageInstancesIteration(IterationJobParams jobParams, Map<String, String> headers,
                                                    Handler<AsyncResult<Response>> resultHandler,
                                                    Context vertxContext) {

    iterationService.submitIteration(jobParams, headers)
      .onSuccess(posted(resultHandler))
      .onFailure(postFailed(resultHandler));
  }

  @Validate
  @Override
  public void getInstanceStorageInstancesIterationById(String id, Map<String, String> headers,
                                                       Handler<AsyncResult<Response>> resultHandler,
                                                       Context vertxContext) {

    iterationService.getIteration(id, headers)
      .onSuccess(getOk(resultHandler))
      .onFailure(getFailed(resultHandler));
  }

  @Validate
  @Override
  public void deleteInstanceStorageInstancesIterationById(String id, Map<String, String> headers,
                                                          Handler<AsyncResult<Response>> resultHandler,
                                                          Context vertxContext) {

    iterationService.cancelIteration(id, headers)
      .onSuccess(deleted(resultHandler))
      .onFailure(deleteFailed(resultHandler));
  }

  private static Handler<IterationJob> posted(Handler<AsyncResult<Response>> resultHandler) {
    return response -> resultHandler.handle(succeededFuture(
      PostInstanceStorageInstancesIterationResponse.respond201WithApplicationJson(response)));
  }

  private static Handler<Throwable> postFailed(Handler<AsyncResult<Response>> resultHandler) {
    return error -> resultHandler.handle(succeededFuture(
      PostInstanceStorageInstancesIterationResponse.respond500WithTextPlain(error.getMessage())));
  }

  private static Handler<Optional<IterationJob>> getOk(Handler<AsyncResult<Response>> resultHandler) {
    return result -> result.ifPresentOrElse(
      iterationJob -> resultHandler.handle(succeededFuture(
        GetInstanceStorageInstancesIterationByIdResponse.respond200WithApplicationJson(iterationJob))),
      () -> resultHandler.handle(succeededFuture(
        GetInstanceStorageInstancesIterationByIdResponse.respond404WithTextPlain(NOT_FOUND.getReasonPhrase())))
    );
  }

  private static Handler<Throwable> getFailed(Handler<AsyncResult<Response>> resultHandler) {
    return error -> resultHandler.handle(succeededFuture(
      GetInstanceStorageInstancesIterationByIdResponse.respond500WithTextPlain(error.getMessage())));
  }

  private static Handler<Void> deleted(Handler<AsyncResult<Response>> resultHandler) {
    return response -> resultHandler.handle(succeededFuture(
      DeleteInstanceStorageInstancesIterationByIdResponse.respond204()));
  }

  private static Handler<Throwable> deleteFailed(Handler<AsyncResult<Response>> resultHandler) {
    return error -> resultHandler.handle(succeededFuture(
      DeleteInstanceStorageInstancesIterationByIdResponse.respond500WithTextPlain(error.getMessage())));
  }
}
