package org.folio.rest.impl;

import com.google.common.collect.Lists;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static org.folio.rest.RestVerticle.MODULE_SPECIFIC_ARGS;

public class InstanceStorageBatchAPI implements InstanceStorageBatchInstances {

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final String INSTANCE_TABLE = "instance";
  private static final String BATCH_SIZE_KEY = "inventory.storage.max.batch.size";
  private static final int MAX_BATCH_SIZE = Integer.parseInt(MODULE_SPECIFIC_ARGS.getOrDefault(BATCH_SIZE_KEY, "50"));

  @Override
  public void postInstanceStorageBatchInstances(Instances entity,
                                                Map<String, String> okapiHeaders,
                                                Handler<AsyncResult<Response>> asyncResultHandler,
                                                Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        PostgresClient postgresClient = PgUtil.postgresClient(vertxContext, okapiHeaders);

        executeInBatch(entity.getInstances(),
          (instances, saveFutures) -> saveInstances(instances, postgresClient, saveFutures))
          .setHandler(ar -> {

            InstancesBatchResponse response = constructResponse(ar.result());

            if (!response.getInstances().isEmpty()) {
              // return 201 response - at least one Instance was successfully created
              asyncResultHandler.handle(Future.succeededFuture(
                PostInstanceStorageBatchInstancesResponse.respond201WithApplicationJson(response)));
            } else {
              // return 500 response with the list of errors - not one Instance was created
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

  /**
   * Performs specified action on collection of instances broken into max size batches,
   * allows to limit the number of connections to the db being executed in parallel
   *
   * @param instances list of Instances
   * @param action    action to be performed on Instances
   * @return future containing the list of completed individual result futures
   */
  private Future<List<Future>> executeInBatch(List<Instance> instances,
                                              BiFunction<List<Instance>, List<Future>, Future<List<Future>>> action) {
    Future<List<Future>> future = Future.succeededFuture(new ArrayList<>());

    List<List<Instance>> batches = Lists.partition(instances, MAX_BATCH_SIZE);
    for (List<Instance> batch : batches) {
      future = future.compose(futures -> action.apply(batch, futures));
    }
    return future;
  }

  /**
   * Saves collection of Instances into the db
   *
   * @param instances      list of Instances to save
   * @param postgresClient Postgres Client
   * @param saveFutures    list of all individual result futures
   * @return future containing the list of completed individual result futures
   */
  private Future<List<Future>> saveInstances(List<Instance> instances, PostgresClient postgresClient,
                                             List<Future> saveFutures) {
    Future<List<Future>> future = Future.future();

    List<Future> futures = instances.stream()
      .map(instance -> saveInstance(instance, postgresClient))
      .collect(Collectors.toList());

    CompositeFuture.join(futures).setHandler(ar -> {
        saveFutures.addAll(futures);
        future.complete(saveFutures);
      }
    );
    return future;
  }

  /**
   * Saves Instance into the db
   *
   * @param instance       Instance to save
   * @param postgresClient Postgres Client
   * @return future containing saved Instance or error
   */
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

  /**
   * Iterates through list of completed result futures and constructs InstancesBatchResponse
   *
   * @param saveFutures list of completed individual result futures
   * @return InstancesBatchResponse
   */
  private InstancesBatchResponse constructResponse(List<Future> saveFutures) {
    InstancesBatchResponse response = new InstancesBatchResponse();

    saveFutures.forEach(save -> {
      if (save.failed()) {
        response.getErrorMessages().add(save.cause().getMessage());
      } else {
        response.getInstances().add((Instance) save.result());
      }
    });

    response.setTotalRecords(response.getInstances().size());
    return response;
  }

}
