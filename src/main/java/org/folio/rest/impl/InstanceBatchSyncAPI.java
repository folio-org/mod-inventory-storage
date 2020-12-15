package org.folio.rest.impl;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.rest.support.StatusUpdatedDateGenerator.generateStatusUpdatedDate;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.InstancesPost;
import org.folio.rest.jaxrs.resource.InstanceStorageBatchSynchronous;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.HridManager;
import org.folio.rest.tools.utils.TenantTool;

import javax.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class InstanceBatchSyncAPI implements InstanceStorageBatchSynchronous {
  @Validate
  @Override
  public void postInstanceStorageBatchSynchronous(boolean upsert, InstancesPost entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    final List<Instance> instances = entity.getInstances();
    final PostgresClient postgresClient = PostgresClient.getInstance(
          vertxContext.owner(), TenantTool.tenantId(okapiHeaders));
    // Currently, there is no method on CompositeFuture to accept List<Future<String>>
    @SuppressWarnings("rawtypes")
    final List<Future> futures = new ArrayList<>();
    final HridManager hridManager = new HridManager(Vertx.currentContext(), postgresClient);

    final String statusUpdatedDate = generateStatusUpdatedDate();
    for (Instance instance : instances) {
      futures.add(setHrid(instance, hridManager));
      instance.setStatusUpdatedDate(statusUpdatedDate);
    }

    CompositeFuture.all(futures)
    .onSuccess(ar -> {
      PgUtil.postSync(InstanceStorageAPI.INSTANCE_TABLE, entity.getInstances(),
          StorageHelper.MAX_ENTITIES, upsert, okapiHeaders, vertxContext,
          InstanceStorageBatchSynchronous.PostInstanceStorageBatchSynchronousResponse.class,
          asyncResultHandler);
    })
    .onFailure(cause -> {
      asyncResultHandler.handle(
          Future.succeededFuture(PostInstanceStorageBatchSynchronousResponse
              .respond500WithTextPlain(cause.getMessage())));
    });
  }

  private Future<Void> setHrid(Instance instance, HridManager hridManager) {
    final Future<String> hridFuture;

    if (isBlank(instance.getHrid())) {
      hridFuture = hridManager.getNextInstanceHrid();
    } else {
      hridFuture = Future.succeededFuture(instance.getHrid());
    }

    return hridFuture.map(hrid -> {
      instance.setHrid(hrid);
      return null;
    });
  }
}
