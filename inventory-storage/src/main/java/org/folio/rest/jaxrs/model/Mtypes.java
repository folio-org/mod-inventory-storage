
package org.folio.rest.jaxrs.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    "mtypes",
    "totalRecords"
})
public class Mtypes {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("mtypes")
    @Valid
    @NotNull
    private List<Mtype> mtypes = new ArrayList<Mtype>();
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("totalRecords")
    @NotNull
    private Integer totalRecords;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    /**
     * 
     * (Required)
     * 
     * @return
     *     The mtypes
     */
    @JsonProperty("mtypes")
    public List<Mtype> getMtypes() {
        return mtypes;
    }

    /**
     * 
     * (Required)
     * 
     * @param mtypes
     *     The mtypes
     */
    @JsonProperty("mtypes")
    public void setMtypes(List<Mtype> mtypes) {
        this.mtypes = mtypes;
    }

    public Mtypes withMtypes(List<Mtype> mtypes) {
        this.mtypes = mtypes;
        return this;
    }

    /**
     * 
     * (Required)
     * 
     * @return
     *     The totalRecords
     */
    @JsonProperty("totalRecords")
    public Integer getTotalRecords() {
        return totalRecords;
    }

    /**
     * 
     * (Required)
     * 
     * @param totalRecords
     *     The totalRecords
     */
    @JsonProperty("totalRecords")
    public void setTotalRecords(Integer totalRecords) {
        this.totalRecords = totalRecords;
    }

    public Mtypes withTotalRecords(Integer totalRecords) {
        this.totalRecords = totalRecords;
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

    public Mtypes withAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
        return this;
    }

}
