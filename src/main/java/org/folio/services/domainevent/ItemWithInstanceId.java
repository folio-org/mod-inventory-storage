package org.folio.services.domainevent;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.folio.rest.jaxrs.model.Item;

public class ItemWithInstanceId {
  private final String instanceId;
  @JsonUnwrapped
  private final Item item;

  public ItemWithInstanceId(Item item, String instanceId) {
    this.instanceId = instanceId;
    this.item = item;
  }

  public String getInstanceId() {
    return instanceId;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
      .append("instanceId", instanceId)
      .append("item", item)
      .toString();
  }
}
