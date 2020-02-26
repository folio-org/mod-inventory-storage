package org.folio.rest.support;

import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Helper class for getting a raw ID.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class InstanceIDRaw {
  @JsonProperty("id")
  @JsonPropertyDescription("identifier")
  @NotNull
  private String id;

  public InstanceIDRaw() {
    id = "";
  }

  public InstanceIDRaw(String id) {
    this.id = id;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }
}
