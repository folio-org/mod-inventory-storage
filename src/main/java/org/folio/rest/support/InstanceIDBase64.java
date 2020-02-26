package org.folio.rest.support;

import java.util.Base64;

import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Helper class for getting a base64-encoded ID.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class InstanceIDBase64 {
  @JsonProperty("id")
  @JsonPropertyDescription("identifier")
  @NotNull
  private String id;

  public InstanceIDBase64() {
    id = "";
  }

  public InstanceIDBase64(String id) {
    this.id = id;
  }

  public String getId() {
    Base64.Encoder encoder = Base64.getEncoder();
    return encoder.encodeToString(id.getBytes());
  }

  public void setId(String id) {
    this.id = id;
  }

}
