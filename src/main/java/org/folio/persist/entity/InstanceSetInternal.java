package org.folio.persist.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.folio.rest.jaxrs.model.HoldingsItem;
import org.folio.rest.jaxrs.model.HoldingsRecords2;
import org.folio.rest.jaxrs.model.PrecedingTitle;
import org.folio.rest.jaxrs.model.SuperInstanceRelationship;

/**
 * Instance with holdings, items, preceding titles, succeeding titles, super instances, sub instances
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class InstanceSetInternal {

  /**
   * A universally unique identifier (UUID), this is a 128-bit number used to identify a record and is shown in hex with dashes, for example 6312d172-f0cf-40f6-b27d-9fa8feaf332f; the UUID version must be from 1-5; see https://dev.folio.org/guides/uuids/
   * (Required)
   */
  @JsonProperty("id")
  @JsonPropertyDescription(
    "A universally unique identifier (UUID), this is a 128-bit number used to identify a record and is shown in hex with dashes, for example 6312d172-f0cf-40f6-b27d-9fa8feaf332f; the UUID version must be from 1-5; see https://dev.folio.org/guides/uuids/")
  @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
  @NotNull
  private String id;
  /**
   * An instance record
   */
  @JsonProperty("instance")
  @JsonPropertyDescription("An instance record")
  @Valid
  private InstanceInternal instance;
  /**
   * Holdings records of the instance
   */
  @JsonProperty("holdingsRecords")
  @JsonPropertyDescription("Holdings records of the instance")
  @Valid
  private List<HoldingsRecords2> holdingsRecords = new ArrayList<>();
  /**
   * Items of the instance
   */
  @JsonProperty("items")
  @JsonPropertyDescription("Items of the instance")
  @Valid
  private List<HoldingsItem> items = new ArrayList<>();
  /**
   * Instances that are preceding titles of the instance
   */
  @JsonProperty("precedingTitles")
  @JsonPropertyDescription("Instances that are preceding titles of the instance")
  @Valid
  private List<PrecedingTitle> precedingTitles = new ArrayList<>();
  /**
   * Instances that are succeeding titles of the instance
   */
  @JsonProperty("succeedingTitles")
  @JsonPropertyDescription("Instances that are succeeding titles of the instance")
  @Valid
  private List<PrecedingTitle> succeedingTitles = new ArrayList<>();
  /**
   * Instances that are super instances of the instance
   */
  @JsonProperty("superInstanceRelationships")
  @JsonPropertyDescription("Instances that are super instances of the instance")
  @Valid
  private List<SuperInstanceRelationship> superInstanceRelationships = new ArrayList<>();
  /**
   * Instances that are sub instances of the instance
   */
  @JsonProperty("subInstanceRelationships")
  @JsonPropertyDescription("Instances that are sub instances of the instance")
  @Valid
  private List<SuperInstanceRelationship> subInstanceRelationships = new ArrayList<>();

  /**
   * A universally unique identifier (UUID), this is a 128-bit number used to identify a record and is shown in hex with dashes, for example 6312d172-f0cf-40f6-b27d-9fa8feaf332f; the UUID version must be from 1-5; see https://dev.folio.org/guides/uuids/
   * (Required)
   */
  @JsonProperty("id")
  public String getId() {
    return id;
  }

  /**
   * A universally unique identifier (UUID), this is a 128-bit number used to identify a record and is shown in hex with dashes, for example 6312d172-f0cf-40f6-b27d-9fa8feaf332f; the UUID version must be from 1-5; see https://dev.folio.org/guides/uuids/
   * (Required)
   */
  @JsonProperty("id")
  public void setId(String id) {
    this.id = id;
  }

  /**
   * An instance record
   */
  @JsonProperty("instance")
  public InstanceInternal getInstance() {
    return instance;
  }

  /**
   * An instance record
   */
  @JsonProperty("instance")
  public void setInstance(InstanceInternal instance) {
    this.instance = instance;
  }

  /**
   * Holdings records of the instance
   */
  @JsonProperty("holdingsRecords")
  public List<HoldingsRecords2> getHoldingsRecords() {
    return holdingsRecords;
  }

  /**
   * Holdings records of the instance
   */
  @JsonProperty("holdingsRecords")
  public void setHoldingsRecords(List<HoldingsRecords2> holdingsRecords) {
    this.holdingsRecords = holdingsRecords;
  }

  /**
   * Items of the instance
   */
  @JsonProperty("items")
  public List<HoldingsItem> getItems() {
    return items;
  }

  /**
   * Items of the instance
   */
  @JsonProperty("items")
  public void setItems(List<HoldingsItem> items) {
    this.items = items;
  }

  public InstanceSetInternal withItems(List<HoldingsItem> items) {
    this.items = items;
    return this;
  }

  /**
   * Instances that are preceding titles of the instance
   */
  @JsonProperty("precedingTitles")
  public List<PrecedingTitle> getPrecedingTitles() {
    return precedingTitles;
  }

  /**
   * Instances that are preceding titles of the instance
   */
  @JsonProperty("precedingTitles")
  public void setPrecedingTitles(List<PrecedingTitle> precedingTitles) {
    this.precedingTitles = precedingTitles;
  }

  /**
   * Instances that are succeeding titles of the instance
   */
  @JsonProperty("succeedingTitles")
  public List<PrecedingTitle> getSucceedingTitles() {
    return succeedingTitles;
  }

  /**
   * Instances that are succeeding titles of the instance
   */
  @JsonProperty("succeedingTitles")
  public void setSucceedingTitles(List<PrecedingTitle> succeedingTitles) {
    this.succeedingTitles = succeedingTitles;
  }

  /**
   * Instances that are super instances of the instance
   */
  @JsonProperty("superInstanceRelationships")
  public List<SuperInstanceRelationship> getSuperInstanceRelationships() {
    return superInstanceRelationships;
  }

  /**
   * Instances that are super instances of the instance
   */
  @JsonProperty("superInstanceRelationships")
  public void setSuperInstanceRelationships(List<SuperInstanceRelationship> superInstanceRelationships) {
    this.superInstanceRelationships = superInstanceRelationships;
  }

  /**
   * Instances that are sub instances of the instance
   */
  @JsonProperty("subInstanceRelationships")
  public List<SuperInstanceRelationship> getSubInstanceRelationships() {
    return subInstanceRelationships;
  }

  /**
   * Instances that are sub instances of the instance
   */
  @JsonProperty("subInstanceRelationships")
  public void setSubInstanceRelationships(List<SuperInstanceRelationship> subInstanceRelationships) {
    this.subInstanceRelationships = subInstanceRelationships;
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder().append(superInstanceRelationships)
      .append(succeedingTitles)
      .append(instance)
      .append(precedingTitles)
      .append(holdingsRecords)
      .append(id)
      .append(subInstanceRelationships)
      .append(items)
      .toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof InstanceSetInternal)) {
      return false;
    }
    InstanceSetInternal rhs = ((InstanceSetInternal) other);
    return new EqualsBuilder().append(superInstanceRelationships, rhs.superInstanceRelationships)
      .append(succeedingTitles, rhs.succeedingTitles)
      .append(instance, rhs.instance)
      .append(precedingTitles, rhs.precedingTitles)
      .append(holdingsRecords, rhs.holdingsRecords)
      .append(id, rhs.id)
      .append(subInstanceRelationships, rhs.subInstanceRelationships)
      .append(items, rhs.items)
      .isEquals();
  }

}
