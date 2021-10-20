package org.folio.rest.support;

import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Helper class for getting an instance ID from holding records.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InstanceID {
  @JsonProperty("instanceId")
  @JsonPropertyDescription("instanceId")
  @NotNull
  private String instanceId;

  public InstanceID() {
    instanceId = "";
  }

  public String getId() {
    return instanceId;
  }

  public void setId(String id) {
    this.instanceId = id;
  }
}
