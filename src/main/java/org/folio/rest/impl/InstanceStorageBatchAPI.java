package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.Instances;
import org.folio.rest.jaxrs.model.InstancesBatchResponse;
import org.folio.rest.jaxrs.resource.InstanceStorageBatchInstances;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;

import javax.ws.rs.core.Response;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class InstanceStorageBatchAPI implements InstanceStorageBatchInstances {

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final String INSTANCE_TABLE = "instance";

  @Override
  public void postInstanceStorageBatchInstances(Instances entity,
                                                Map<String, String> okapiHeaders,
                                                Handler<AsyncResult<Response>> asyncResultHandler,
                                                Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        PostgresClient postgresClient = PgUtil.postgresClient(vertxContext, okapiHeaders);

        List<Future> futures = entity.getInstances().stream()
          .map(instance -> saveInstance(instance, postgresClient))
          .collect(Collectors.toList());

        CompositeFuture.join(futures).setHandler(ar -> {

          InstancesBatchResponse response = new InstancesBatchResponse();

          futures.forEach(save -> {
            if (save.failed()) {
              response.getErrorMessages().add(save.cause().getMessage());
            } else {
              response.getInstances().add((Instance) save.result());
            }
          });

          response.setTotalRecords(response.getInstances().size());

          if (response.getErrorMessages().isEmpty()) {
            asyncResultHandler.handle(Future.succeededFuture(
              PostInstanceStorageBatchInstancesResponse.respond201WithApplicationJson(response)));
          } else {
            log.error("Failed to create some of the Instances: " + response.getErrorMessages());
            asyncResultHandler.handle(Future.succeededFuture(
              PostInstanceStorageBatchInstancesResponse.respond500WithApplicationJson(response)
            ));
          }
        });
      } catch (Exception e) {
        log.error("Failed to create Instances", e);
        asyncResultHandler.handle(Future.succeededFuture(
          PostInstanceStorageBatchInstancesResponse.respond500WithTextPlain(e.getMessage())));
      }
    });
  }

  private Future<Instance> saveInstance(Instance instance, PostgresClient postgresClient) {
    Future<Instance> future = Future.future();
    postgresClient.save(INSTANCE_TABLE, instance.getId(), instance, save -> {
      if (save.failed()) {
        log.error("Failed to create Instances", save.cause());
        future.fail(save.cause());
        return;
      }
      instance.setId(save.result());
      future.complete(instance);
    });
    return future;
  }

}
