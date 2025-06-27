package org.folio.services.domainevent;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.InventoryKafkaTopic.INSTANCE;
import static org.folio.InventoryKafkaTopic.REINDEX_RECORDS;
import static org.folio.rest.tools.utils.TenantTool.tenantId;
import static org.folio.utils.Environment.getKafkaProducerMaxRequestSize;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.persist.InstanceRepository;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.PublishReindexRecords;

public class InstanceDomainEventPublisher extends AbstractDomainEventPublisher<Instance, Instance> {

  private final CommonDomainEventPublisher<Map<String, Object>> instanceReindexPublisher;

  public InstanceDomainEventPublisher(Context context, Map<String, String> okapiHeaders) {
    super(new InstanceRepository(context, okapiHeaders),
      new CommonDomainEventPublisher<>(context, okapiHeaders,
        INSTANCE.fullTopicName(tenantId(okapiHeaders))));
    instanceReindexPublisher = new CommonDomainEventPublisher<>(context, okapiHeaders,
      REINDEX_RECORDS.fullTopicName(tenantId(okapiHeaders)), getKafkaProducerMaxRequestSize());
  }

  public Future<Void> publishReindexInstances(String key, List<Map<String, Object>> instances) {
    if (StringUtils.isBlank(key)) {
      return succeededFuture();
    }

    return instanceReindexPublisher.publishReindexRecords(key, PublishReindexRecords.RecordType.INSTANCE, instances);
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
}
