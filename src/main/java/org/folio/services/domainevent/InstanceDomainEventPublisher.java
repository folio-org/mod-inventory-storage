package org.folio.services.domainevent;

import static io.vertx.core.Future.succeededFuture;
import static org.apache.logging.log4j.LogManager.getLogger;
import static org.folio.InventoryKafkaTopic.INSTANCE;
import static org.folio.InventoryKafkaTopic.REINDEX_RECORDS;
import static org.folio.rest.tools.utils.TenantTool.tenantId;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Logger;
import org.folio.persist.InstanceRepository;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.PublishReindexRecords;

public class InstanceDomainEventPublisher extends AbstractDomainEventPublisher<Instance, Instance> {

  private static final String MAX_REQUEST_SIZE = "KAFKA_REINDEX_PRODUCER_MAX_REQUEST_SIZE_BYTES";
  private static final Logger log = getLogger(InstanceDomainEventPublisher.class);
  
  private final CommonDomainEventPublisher<Map<String, Object>> instanceReindexPublisher;

  public InstanceDomainEventPublisher(Context context, Map<String, String> okapiHeaders) {
    super(new InstanceRepository(context, okapiHeaders),
      new CommonDomainEventPublisher<>(context, okapiHeaders,
        INSTANCE.fullTopicName(tenantId(okapiHeaders))));
    instanceReindexPublisher = new CommonDomainEventPublisher<>(context, okapiHeaders,
      REINDEX_RECORDS.fullTopicName(tenantId(okapiHeaders)), getProducerMaxRequestSize());
  }

  public Future<Void> publishReindexInstances(String key, List<Map<String, Object>> instances) {
    if (StringUtils.isBlank(key)) {
      return succeededFuture();
    }

    return instanceReindexPublisher.publishReindexRecords(key, PublishReindexRecords.RecordType.INSTANCE, instances);
  }

  public Future<Void> publishInstancesCreated(List<Instance> instances) {
    if (instances.isEmpty()) {
      log.info("No instances were created, skipping event sending");
      return succeededFuture();
    }

    log.info("[{}] instances were created, sending events for them", instances.size());

    return domainEventService.publishRecordsCreated(instances.stream()
      .map(instance -> pair(instance.getId(), instance))
      .toList());
  }

  @Override
  protected Future<List<Pair<String, Instance>>> getRecordIds(Collection<Instance> instances) {
    return succeededFuture(instances.stream()
      .map(instance -> pair(instance.getId(), instance))
      .toList());
  }

  @Override
  protected Instance convertDomainToEvent(String instanceId, Instance domain) {
    return domain;
  }

  @Override
  protected String getId(Instance instance) {
    return instance.getId();
  }

  private int getProducerMaxRequestSize() {
    return Integer.parseInt(StringUtils.firstNonBlank(
      System.getenv(MAX_REQUEST_SIZE),
      System.getProperty(MAX_REQUEST_SIZE),
      "10485760")); // 10MB
  }
}
