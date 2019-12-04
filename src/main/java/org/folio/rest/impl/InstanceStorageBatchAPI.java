package org.folio.rest.impl;

import com.google.common.collect.Lists;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.Instances;
import org.folio.rest.jaxrs.model.InstancesBatchResponse;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.jaxrs.resource.InstanceStorageBatchInstances;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.HridManager;
import org.folio.rest.tools.utils.JwtUtils;

import javax.ws.rs.core.Response;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.rest.RestVerticle.MODULE_SPECIFIC_ARGS;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TOKEN;
import static org.folio.rest.RestVerticle.OKAPI_USERID_HEADER;

public class InstanceStorageBatchAPI implements InstanceStorageBatchInstances {

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final String INSTANCE_TABLE = "instance";
  private static final String PARALLEL_DB_CONNECTIONS_LIMIT_KEY = "inventory.storage.parallel.db.connections.limit";
  private static final int PARALLEL_DB_CONNECTIONS_LIMIT = Integer.parseInt(MODULE_SPECIFIC_ARGS.getOrDefault(PARALLEL_DB_CONNECTIONS_LIMIT_KEY, "4"));

  @Override
  public void postInstanceStorageBatchInstances(Instances entity,
                                                Map<String, String> okapiHeaders,
                                                Handler<AsyncResult<Response>> asyncResultHandler,
                                                Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        PostgresClient postgresClient = PgUtil.postgresClient(vertxContext, okapiHeaders);
        populateMetaDataForList(entity.getInstances(), okapiHeaders);
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

    List<List<Instance>> batches = Lists.partition(instances, PARALLEL_DB_CONNECTIONS_LIMIT);
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

    final Future<String> hridFuture;
    if (isBlank(instance.getHrid())) {
      final HridManager hridManager = new HridManager(Vertx.currentContext(), postgresClient);
      hridFuture = hridManager.getNextInstanceHrid();
    } else {
      hridFuture = StorageHelper.completeFuture(instance.getHrid());
    }

    hridFuture.map(hrid -> {
      instance.setHrid(hrid);

      postgresClient.save(INSTANCE_TABLE, instance.getId(), instance, save -> {
        if (save.failed()) {
          log.error("Failed to create Instances", save.cause());
          future.fail(save.cause());
          return;
        }
        instance.setId(save.result());
        future.complete(instance);
      });
      return null;
    })
    .otherwise(error -> {
      log.error("Failed to generate an instance HRID", error);
      future.fail(error);
      return null;
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

  private void populateMetaDataForList(List<Instance> list, Map<String, String> okapiHeaders) {
    String userId = okapiHeaders.getOrDefault(OKAPI_USERID_HEADER, "");
    String token = okapiHeaders.getOrDefault(OKAPI_HEADER_TOKEN, "");
    if (userId == null && token != null) {
      userId = userIdFromToken(token);
    }
    Metadata md = new Metadata();
    md.setUpdatedDate(new Date());
    md.setCreatedDate(md.getUpdatedDate());
    md.setCreatedByUserId(userId);
    md.setUpdatedByUserId(userId);
    list.forEach(instance -> instance.setMetadata(md));
  }

  private static String userIdFromToken(String token) {
    try {
      String[] split = token.split("\\.");
      String json = JwtUtils.getJson(split[1]);
      JsonObject j = new JsonObject(json);
      return j.getString("user_id");
    } catch (Exception e) {
      log.warn("Invalid x-okapi-token: " + token, e);
      return null;
    }
  }


}
