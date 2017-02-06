
package org.folio.rest.jaxrs.model;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Generated;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("org.jsonschema2pojo")
@JsonPropertyOrder({
    "id",
    "instanceId",
    "title",
    "barcode",
    "status",
    "materialType",
    "location"
})
public class Item {

    @JsonProperty("id")
    private String id;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("instanceId")
    @NotNull
    private String instanceId;
    @JsonProperty("title")
    private String title;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("barcode")
    @NotNull
    private String barcode;
    @JsonProperty("status")
    @Valid
    private Status status;
    @JsonProperty("materialType")
    @Valid
    private MaterialType materialType;
    @JsonProperty("location")
    @Valid
    private Location location;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    /**
     * 
     * @return
     *     The id
     */
    @JsonProperty("id")
    public String getId() {
        return id;
    }

    /**
     * 
     * @param id
     *     The id
     */
    @JsonProperty("id")
    public void setId(String id) {
        this.id = id;
    }

    public Item withId(String id) {
        this.id = id;
        return this;
    }

    /**
     * 
     * (Required)
     * 
     * @return
     *     The instanceId
     */
    @JsonProperty("instanceId")
    public String getInstanceId() {
        return instanceId;
    }

    /**
     * 
     * (Required)
     * 
     * @param instanceId
     *     The instanceId
     */
    @JsonProperty("instanceId")
    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public Item withInstanceId(String instanceId) {
        this.instanceId = instanceId;
        return this;
    }

    /**
     * 
     * @return
     *     The title
     */
    @JsonProperty("title")
    public String getTitle() {
        return title;
    }

    /**
     * 
     * @param title
     *     The title
     */
    @JsonProperty("title")
    public void setTitle(String title) {
        this.title = title;
    }

    public Item withTitle(String title) {
        this.title = title;
        return this;
    }

    /**
     * 
     * (Required)
     * 
     * @return
     *     The barcode
     */
    @JsonProperty("barcode")
    public String getBarcode() {
        return barcode;
    }

    /**
     * 
     * (Required)
     * 
     * @param barcode
     *     The barcode
     */
    @JsonProperty("barcode")
    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public Item withBarcode(String barcode) {
        this.barcode = barcode;
        return this;
    }

    /**
     * 
     * @return
     *     The status
     */
    @JsonProperty("status")
    public Status getStatus() {
        return status;
    }

    /**
     * 
     * @param status
     *     The status
     */
    @JsonProperty("status")
    public void setStatus(Status status) {
        this.status = status;
    }

    public Item withStatus(Status status) {
        this.status = status;
        return this;
    }

    /**
     * 
     * @return
     *     The materialType
     */
    @JsonProperty("materialType")
    public MaterialType getMaterialType() {
        return materialType;
    }

    /**
     * 
     * @param materialType
     *     The materialType
     */
    @JsonProperty("materialType")
    public void setMaterialType(MaterialType materialType) {
        this.materialType = materialType;
    }

    public Item withMaterialType(MaterialType materialType) {
        this.materialType = materialType;
        return this;
    }

    /**
     * 
     * @return
     *     The location
     */
    @JsonProperty("location")
    public Location getLocation() {
        return location;
    }

    /**
     * 
     * @param location
     *     The location
     */
    @JsonProperty("location")
    public void setLocation(Location location) {
        this.location = location;
    }

    public Item withLocation(Location location) {
        this.location = location;
        return this;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

    public Item withAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
        return this;
    }

}
