package org.folio.services.domainevent;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.InventoryKafkaTopic.INSTANCE;
import static org.folio.InventoryKafkaTopic.REINDEX_RECORDS;
import static org.folio.rest.jaxrs.model.ReindexRecordsRequest.RecordType;

import io.vertx.core.Future;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.persist.AbstractRepository;
import org.folio.persist.InstanceRepository;
import org.folio.rest.jaxrs.model.Instance;
import org.springframework.stereotype.Component;

@Component
public class InstanceDomainEventPublisher extends AbstractDomainEventPublisher<Instance, Instance> {

  private final CommonDomainEventPublisher<Map<String, Object>> instanceReindexPublisher;
  private final InstanceRepository instanceRepository;

  public InstanceDomainEventPublisher(DomainEventPublisherFactory publisherFactory,
                                      InstanceRepository instanceRepository) {
    super(publisherFactory.publisher(INSTANCE));
    this.instanceRepository = instanceRepository;
    this.instanceReindexPublisher = publisherFactory.reindexPublisher(REINDEX_RECORDS);
  }

  public Future<Void> publishReindexInstances(String key, List<Map<String, Object>> instances,
                                              Map<String, String> headers) {
    if (StringUtils.isBlank(key)) {
      return succeededFuture();
    }

    return instanceReindexPublisher.publishReindexRecords(key, RecordType.INSTANCE, instances, headers);
  }

  @Override
  protected AbstractRepository<Instance> repository() {
    return instanceRepository;
  }

  @Override
  protected Future<List<Pair<String, Instance>>> getRecordIds(Collection<Instance> instances,
                                                              Map<String, String> headers) {
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
