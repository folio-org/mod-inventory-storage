package org.folio.persist.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.folio.rest.jaxrs.model.HoldingsItem;
import org.folio.rest.jaxrs.model.HoldingsRecords2;
import org.folio.rest.jaxrs.model.Instance;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"instanceId", "isBoundWith", "instance", "holdingsRecords", "items"})
public class InventoryViewInstanceInternal {

  @JsonProperty("instanceId")
  @JsonPropertyDescription(
    "A universally unique identifier (UUID), this is a 128-bit number used to identify a record and is "
      + "shown in hex with dashes, for example 6312d172-f0cf-40f6-b27d-9fa8feaf332f; the UUID version must be "
      + "from 1-5; see https://dev.folio.org/guides/uuids/")
  @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
  @NotNull
  private String instanceId;

  @JsonProperty("isBoundWith")
  @JsonPropertyDescription(
    "Records the relationship between a part of a bound-with (a holdings-record) and the bound-with as "
      + "a whole (the circulatable item)")
  private Boolean isBoundWith;
  @JsonPropertyDescription("An instance record")
  @Valid
  @NotNull
  private Instance instance;

  @JsonProperty("holdingsRecords")
  @JsonPropertyDescription("Holdings records for the instance")
  @Valid
  private List<HoldingsRecords2> holdingsRecords = new ArrayList<>();

  @JsonProperty("items")
  @JsonPropertyDescription("Items for the instance")
  @Valid
  private List<HoldingsItem> items = new ArrayList<>();

  @JsonProperty("instanceId")
  public String getInstanceId() {
    return instanceId;
  }

  @JsonProperty("instanceId")
  public void setInstanceId(String instanceId) {
    this.instanceId = instanceId;
  }

  @JsonProperty("isBoundWith")
  public Boolean getIsBoundWith() {
    return isBoundWith;
  }

  @JsonProperty("isBoundWith")
  public void setIsBoundWith(Boolean isBoundWith) {
    this.isBoundWith = isBoundWith;
  }

  @JsonProperty("holdingsRecords")
  public List<HoldingsRecords2> getHoldingsRecords() {
    return holdingsRecords;
  }

  @JsonProperty("holdingsRecords")
  public void setHoldingsRecords(List<HoldingsRecords2> holdingsRecords) {
    this.holdingsRecords = holdingsRecords;
  }

  @JsonProperty("instance")
  public Instance getInstance() {
    return instance;
  }

  @JsonProperty("instance")
  public void setInstance(InstanceInternal instance) {
    this.instance = instance.toInstanceDto();
  }

  @JsonProperty("items")
  public List<HoldingsItem> getItems() {
    return items;
  }

  @JsonProperty("items")
  public void setItems(List<HoldingsItem> items) {
    this.items = items;
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder().append(isBoundWith)
      .append(instanceId)
      .append(instance)
      .append(items)
      .append(holdingsRecords)
      .toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof InventoryViewInstanceInternal)) {
      return false;
    }
    InventoryViewInstanceInternal rhs = (InventoryViewInstanceInternal) other;
    return new EqualsBuilder().append(isBoundWith, rhs.isBoundWith)
      .append(instanceId, rhs.instanceId)
      .append(instance, rhs.instance)
      .append(items, rhs.items)
      .append(holdingsRecords, rhs.holdingsRecords)
      .isEquals();
  }

}
