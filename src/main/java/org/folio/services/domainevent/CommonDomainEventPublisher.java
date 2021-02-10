package org.folio.services.domainevent;

import static io.vertx.core.CompositeFuture.all;
import static io.vertx.core.Future.succeededFuture;
import static org.apache.logging.log4j.LogManager.getLogger;
import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.folio.okapi.common.XOkapiHeaders.URL;
import static org.folio.rest.tools.utils.TenantTool.tenantId;
import static org.folio.services.domainevent.DomainEvent.createEvent;
import static org.folio.services.domainevent.DomainEvent.deleteAllEvent;
import static org.folio.services.domainevent.DomainEvent.deleteEvent;
import static org.folio.services.domainevent.DomainEvent.updateEvent;
import static org.folio.services.kafka.KafkaProducerServiceFactory.getKafkaProducerService;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.Logger;
import org.folio.services.kafka.KafkaMessage;
import org.folio.services.kafka.topic.KafkaTopic;

import io.vertx.core.Context;
import io.vertx.core.Future;

public class CommonDomainEventPublisher<T> {
  public static final String NULL_INSTANCE_ID = "00000000-0000-0000-0000-000000000000";
  private static final Logger log = getLogger(CommonDomainEventPublisher.class);
  private static final Set<String> FORWARDER_HEADERS = Set.of(URL.toLowerCase(),
    TENANT.toLowerCase());

  private final Context vertxContext;
  private final Map<String, String> okapiHeaders;
  private final KafkaTopic kafkaTopic;

  CommonDomainEventPublisher(Context vertxContext, Map<String, String> okapiHeaders,
    KafkaTopic kafkaTopic) {

    this.vertxContext = vertxContext;
    this.okapiHeaders = okapiHeaders;
    this.kafkaTopic = kafkaTopic;
  }

  Future<Void> publishRecordUpdated(String instanceId, T oldRecord, T newRecord) {
    final DomainEvent<T> domainEvent = updateEvent(oldRecord, newRecord, getTenant());

    return publishMessage(instanceId, domainEvent);
  }

  Future<Void> publishRecordsUpdated(Collection<Triple<String, T, T>> updatedRecords) {
    if (updatedRecords.isEmpty()) {
      return succeededFuture();
    }

    return all(updatedRecords.stream()
      .map(record -> publishRecordUpdated(record.getLeft(), record.getMiddle(), record.getRight()))
      .collect(Collectors.toList()))
      .map(notUsed -> null);
  }

  Future<Void> publishRecordCreated(String instanceId, T newRecord) {
    final DomainEvent<T> domainEvent = createEvent(newRecord, getTenant());

    return publishMessage(instanceId, domainEvent);
  }

  Future<Void> publishRecordsCreated(List<Pair<String, T>> records) {
    if (records.isEmpty()) {
      return succeededFuture();
    }

    return all(records.stream()
      .map(record -> publishRecordCreated(record.getKey(), record.getValue()))
      .collect(Collectors.toList()))
      .map(notUsed -> null);
  }

  Future<Void> publishRecordRemoved(String instanceId, T oldEntity) {
    final DomainEvent<T> domainEvent = deleteEvent(oldEntity, getTenant());

    return publishMessage(instanceId, domainEvent);
  }

  Future<Void> publishAllRecordsRemoved() {
    return publishMessage(NULL_INSTANCE_ID, deleteAllEvent(getTenant()));
  }

  private Future<Void> publishMessage(String instanceId, DomainEvent<?> domainEvent) {
    log.debug("Sending domain event [{}], payload [{}]", instanceId, domainEvent);

    return getKafkaProducerService(vertxContext.owner())
      .sendMessage(KafkaMessage.builder()
        .key(instanceId).payload(domainEvent).topic(kafkaTopic)
        .headers(getHeadersToForward()).build())
      .onComplete(result -> {
        if (result.failed()) {
          log.error("Unable to send domain event [{}], payload - [{}]",
            instanceId, domainEvent, result.cause());
        }
      });
  }

  private Map<String, String> getHeadersToForward() {
    return okapiHeaders.entrySet().stream()
      .filter(entry -> FORWARDER_HEADERS.contains(entry.getKey().toLowerCase()))
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private String getTenant() {
    return tenantId(okapiHeaders);
  }
}
