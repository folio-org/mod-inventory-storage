package org.folio.persist.entity;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.folio.rest.jaxrs.model.ResultInfo;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
  "instances",
  "totalRecords",
  "resultInfo"
})
public class InstancesInternal {

  @JsonProperty("instances")
  @JsonPropertyDescription("List of instance records")
  @Valid
  @NotNull
  private List<InstanceInternal> instances = new ArrayList<>();
  @JsonProperty("totalRecords")
  @JsonPropertyDescription("Estimated or exact total number of records")
  @NotNull
  private Integer totalRecords;
  @JsonProperty("resultInfo")
  @JsonPropertyDescription("Faceting of result sets")
  @Valid
  private ResultInfo resultInfo;
  @JsonIgnore
  @Valid
  private final Map<String, Object> additionalProperties = new HashMap<>();

  @JsonProperty("instances")
  public List<InstanceInternal> getInstances() {
    return instances;
  }

  @JsonProperty("instances")
  public void setInstances(List<InstanceInternal> instances) {
    this.instances = instances;
  }

  @JsonProperty("totalRecords")
  public Integer getTotalRecords() {
    return totalRecords;
  }

  @JsonProperty("totalRecords")
  public void setTotalRecords(Integer totalRecords) {
    this.totalRecords = totalRecords;
  }

  public InstancesInternal withTotalRecords(Integer totalRecords) {
    this.totalRecords = totalRecords;
    return this;
  }

  @JsonProperty("resultInfo")
  public ResultInfo getResultInfo() {
    return resultInfo;
  }

  @JsonProperty("resultInfo")
  public void setResultInfo(ResultInfo resultInfo) {
    this.resultInfo = resultInfo;
  }

  @JsonAnyGetter
  public Map<String, Object> getAdditionalProperties() {
    return this.additionalProperties;
  }

  @JsonAnySetter
  public void setAdditionalProperty(String name, Object value) {
    this.additionalProperties.put(name, value);
  }

  public InstancesInternal withAdditionalProperty(String name, Object value) {
    this.additionalProperties.put(name, value);
    return this;
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder().append(totalRecords)
      .append(additionalProperties)
      .append(instances)
      .append(resultInfo)
      .toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof InstancesInternal rhs)) {
      return false;
    }
    return new EqualsBuilder().append(totalRecords, rhs.totalRecords)
      .append(additionalProperties, rhs.additionalProperties)
      .append(instances, rhs.instances)
      .append(resultInfo, rhs.resultInfo)
      .isEquals();
  }

}
