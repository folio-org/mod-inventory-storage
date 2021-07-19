package org.folio.services.domainevent;

import static io.vertx.core.Future.succeededFuture;
import static org.apache.logging.log4j.LogManager.getLogger;
import static org.folio.Environment.environmentName;
import static org.folio.rest.support.ResponseUtil.isCreateSuccessResponse;
import static org.folio.rest.tools.utils.TenantTool.tenantId;
import static org.folio.services.domainevent.DomainEvent.reindexEvent;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.Logger;
import org.folio.persist.InstanceRepository;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.services.batch.BatchOperationContext;
import org.folio.services.kafka.topic.KafkaTopic;

import io.vertx.core.Context;
import io.vertx.core.Future;

public class InstanceDomainEventPublisher {
  private static final Logger log = getLogger(InstanceDomainEventPublisher.class);
  public static final String REINDEX_JOB_ID_HEADER = "reindex-job-id";

  private final InstanceRepository instanceRepository;
  private final CommonDomainEventPublisher<Instance> domainEventService;

  public InstanceDomainEventPublisher(Context context, Map<String, String> okapiHeaders) {
    instanceRepository = new InstanceRepository(context, okapiHeaders);
    domainEventService = new CommonDomainEventPublisher<>(context, okapiHeaders,
      KafkaTopic.instance(environmentName(), tenantId(okapiHeaders)));
  }

  public Future<Void> publishInstanceUpdated(Instance oldRecord, Instance newRecord) {
    return domainEventService.publishRecordUpdated(oldRecord.getId(), oldRecord, newRecord);
  }

  public Future<Void> publishInstanceCreated(Instance newInstance) {
    return domainEventService.publishRecordCreated(newInstance.getId(), newInstance);
  }

  public Future<Void> publishInstancesCreated(Collection<Instance> instances) {
    if (instances.isEmpty()) {
      log.info("No instances were created, skipping event sending");
      return succeededFuture();
    }

    log.info("[{}] instances were created, sending events for them", instances.size());

    final var createdInstancesPairs = instances.stream()
      .map(instance -> new ImmutablePair<>(instance.getId(), instance))
      .collect(Collectors.<Pair<String, Instance>>toList());

    return domainEventService.publishRecordsCreated(createdInstancesPairs);
  }

  public Future<Void> publishInstanceRemoved(Instance oldEntity) {
    return domainEventService.publishRecordRemoved(oldEntity.getId(), oldEntity);
  }

  public Future<Void> publishAllInstancesRemoved() {
    return domainEventService.publishAllRecordsRemoved();
  }

  public Function<Response, Future<Response>> publishInstancesCreatedOrUpdated(
    BatchOperationContext<Instance> batchOperation) {

    return response -> {
      if (!isCreateSuccessResponse(response)) {
        log.warn("Instance create/update failed, skipping event publishing");
        return succeededFuture(response);
      }

      log.info("Instances created {}, instances updated {}",
        batchOperation.getRecordsToBeCreated().size(),
        batchOperation.getExistingRecords().size());

      return publishInstancesCreated(batchOperation.getRecordsToBeCreated())
        .compose(notUsed -> publishInstancesUpdated(batchOperation.getExistingRecords()))
        .map(response);
    };
  }

  public Future<Void> publishInstanceReindex(String instanceId, String reindexJobId) {
    var jobIdHeader = Map.of(REINDEX_JOB_ID_HEADER, reindexJobId);

    return domainEventService.publishMessage(instanceId,
      reindexEvent(domainEventService.tenantId), jobIdHeader);
  }

  private Future<Void> publishInstancesUpdated(Collection<Instance> oldInstances) {
    log.info("[{}] instances were updated, sending events for them", oldInstances.size());

    return instanceRepository.getById(oldInstances, Instance::getId)
      .map(updatedInstances -> mapOldInstancesToUpdated(updatedInstances, oldInstances))
      .compose(domainEventService::publishRecordsUpdated);
  }

  private List<Triple<String, Instance, Instance>> mapOldInstancesToUpdated(
    Map<String, Instance> updatedInstancesMap, Collection<Instance> oldInstances) {

    return oldInstances.stream()
      .map(instance -> {
        final String instanceId = instance.getId();
        final Instance newInstance = updatedInstancesMap.get(instanceId);

        return new ImmutableTriple<>(instanceId, instance, newInstance);
      }).collect(Collectors.toList());
  }
}
