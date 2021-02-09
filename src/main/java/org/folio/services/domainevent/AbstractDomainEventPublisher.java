package org.folio.services.domainevent;

import static io.vertx.core.Future.succeededFuture;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.apache.logging.log4j.LogManager.getLogger;
import static org.folio.rest.support.ResponseUtil.isCreateSuccessResponse;
import static org.folio.rest.support.ResponseUtil.isDeleteSuccessResponse;
import static org.folio.rest.support.ResponseUtil.isUpdateSuccessResponse;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.ws.rs.core.Response;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.Logger;
import org.folio.persist.AbstractRepository;
import org.folio.services.batch.BatchOperationContext;

import io.vertx.core.Future;

abstract class AbstractDomainEventPublisher<DomainType, EventType> {
  private static final Logger log = getLogger(AbstractDomainEventPublisher.class);

  protected final AbstractRepository<DomainType> repository;
  protected final CommonDomainEventPublisher<EventType> domainEventService;

  public AbstractDomainEventPublisher(AbstractRepository<DomainType> repository,
    CommonDomainEventPublisher<EventType> domainEventService) {

    this.repository = repository;
    this.domainEventService = domainEventService;
  }

  public Function<Response, Future<Response>> publishUpdated(DomainType oldRecord) {
    return response -> {
      if (!isUpdateSuccessResponse(response)) {
        log.warn("Record update failed, skipping event publishing");
        return succeededFuture(response);
      }

      return publishUpdated(singletonList(oldRecord)).map(response);
    };
  }

  public Function<Response, Future<Response>> publishCreated(DomainType record) {
    return response -> {
      if (!isCreateSuccessResponse(response)) {
        log.warn("Record create failed, skipping event publishing");
        return succeededFuture(response);
      }

      return publishCreated(singletonList(record)).map(response);
    };
  }

  public Function<Response, Future<Response>> publishCreatedOrUpdated(
    BatchOperationContext<DomainType> batchOperation) {

    return response -> {
      if (!isCreateSuccessResponse(response)) {
        log.warn("Records create/update failed, skipping event publishing");
        return succeededFuture(response);
      }

      log.info("Records created {}, records updated {}",
        batchOperation.getRecordsToBeCreated().size(),
        batchOperation.getExistingRecords().size());

      return publishCreated(batchOperation.getRecordsToBeCreated())
        .compose(notUsed -> publishUpdated(batchOperation.getExistingRecords()))
        .map(response);
    };
  }

  public Function<Response, Future<Response>> publishRemoved(DomainType record) {
    return response -> {
      if (!isDeleteSuccessResponse(response)) {
        log.warn("Record removal failed, no event will be sent");
        return succeededFuture(response);
      }

      return toInstanceIdEventTypePair(record)
        .compose(event -> domainEventService.publishRecordRemoved(event.getKey(), event.getValue()))
        .map(response);
    };
  }

  public Future<Void> publishAllRemoved() {
    return domainEventService.publishAllRecordsRemoved();
  }

  protected Future<Void> publishUpdated(Collection<DomainType> oldRecords) {
    if (oldRecords.isEmpty()) {
      log.info("No records were updated, skipping event sending");
      return succeededFuture();
    }

    log.info("[{}] records were updated, sending events for them", oldRecords.size());

    return repository.getById(oldRecords, this::getId)
      .map(updatedItems -> mapOldRecordsToUpdatedRecords(oldRecords, updatedItems))
      .compose(this::toInstanceIdEventTypeTriples)
      .compose(domainEventService::publishRecordsUpdated);
  }

  private Future<Void> publishCreated(Collection<DomainType> records) {
    return toInstanceIdEventTypePairs(records)
      .compose(domainEventService::publishRecordsCreated);
  }

  protected List<Pair<DomainType, DomainType>> mapOldRecordsToUpdatedRecords(
    Collection<DomainType> oldRecords, Map<String, DomainType> newRecords) {

    return oldRecords.stream()
      .map(oldItem -> new ImmutablePair<>(oldItem, newRecords.get(getId(oldItem))))
      .collect(toList());
  }

  protected abstract Future<List<Pair<String, EventType>>> toInstanceIdEventTypePairs(
    Collection<DomainType> records);

  private Future<Pair<String, EventType>> toInstanceIdEventTypePair(DomainType records) {
    return toInstanceIdEventTypePairs(List.of(records))
      .map(list -> list.get(0));
  }

  protected abstract Future<List<Triple<String, EventType, EventType>>> toInstanceIdEventTypeTriples(
    Collection<Pair<DomainType, DomainType>> oldToNewRecordPairs);

  protected abstract String getId(DomainType record);
}
