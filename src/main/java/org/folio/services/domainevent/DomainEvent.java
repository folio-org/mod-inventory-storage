package org.folio.services.domainevent;

import static org.folio.services.domainevent.DomainEventType.CREATE;
import static org.folio.services.domainevent.DomainEventType.DELETE;
import static org.folio.services.domainevent.DomainEventType.DELETE_ALL;
import static org.folio.services.domainevent.DomainEventType.REINDEX;
import static org.folio.services.domainevent.DomainEventType.UPDATE;

import org.apache.commons.lang3.builder.ToStringBuilder;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DomainEvent<T> {
  @JsonProperty("old")
  private T oldEntity;
  @JsonProperty("new")
  private T newEntity;
  private DomainEventType type;
  private String tenant;

  public DomainEvent(T oldEntity, T newEntity, DomainEventType type, String tenant) {
    this.oldEntity = oldEntity;
    this.newEntity = newEntity;
    this.type = type;
    this.tenant = tenant;
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
      .append("oldEntity", oldEntity)
      .append("newEntity", newEntity)
      .append("type", type)
      .append("tenant", tenant)
      .toString();
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

  public static <T> DomainEvent<T> reindexEvent(String tenant, T newEntity) {
    return new DomainEvent<>(null, newEntity, REINDEX, tenant);
  }
}
