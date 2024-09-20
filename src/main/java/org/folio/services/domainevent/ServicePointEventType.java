package org.folio.services.domainevent;

import static org.folio.InventoryKafkaTopic.SERVICE_POINT;
import static org.folio.services.domainevent.DomainEventType.DELETE;
import static org.folio.services.domainevent.DomainEventType.CREATE;
import static org.folio.services.domainevent.DomainEventType.UPDATE;

import org.folio.kafka.services.KafkaTopic;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ServicePointEventType {

  INVENTORY_SERVICE_POINT_CREATED(SERVICE_POINT, CREATE),
  INVENTORY_SERVICE_POINT_UPDATED(SERVICE_POINT, UPDATE),
  INVENTORY_SERVICE_POINT_DELETED(SERVICE_POINT, DELETE);

  private final KafkaTopic kafkaTopic;
  private final DomainEventType payloadType;

}
