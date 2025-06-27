package org.folio.services.domainevent;

import static io.vertx.core.Future.succeededFuture;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toMap;
import static org.apache.logging.log4j.LogManager.getLogger;
import static org.folio.rest.support.ResponseUtil.isCreateSuccessResponse;
import static org.folio.rest.support.ResponseUtil.isDeleteSuccessResponse;
import static org.folio.rest.support.ResponseUtil.isUpdateSuccessResponse;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import java.util.Collection;
import java.util.List;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.Logger;
import org.folio.persist.AbstractRepository;
import org.folio.rest.support.CollectionUtil;
import org.folio.services.batch.BatchOperationContext;

abstract class AbstractDomainEventPublisher<D, E> {
  private static final Logger log = getLogger(AbstractDomainEventPublisher.class);

  protected final AbstractRepository<D> repository;
  protected final CommonDomainEventPublisher<E> domainEventService;

  protected AbstractDomainEventPublisher(AbstractRepository<D> repository,
                                         CommonDomainEventPublisher<E> domainEventService) {

    this.repository = repository;
    this.domainEventService = domainEventService;
  }

  static <L, R> Pair<L, R> pair(L left, R right) {
    return new ImmutablePair<>(left, right);
  }

  static <L, M, R> Triple<L, M, R> triple(L left, M middle, R right) {
    return new ImmutableTriple<>(left, middle, right);
  }

  @SuppressWarnings("unchecked")
  public Handler<Response> publishCreated() {
    return response -> {
      if (!isCreateSuccessResponse(response)) {
        log.warn("Record create failed, skipping event publishing");
        return;
      }

      publishRecordsCreated(singletonList((D) response.getEntity()));
    };
  }

  public Handler<Response> publishCreatedOrUpdated(BatchOperationContext<D> batchOperation) {

    return response -> {
      if (!isCreateSuccessResponse(response)) {
        log.warn("Records create/update failed, skipping event publishing");
        return;
      }

      log.info("Records created {}, records updated {}", batchOperation.recordsToBeCreated().size(),
        batchOperation.existingRecords().size());

      if (batchOperation.publishEvents()) {
        publishRecordsCreated(batchOperation.recordsToBeCreated()).compose(
          notUsed -> publishUpdated(batchOperation.existingRecords()));
      }
    };
  }

  public Handler<Response> publishRemoved(D removedRecord) {
    return response -> {
      if (!isDeleteSuccessResponse(response)) {
        log.warn("Record removal failed, no event will be sent");
        return;
      }

      getInstanceId(removedRecord).compose(instanceId -> domainEventService.publishRecordRemoved(instanceId,
        convertDomainToEvent(instanceId, removedRecord)));
    };
  }

  public void publishRemoved(String instanceId, String rawRecord) {
    domainEventService.publishRecordRemoved(instanceId, rawRecord);
  }

  public Future<Void> publishAllRemoved() {
    return domainEventService.publishAllRecordsRemoved();
  }

  public Handler<Response> publishUpdated(D oldRecord) {
    return response -> {
      if (!isUpdateSuccessResponse(response)) {
        log.warn("Record update failed, skipping event publishing");
        return;
      }

      publishUpdated(singletonList(oldRecord));
    };
  }

  protected Future<Void> publishUpdated(Collection<D> oldRecords) {
    if (oldRecords.isEmpty()) {
      log.info("No records were updated, skipping event sending");
      return succeededFuture();
    }

    log.info("[{}] records were updated, sending events for them", oldRecords.size());

    return repository.getByIds(oldRecords, this::getId)
      .compose(updatedItems -> convertDomainsToEvents(updatedItems.values(), oldRecords))
      .compose(domainEventService::publishRecordsUpdated);
  }

  protected abstract Future<List<Pair<String, D>>> getRecordIds(Collection<D> domainTypes);

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

  private Future<Void> publishRecordsCreated(Collection<D> records) {
    return convertDomainsToEvents(records).compose(domainEventService::publishRecordsCreated);
  }

  protected Future<List<Pair<String, E>>> convertDomainsToEvents(Collection<D> domains) {
    return getRecordIds(domains).map(pairs -> pairs.stream()
      .map(pair -> pair(pair.getKey(), convertDomainToEvent(pair.getKey(), pair.getValue())))
      .toList());
  }

  protected Future<List<Triple<String, E, E>>> convertDomainsToEvents(Collection<D> newRecords,
                                                                    Collection<D> oldRecords) {

    return getRecordIds(oldRecords).compose(oldRecordsInstanceIds -> getRecordIds(newRecords).map(
      newRecordsInstanceIds -> mapOldRecordsToNew(oldRecordsInstanceIds, newRecordsInstanceIds)));
  }

  private Future<String> getInstanceId(D domainType) {
    return getRecordIds(List.of(domainType)).map(CollectionUtil::getFirst).map(Pair::getKey);
  }
}
