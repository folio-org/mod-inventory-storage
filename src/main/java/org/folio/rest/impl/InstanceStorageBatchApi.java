package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.rest.RestVerticle.MODULE_SPECIFIC_ARGS;
import static org.folio.rest.jaxrs.resource.InstanceStorageBatchInstances.PostInstanceStorageBatchInstancesResponse.respond201WithApplicationJson;
import static org.folio.rest.jaxrs.resource.InstanceStorageBatchInstances.PostInstanceStorageBatchInstancesResponse.respond500WithApplicationJson;
import static org.folio.rest.jaxrs.resource.InstanceStorageBatchInstances.PostInstanceStorageBatchInstancesResponse.respond500WithTextPlain;
import static org.folio.rest.support.StatusUpdatedDateGenerator.generateStatusUpdatedDate;

import com.google.common.collect.Lists;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.Instances;
import org.folio.rest.jaxrs.model.InstancesBatchResponse;
import org.folio.rest.jaxrs.resource.InstanceStorageBatchInstances;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.HridManager;
import org.folio.rest.tools.utils.MetadataUtil;
import org.folio.services.domainevent.InstanceDomainEventPublisher;

@SuppressWarnings("rawtypes")
public class InstanceStorageBatchApi implements InstanceStorageBatchInstances {

  private static final Logger log = LogManager.getLogger();

  private static final String INSTANCE_TABLE = "instance";
  private static final String PARALLEL_DB_CONNECTIONS_LIMIT_KEY =
    "inventory.storage.parallel.db.connections.limit";
  private static final int PARALLEL_DB_CONNECTIONS_LIMIT = Integer.parseInt(
    MODULE_SPECIFIC_ARGS.getOrDefault(PARALLEL_DB_CONNECTIONS_LIMIT_KEY, "4"));

  @Validate
  @Override
  public void postInstanceStorageBatchInstances(Instances entity,
                                                Map<String, String> okapiHeaders,
                                                Handler<AsyncResult<Response>> asyncResultHandler,
                                                Context vertxContext) {

    final String statusUpdatedDate = generateStatusUpdatedDate();
    for (Instance instance : entity.getInstances()) {
      instance.setStatusUpdatedDate(statusUpdatedDate);
    }

    vertxContext.runOnContext(v -> {
      try {
        PostgresClient postgresClient = PgUtil.postgresClient(vertxContext, okapiHeaders);
        final InstanceDomainEventPublisher instanceDomainEventPublisher =
          new InstanceDomainEventPublisher(vertxContext, okapiHeaders);

        MetadataUtil.populateMetadata(entity.getInstances(), okapiHeaders);
        executeInBatch(entity.getInstances(),
          instances -> saveInstances(instances, postgresClient))
          .onComplete(ar -> {

            InstancesBatchResponse response = constructResponse(ar.result());

            if (!response.getInstances().isEmpty()) {
              instanceDomainEventPublisher.publishInstancesCreated(response.getInstances())
                .onSuccess(notUsed -> asyncResultHandler.handle(
                  succeededFuture(respond201WithApplicationJson(response))))
                .onFailure(error -> {
                  log.error("Failed to send events for instances", error);

                  asyncResultHandler.handle(succeededFuture(
                    respond500WithTextPlain(error.getMessage())));
                });
            } else {
              // return 500 response with the list of errors - not one Instance was created
              log.error("Failed to create some of the Instances: {}", response.getErrorMessages());
              asyncResultHandler.handle(succeededFuture(
                respond500WithApplicationJson(response)));
            }
          });
      } catch (Exception e) {
        log.error("Failed to create Instances", e);
        asyncResultHandler.handle(succeededFuture(respond500WithTextPlain(e.getMessage())));
      }
    });
  }

  /**
   * Performs specified action on collection of instances broken into max size batches,
   * allows to limit the number of connections to the db being executed in parallel.
   *
   * @param instances list of Instances
   * @param action    action to be performed on Instances
   * @return succeeded future containing the list of completed (failed and succeeded)
   *   individual result futures, one per instance
   */
  private Future<List<Future<Instance>>> executeInBatch(List<Instance> instances,
                                              Function<List<Instance>, Future<List<Future<Instance>>>> action) {
    List<Future<Instance>> totalFutures = new ArrayList<>();

    List<List<Instance>> batches = Lists.partition(instances, PARALLEL_DB_CONNECTIONS_LIMIT);
    Future<List<Future<Instance>>> future = succeededFuture();
    for (List<Instance> batch : batches) {
      future = future.compose(x -> action.apply(batch))
        .onSuccess(totalFutures::addAll);
    }
    return future.map(totalFutures);
  }

  /**
   * Saves collection of Instances into the db.
   *
   * @param instances      list of Instances to save
   * @param postgresClient Postgres Client
   * @return succeeded future containing the list of completed (succeeded and failed) individual result futures
   */
  private Future<List<Future<Instance>>> saveInstances(List<Instance> instances, PostgresClient postgresClient) {
    List<Future<Instance>> futures = instances.stream()
      .map(instance -> saveInstance(instance, postgresClient))
      .toList();

    return Future.join(futures)
      // on success and on failure return succeeding future with list of all (succeeded and failed) futures
      .map(futures)
      .otherwise(futures);
  }

  /**
   * Saves Instance into the db.
   *
   * @param instance       Instance to save
   * @param postgresClient Postgres Client
   * @return future containing saved Instance or error
   */
  private Future<Instance> saveInstance(Instance instance, PostgresClient postgresClient) {
    final Future<String> hridFuture;

    if (isBlank(instance.getHrid())) {
      final HridManager hridManager = new HridManager(postgresClient);
      hridFuture = hridManager.getNextInstanceHrid();
    } else {
      hridFuture = succeededFuture(instance.getHrid());
    }

    return hridFuture.compose(hrid -> {
      instance.setHrid(hrid);
      return Future.<String>future(promise -> postgresClient.save(INSTANCE_TABLE, instance.getId(), instance, promise));
    }).map(id -> {
      instance.setId(id);
      return instance;
    }).onFailure(error -> log.error("Failed to generate an instance HRID", error));
  }

  /**
   * Iterates through list of completed result futures and constructs InstancesBatchResponse.
   *
   * @param saveFutures list of completed individual result futures
   * @return InstancesBatchResponse
   */
  private InstancesBatchResponse constructResponse(List<Future<Instance>> saveFutures) {
    InstancesBatchResponse response = new InstancesBatchResponse();

    saveFutures.forEach(save -> {
      if (save.failed()) {
        response.getErrorMessages().add(save.cause().getMessage());
      } else {
        response.getInstances().add(save.result());
      }
    });

    response.setTotalRecords(response.getInstances().size());
    return response;
  }

}
