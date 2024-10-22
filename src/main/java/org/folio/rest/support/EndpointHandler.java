package org.folio.rest.support;

import static io.vertx.core.Future.succeededFuture;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import javax.ws.rs.core.Response;
import org.folio.rest.jaxrs.model.Instances;
import org.folio.rest.jaxrs.model.InstancesWithoutPubPeriod;
import org.folio.rest.jaxrs.resource.InstanceStorage;
import org.folio.utils.ObjectConverterUtils;

public final class EndpointHandler {
  private EndpointHandler() { }

  /**
   * On success pass the result to asyncResultHandler. On failure use
   * {@link EndpointFailureHandler} to create an error Response for the Throwable
   * and pass it to asyncResultHandler.
   */
  public static Handler<AsyncResult<Response>> handle(Handler<AsyncResult<Response>> asyncResultHandler) {
    return result -> {
      if (result.succeeded()) {
        asyncResultHandler.handle(succeededFuture(result.result()));
      } else {
        EndpointFailureHandler.handleFailure(asyncResultHandler).handle(result.cause());
      }
    };
  }

  public static Handler<AsyncResult<Response>> handleInstances(Handler<AsyncResult<Response>> asyncResultHandler) {
    return result -> {
      if (result.succeeded() && result.result().getStatus() == 200) {
        Instances instances = (Instances) result.result().getEntity();
        var instancesWithoutPubPeriod = ObjectConverterUtils.convertObject(instances, InstancesWithoutPubPeriod.class);

        asyncResultHandler.handle(succeededFuture(
          InstanceStorage.GetInstanceStorageInstancesResponse.respond200WithApplicationJson(
            instancesWithoutPubPeriod)));
      } else {
        asyncResultHandler.handle(succeededFuture(
          InstanceStorage.GetInstanceStorageInstancesResponse.respond500WithTextPlain(result.cause())));
      }
    };
  }
}
