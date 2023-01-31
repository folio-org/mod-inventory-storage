package org.folio.persist.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

@JsonIgnoreProperties(ignoreUnknown = true)
public class InstanceSetsInternal {

  /**
   * List of instance sets
   * (Required)
   */
  @JsonProperty("instanceSets")
  @JsonPropertyDescription("List of instance sets")
  @Valid
  @NotNull
  private List<InstanceSetInternal> instanceSets = new ArrayList<>();

  /**
   * List of instance sets
   * (Required)
   */
  @JsonProperty("instanceSets")
  public List<InstanceSetInternal> getInstanceSets() {
    return instanceSets;
  }

  /**
   * List of instance sets
   * (Required)
   */
  @JsonProperty("instanceSets")
  public void setInstanceSets(List<InstanceSetInternal> instanceSets) {
    this.instanceSets = instanceSets;
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder().append(instanceSets).toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof InstanceSetsInternal)) {
      return false;
    }
    InstanceSetsInternal rhs = ((InstanceSetsInternal) other);
    return new EqualsBuilder().append(instanceSets, rhs.instanceSets).isEquals();
  }
}
