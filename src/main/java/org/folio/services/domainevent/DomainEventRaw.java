package org.folio.services.domainevent;

import static org.folio.services.domainevent.DomainEventType.DELETE;

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

  public static DomainEventRaw deleteEvent(String oldEntity, String tenant) {
    return new DomainEventRaw(oldEntity, null, DELETE, tenant);
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
