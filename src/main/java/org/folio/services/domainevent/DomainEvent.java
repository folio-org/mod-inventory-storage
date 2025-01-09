package org.folio.services.domainevent;

import static org.folio.services.domainevent.DomainEventType.CREATE;
import static org.folio.services.domainevent.DomainEventType.DELETE;
import static org.folio.services.domainevent.DomainEventType.DELETE_ALL;
import static org.folio.services.domainevent.DomainEventType.MIGRATION;
import static org.folio.services.domainevent.DomainEventType.REINDEX;
import static org.folio.services.domainevent.DomainEventType.UPDATE;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import org.apache.commons.lang3.builder.ToStringBuilder;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DomainEvent<T> {

  private UUID eventId;
  private Long eventTs;
  @JsonProperty("old")
  private T oldEntity;
  @JsonProperty("new")
  private T newEntity;
  private DomainEventType type;
  private String tenant;

  @JsonCreator
  public DomainEvent(@JsonProperty("old") T oldEntity, @JsonProperty("new") T newEntity,
                     @JsonProperty("type") DomainEventType type, @JsonProperty("tenant") String tenant) {
    this.eventId = UUID.randomUUID();
    this.eventTs = System.currentTimeMillis();
    this.oldEntity = oldEntity;
    this.newEntity = newEntity;
    this.type = type;
    this.tenant = tenant;
  }

  public static <T> DomainEvent<T> updateEvent(T oldEntity, T newEntity, String tenant) {
    return new DomainEvent<>(oldEntity, newEntity, UPDATE, tenant);
  }

  public static <T> DomainEvent<T> createEvent(T newEntity, String tenant) {
    return new DomainEvent<>(null, newEntity, CREATE, tenant);
  }

  public static <T> DomainEvent<T> deleteEvent(T oldEntity, String tenant) {
    return new DomainEvent<>(oldEntity, null, DELETE, tenant);
  }

  public static <T> DomainEvent<T> deleteAllEvent(String tenant) {
    return new DomainEvent<>(null, null, DELETE_ALL, tenant);
  }

  public static <T> DomainEvent<T> reindexEvent(String tenant) {
    return new DomainEvent<>(null, null, REINDEX, tenant);
  }

  public static <T> DomainEvent<T> asyncMigrationEvent(T job, String tenant) {
    return new DomainEvent<>(null, job, MIGRATION, tenant);
  }

  public UUID getEventId() {
    return eventId;
  }

  public void setEventId(UUID eventId) {
    this.eventId = eventId;
  }

  public Long getEventTs() {
    return eventTs;
  }

  public void setEventTs(Long eventTs) {
    this.eventTs = eventTs;
  }

  public T getOldEntity() {
    return oldEntity;
  }

  public void setOldEntity(T oldEntity) {
    this.oldEntity = oldEntity;
  }

  public Object getNewEntity() {
    return newEntity;
  }

  public void setNewEntity(T newEntity) {
    this.newEntity = newEntity;
  }

  public DomainEventType getType() {
    return type;
  }

  public void setType(DomainEventType type) {
    this.type = type;
  }

  public String getTenant() {
    return tenant;
  }

  public void setTenant(String tenant) {
    this.tenant = tenant;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
      .append("eventId", eventId)
      .append("eventTs", eventTs)
      .append("oldEntity", oldEntity)
      .append("newEntity", newEntity)
      .append("type", type)
      .append("tenant", tenant)
      .toString();
  }
}
