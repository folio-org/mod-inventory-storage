package org.folio.services.domainevent;

import static org.folio.services.domainevent.DomainEventType.CREATE;
import static org.folio.services.domainevent.DomainEventType.DELETE;
import static org.folio.services.domainevent.DomainEventType.DELETE_ALL;
import static org.folio.services.domainevent.DomainEventType.MIGRATION;
import static org.folio.services.domainevent.DomainEventType.REINDEX;
import static org.folio.services.domainevent.DomainEventType.UPDATE;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * Domain event with old and new entity stored as raw (serialized) JSON String.
 *
 * <p>This allows to send the raw JSON String from the database to Kafka
 * without deserializing and serializing it.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DomainEventRaw {
  @JsonProperty("old")
  @JsonRawValue
  private final String oldEntity;
  @JsonProperty("new")
  @JsonRawValue
  private final String newEntity;
  private final DomainEventType type;
  private final String tenant;

  public DomainEventRaw(String oldEntity, String newEntity, DomainEventType type, String tenant) {
    this.oldEntity = oldEntity;
    this.newEntity = newEntity;
    this.type = type;
    this.tenant = tenant;
  }

  public static DomainEventRaw updateEvent(String oldEntity, String newEntity, String tenant) {
    return new DomainEventRaw(oldEntity, newEntity, UPDATE, tenant);
  }

  public static DomainEventRaw createEvent(String newEntity, String tenant) {
    return new DomainEventRaw(null, newEntity, CREATE, tenant);
  }

  public static DomainEventRaw deleteEvent(String oldEntity, String tenant) {
    return new DomainEventRaw(oldEntity, null, DELETE, tenant);
  }

  public static DomainEventRaw deleteAllEvent(String tenant) {
    return new DomainEventRaw(null, null, DELETE_ALL, tenant);
  }

  public static DomainEventRaw reindexEvent(String tenant) {
    return new DomainEventRaw(null, null, REINDEX, tenant);
  }

  public static DomainEventRaw reindexEvent(String tenant, String newEntity) {
    return new DomainEventRaw(null, newEntity, REINDEX, tenant);
  }

  public static DomainEventRaw asyncMigrationEvent(String job, String tenant) {
    return new DomainEventRaw(null, job, MIGRATION, tenant);
  }

  public String getOldEntity() {
    return oldEntity;
  }

  public Object getNewEntity() {
    return newEntity;
  }

  public DomainEventType getType() {
    return type;
  }

  public String getTenant() {
    return tenant;
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
}
