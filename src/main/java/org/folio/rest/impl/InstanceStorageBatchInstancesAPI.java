package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.Instances;
import org.folio.rest.jaxrs.model.InstancesBatchResponse;
import org.folio.rest.jaxrs.resource.InstanceStorageBatchInstances;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.TenantTool;

import javax.ws.rs.core.Response;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class InstanceStorageBatchInstancesAPI implements InstanceStorageBatchInstances {

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final String TENANT_HEADER = "x-okapi-tenant";
  private static final String INSTANCE_TABLE = "instance";

  @Override
  public void postInstanceStorageBatchInstances(Instances entity,
                                                Map<String, String> okapiHeaders,
                                                Handler<AsyncResult<Response>> asyncResultHandler,
                                                Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        String tenantId = okapiHeaders.get(TENANT_HEADER);
        PostgresClient postgresClient =
          PostgresClient.getInstance(vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

        Map<Instance, Future<String>> savedInstances = entity.getInstances().stream()
          .map(instance -> Pair.of(instance, saveInstance(instance, postgresClient)))
          .collect(LinkedHashMap::new, (map, pair) -> map.put(pair.getKey(), pair.getValue()), Map::putAll);

        InstancesBatchResponse response = new InstancesBatchResponse();

        CompositeFuture.join(new ArrayList<>(savedInstances.values())).setHandler(ar -> {

            savedInstances.forEach((instance, future) -> {
              if (future.succeeded()) {
                response.getInstances().add(instance);
              } else {
                response.getErrorMessages().add(future.cause().getMessage());
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
          }
        );
      } catch (Exception e) {
        log.error("Failed to create Instances", e);
        asyncResultHandler.handle(Future.succeededFuture(
          PostInstanceStorageBatchInstancesResponse.respond500WithTextPlain(e.getMessage())));
      }
    });
  }

  private Future<String> saveInstance(Instance instance, PostgresClient postgresClient) {
    Future<String> future = Future.future();

    if (instance.getId() == null) {
      instance.setId(UUID.randomUUID().toString());
    }

    postgresClient.save(INSTANCE_TABLE, instance.getId(), instance, future.completer());

    return future;
  }

}
