package org.folio.services.domainevent;

import static io.vertx.core.Future.succeededFuture;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toMap;
import static org.apache.logging.log4j.LogManager.getLogger;
import static org.folio.rest.tools.utils.TenantTool.tenantId;

import io.vertx.core.Future;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.Logger;
import org.folio.persist.AbstractRepository;
import org.folio.services.batch.BatchOperationContext;
import org.folio.utils.CollectionUtil;

abstract class AbstractDomainEventPublisher<D, E> {
  private static final Logger log = getLogger(AbstractDomainEventPublisher.class);

  protected final CommonDomainEventPublisher<E> domainEventService;

  protected AbstractDomainEventPublisher(CommonDomainEventPublisher<E> domainEventService) {
    this.domainEventService = domainEventService;
  }

  public Future<Void> publishRecordCreated(D newRecord, Map<String, String> headers) {
    return publishRecordsCreated(singletonList(newRecord), headers);
  }

  public Future<Void> publishRecordsCreatedOrUpdated(BatchOperationContext<D> batchOperation,
                                                     Map<String, String> headers) {
    log.info("Records created {}, records updated {}", batchOperation.recordsToBeCreated().size(),
      batchOperation.existingRecords().size());
    if (!batchOperation.publishEvents()) {
      return succeededFuture();
    }
    return publishRecordsCreated(batchOperation.recordsToBeCreated(), headers)
      .compose(notUsed -> publishUpdated(batchOperation.existingRecords(), headers));
  }

  public Future<Void> publishRecordUpdated(D oldRecord, Map<String, String> headers) {
    return publishUpdated(singletonList(oldRecord), headers);
  }

  public Future<Void> publishRecordRemoved(D removedRecord, Map<String, String> headers) {
    return getInstanceId(removedRecord, headers).compose(instanceId -> domainEventService.publishRecordRemoved(
      instanceId, convertDomainToEvent(instanceId, removedRecord), headers));
  }

  public void publishRemoved(String instanceId, String rawRecord, Map<String, String> headers) {
    domainEventService.publishRecordRemoved(instanceId, rawRecord, headers);
  }

  public Future<Void> publishAllRemoved(Map<String, String> headers) {
    return domainEventService.publishAllRecordsRemoved(headers);
  }

  protected abstract AbstractRepository<D> repository();

  protected Future<Void> publishUpdated(Collection<D> oldRecords, Map<String, String> headers) {
    if (oldRecords.isEmpty()) {
      log.info("No records were updated, skipping event sending");
      return succeededFuture();
    }

    log.info("[{}] records were updated, sending events for them", oldRecords.size());

    return repository().getByIds(oldRecords, this::getId, tenantId(headers))
      .compose(updatedItems -> convertDomainsToEvents(updatedItems.values(), oldRecords, headers))
      .compose(events -> domainEventService.publishRecordsUpdated(events, headers));
  }

  protected abstract Future<List<Pair<String, D>>> getRecordIds(Collection<D> domainTypes,
                                                                Map<String, String> headers);

  protected abstract E convertDomainToEvent(String instanceId, D domain);

  protected abstract String getId(D entity);

  protected List<Triple<String, E, E>> mapOldRecordsToNew(List<Pair<String, D>> oldRecords,
                                                          List<Pair<String, D>> newRecords) {

    var idToOldRecordPairMap = oldRecords.stream().collect(toMap(pair -> getId(pair.getValue()), pair -> pair));

    return newRecords.stream().map(newRecordPair -> {
      var oldRecordPair = idToOldRecordPairMap.get(getId(newRecordPair.getValue()));
      return triple(newRecordPair.getKey(), convertDomainToEvent(oldRecordPair.getKey(), oldRecordPair.getValue()),
        convertDomainToEvent(newRecordPair.getKey(), newRecordPair.getValue()));
    }).toList();
  }

  protected Future<List<Pair<String, E>>> convertDomainsToEvents(Collection<D> domains,
                                                                 Map<String, String> headers) {
    return getRecordIds(domains, headers).map(pairs -> pairs.stream()
      .map(pair -> pair(pair.getKey(), convertDomainToEvent(pair.getKey(), pair.getValue())))
      .toList());
  }

  protected Future<List<Triple<String, E, E>>> convertDomainsToEvents(Collection<D> newRecords,
                                                                      Collection<D> oldRecords,
                                                                      Map<String, String> headers) {

    return getRecordIds(oldRecords, headers).compose(oldRecordsInstanceIds ->
      getRecordIds(newRecords, headers).map(
        newRecordsInstanceIds -> mapOldRecordsToNew(oldRecordsInstanceIds, newRecordsInstanceIds)));
  }

  static <L, R> Pair<L, R> pair(L left, R right) {
    return new ImmutablePair<>(left, right);
  }

  static <L, M, R> Triple<L, M, R> triple(L left, M middle, R right) {
    return new ImmutableTriple<>(left, middle, right);
  }

  private Future<Void> publishRecordsCreated(Collection<D> records, Map<String, String> headers) {
    return convertDomainsToEvents(records, headers)
      .compose(events -> domainEventService.publishRecordsCreated(events, headers));
  }

  private Future<String> getInstanceId(D domainType, Map<String, String> headers) {
    return getRecordIds(List.of(domainType), headers).map(CollectionUtil::getFirst).map(Pair::getKey);
  }
}
