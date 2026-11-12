package org.folio.services.domainevent.model;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.folio.rest.jaxrs.model.Item;

/**
 * The item as published in item domain events, with the id and the {@code source} of the item's instance.
 */
public class ItemWithInstanceId {
  private final String instanceId;
  private final String instanceSource;
  @JsonUnwrapped
  private final Item item;

  public ItemWithInstanceId(Item item, String instanceId, String instanceSource) {
    this.instanceId = instanceId;
    this.instanceSource = instanceSource;
    this.item = item;
  }

  public String getInstanceId() {
    return instanceId;
  }

  public String getInstanceSource() {
    return instanceSource;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
      .append("instanceId", instanceId)
      .append("instanceSource", instanceSource)
      .append("item", item)
      .toString();
  }
}
