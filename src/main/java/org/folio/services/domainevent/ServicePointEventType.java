package org.folio.services.domainevent;

import static org.folio.InventoryKafkaTopic.SERVICE_POINT;
import static org.folio.services.domainevent.DomainEventType.CREATE;
import static org.folio.services.domainevent.DomainEventType.DELETE;
import static org.folio.services.domainevent.DomainEventType.UPDATE;

import org.folio.kafka.services.KafkaTopic;

public enum ServicePointEventType {

  SERVICE_POINT_CREATED(SERVICE_POINT, CREATE),
  SERVICE_POINT_UPDATED(SERVICE_POINT, UPDATE),
  SERVICE_POINT_DELETED(SERVICE_POINT, DELETE);

  private final KafkaTopic kafkaTopic;
  private final DomainEventType payloadType;

  ServicePointEventType(KafkaTopic kafkaTopic, DomainEventType payloadType) {
    this.kafkaTopic = kafkaTopic;
    this.payloadType = payloadType;
  }

  public KafkaTopic getKafkaTopic() {
    return kafkaTopic;
  }

  public DomainEventType getPayloadType() {
    return payloadType;
  }
}
