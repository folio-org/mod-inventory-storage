package org.folio.services.domainevent;

import static org.folio.services.domainevent.DomainEventType.CREATE;
import static org.folio.services.domainevent.DomainEventType.DELETE;
import static org.folio.services.domainevent.DomainEventType.UPDATE;

import org.apache.commons.lang3.builder.ToStringBuilder;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DomainEvent {
  @JsonProperty("old")
  private Object oldEntity;
  @JsonProperty("new")
  private Object newEntity;
  private DomainEventType type;
  private String tenant;

  public DomainEvent(Object oldEntity, Object newEntity, DomainEventType type, String tenant) {
    this.oldEntity = oldEntity;
    this.newEntity = newEntity;
    this.type = type;
    this.tenant = tenant;
  }

  public Object getOldEntity() {
    return oldEntity;
  }

  public void setOldEntity(Object oldEntity) {
    this.oldEntity = oldEntity;
  }

  public Object getNewEntity() {
    return newEntity;
  }

  public void setNewEntity(Object newEntity) {
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

  public static DomainEvent updateEvent(Object oldEntity, Object newEntity, String tenant) {
    return new DomainEvent(oldEntity, newEntity, UPDATE, tenant);
  }

  public static DomainEvent createEvent(Object newEntity, String tenant) {
    return new DomainEvent(null, newEntity, CREATE, tenant);
  }

  public static DomainEvent deleteEvent(Object oldEntity, String tenant) {
    return new DomainEvent(oldEntity, null, DELETE, tenant);
  }
}
