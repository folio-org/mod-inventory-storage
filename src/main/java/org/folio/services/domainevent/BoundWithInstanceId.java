package org.folio.services.domainevent;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.folio.rest.jaxrs.model.BoundWithPart;

public class BoundWithInstanceId {
  private final String instanceId;
  @JsonUnwrapped
  private final BoundWithPart boundWithPart;

  public BoundWithInstanceId(BoundWithPart boundWithPart, String instanceId) {
    this.instanceId = instanceId;
    this.boundWithPart = boundWithPart;
  }

  public String getInstanceId() {
    return instanceId;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
      .append("instanceId", instanceId)
      .append("boundWithPart", boundWithPart)
      .toString();
  }
}
