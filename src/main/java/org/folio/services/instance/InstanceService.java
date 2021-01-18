package org.folio.services.instance;

import static io.vertx.core.CompositeFuture.all;
import static io.vertx.core.Future.succeededFuture;
import static io.vertx.core.Promise.promise;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.rest.impl.InstanceStorageAPI.INSTANCE_TABLE;
import static org.folio.rest.impl.StorageHelper.MAX_ENTITIES;
import static org.folio.rest.jaxrs.resource.InstanceStorageBatchSynchronous.PostInstanceStorageBatchSynchronousResponse;
import static org.folio.rest.persist.PgUtil.postSync;
import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.rest.support.StatusUpdatedDateGenerator.generateStatusUpdatedDate;

import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.persist.InstanceRepository;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.HridManager;
import org.folio.services.batch.BatchOperationContext;
import org.folio.services.domainevent.InstanceDomainEventPublisher;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;

public class InstanceService {
  private final HridManager hridManager;
  private final Context vertxContext;
  private final Map<String, String> okapiHeaders;
  private final InstanceDomainEventPublisher domainEventPublisher;
  private final InstanceRepository instanceRepository;

  public InstanceService(Context vertxContext, Map<String, String> okapiHeaders) {
    this.vertxContext = vertxContext;
    this.okapiHeaders = okapiHeaders;

    final PostgresClient postgresClient = postgresClient(vertxContext, okapiHeaders);
    hridManager = new HridManager(vertxContext, postgresClient);
    domainEventPublisher = new InstanceDomainEventPublisher(vertxContext, okapiHeaders);
    instanceRepository = new InstanceRepository(vertxContext, okapiHeaders);
  }

  public Future<Response> createInstances(List<Instance> instances, boolean upsert) {
    final String statusUpdatedDate = generateStatusUpdatedDate();

    @SuppressWarnings("rawtypes")
    final List<Future> setHridFutures = instances.stream()
      .map(instance -> {
        instance.setStatusUpdatedDate(statusUpdatedDate);
        return getHrid(instance).map(instance::withHrid);
      }).collect(toList());

    return all(setHridFutures)
      .compose(notUsed -> buildBatchOperationContext(upsert, instances))
      .compose(batchOperation -> {
        final Promise<Response> postResult = promise();

        // Can use instances list here directly because the class is stateful
        postSync(INSTANCE_TABLE, instances, MAX_ENTITIES, upsert, okapiHeaders,
          vertxContext, PostInstanceStorageBatchSynchronousResponse.class, postResult);

        return postResult.future()
          .compose(domainEventPublisher.publishInstancesCreatedOrUpdated(batchOperation));
      });
  }

  private Future<BatchOperationContext<Instance>> buildBatchOperationContext(
    boolean upsert, List<Instance> allInstances) {

    if (!upsert) {
      return succeededFuture(new BatchOperationContext<>(allInstances, emptyList(), emptyList()));
    }

    return instanceRepository.getById(allInstances, Instance::getId)
      .map(foundInstances -> {
        final var instancesToBeCreated = allInstances.stream()
          .filter(instance -> !foundInstances.containsKey(instance.getId()))
          .collect(toList());

        // new representations for existing instances
        final var instancesToBeUpdated = allInstances.stream()
          .filter(instance -> foundInstances.containsKey(instance.getId()))
          .collect(toList());

        // old (existing) instance representations before applying update
        final var existingRecordsBeforeUpdate = instancesToBeUpdated.stream()
          .map(instances -> foundInstances.get(instances.getId()))
          .collect(toList());

        return new BatchOperationContext<>(instancesToBeCreated, instancesToBeUpdated,
          existingRecordsBeforeUpdate);
      });
  }

  private Future<String> getHrid(Instance instance) {
    return isBlank(instance.getHrid())
      ? hridManager.getNextInstanceHrid() : succeededFuture(instance.getHrid());
  }
}
