package org.folio.rest.support;

import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Helper class for getting an ID.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class InstanceID {
  @JsonProperty("id")
  @JsonPropertyDescription("identifier")
  @NotNull
  private String id;

  public InstanceID() {
    id = "";
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }
}
