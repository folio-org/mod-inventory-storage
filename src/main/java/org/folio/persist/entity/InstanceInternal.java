package org.folio.persist.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.folio.rest.jaxrs.model.AlternativeTitle;
import org.folio.rest.jaxrs.model.Classification;
import org.folio.rest.jaxrs.model.Contributor;
import org.folio.rest.jaxrs.model.ElectronicAccess;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Identifier;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.InstanceFormat;
import org.folio.rest.jaxrs.model.InstanceNote;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.jaxrs.model.Publication;
import org.folio.rest.jaxrs.model.PublicationPeriod;
import org.folio.rest.jaxrs.model.Series;
import org.folio.rest.jaxrs.model.Subject;
import org.folio.rest.jaxrs.model.Tags;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class InstanceInternal {

  /**
   * A universally unique identifier (UUID), this is a 128-bit number used to identify a record and is shown in hex with dashes, for example 6312d172-f0cf-40f6-b27d-9fa8feaf332f; the UUID version must be from 1-5; see https://dev.folio.org/guides/uuids/
   */
  @JsonProperty("id")
  @JsonPropertyDescription(
    "A universally unique identifier (UUID), this is a 128-bit number used to identify a record and is shown in hex with dashes, for example 6312d172-f0cf-40f6-b27d-9fa8feaf332f; the UUID version must be from 1-5; see https://dev.folio.org/guides/uuids/")
  @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
  private String id;
  /**
   * Record version for optimistic locking.
   */
  @JsonProperty("_version")
  @JsonPropertyDescription("Record version for optimistic locking")
  private Integer version;
  /**
   * The human readable ID, also called eye readable ID. A system-assigned
   * sequential ID which maps to the InstanceInternal ID
   */
  @JsonProperty("hrid")
  @JsonPropertyDescription(
    "The human readable ID, also called eye readable ID. A system-assigned sequential ID which maps to the "
      + "InstanceInternal ID")
  private String hrid;
  /**
   * A unique instance identifier matching a client-side bibliographic record identification scheme,
   * in particular for a scenario where multiple separate catalogs with no shared record identifiers
   * contribute to the same InstanceInternal in Inventory. A match key is typically generated from select,
   * normalized pieces of metadata in bibliographic records
   */
  @JsonProperty("matchKey")
  @JsonPropertyDescription(
    "A unique instance identifier matching a client-side bibliographic record identification scheme, "
      + "in particular for a scenario where multiple separate catalogs with no shared record identifiers "
      + "contribute to the same InstanceInternal in Inventory. A match key is typically generated from select, "
      + "normalized pieces of metadata in bibliographic records")
  private String matchKey;
  /**
   * The metadata source and its format of the underlying record to the instance record.
   * (e.g. FOLIO if it's a record created in Inventory;  MARC if it's a MARC record created in
   * MARCcat or EPKB if it's a record coming from eHoldings)
   * (Required)
   */
  @JsonProperty("source")
  @JsonPropertyDescription("The metadata source and its format of the underlying record to the instance record. "
    + "(e.g. FOLIO if it's a record created in Inventory;  MARC if it's a MARC record created "
    + "in MARCcat or EPKB if it's a record coming from eHoldings)")
  @NotNull
  private String source;
  /**
   * The primary title (or label) associated with the resource.
   * (Required)
   */
  @JsonProperty("title")
  @JsonPropertyDescription("The primary title (or label) associated with the resource")
  @NotNull
  private String title;
  /**
   * Title normalized for browsing and searching; based on the title with articles removed.
   */
  @JsonProperty("indexTitle")
  @JsonPropertyDescription("Title normalized for browsing and searching; based on the title with articles removed")
  private String indexTitle;
  /**
   * List of alternative titles for the resource (e.g. original language version title of a movie)
   */
  @JsonProperty("alternativeTitles")
  @JsonDeserialize(as = java.util.LinkedHashSet.class)
  @JsonPropertyDescription(
    "List of alternative titles for the resource (e.g. original language version title of a movie)")
  @Valid
  private Set<AlternativeTitle> alternativeTitles = new LinkedHashSet<>();
  /**
   * The edition statement, imprint and other publication source information.
   */
  @JsonProperty("editions")
  @JsonDeserialize(as = java.util.LinkedHashSet.class)
  @JsonPropertyDescription("The edition statement, imprint and other publication source information")
  @Valid
  private Set<String> editions = new LinkedHashSet<>();
  /**
   * List of series titles associated with the resource (e.g. Harry Potter)
   */
  @JsonProperty("series")
  @JsonDeserialize(as = java.util.LinkedHashSet.class)
  @JsonPropertyDescription("List of series titles associated with the resource (e.g. Harry Potter)")
  @Valid
  private Set<Series> series = new LinkedHashSet<>();
  /**
   * An extensible set of name-value pairs of identifiers associated with the resource.
   */
  @JsonProperty("identifiers")
  @JsonPropertyDescription("An extensible set of name-value pairs of identifiers associated with the resource")
  @Valid
  private List<Identifier> identifiers = new ArrayList<>();
  /**
   * List of contributors.
   */
  @JsonProperty("contributors")
  @JsonPropertyDescription("List of contributors")
  @Valid
  private List<Contributor> contributors = new ArrayList<>();
  /**
   * List of subject headings.
   */
  @JsonProperty("subjects")
  @JsonDeserialize(as = java.util.LinkedHashSet.class)
  @JsonPropertyDescription("List of subject headings")
  @Valid
  private Set<Subject> subjects = new LinkedHashSet<>();
  /**
   * List of classifications.
   */
  @JsonProperty("classifications")
  @JsonPropertyDescription("List of classifications")
  @Valid
  private List<Classification> classifications = new ArrayList<>();
  /**
   * List of publication items.
   */
  @JsonProperty("publication")
  @JsonPropertyDescription("List of publication items")
  @Valid
  private List<Publication> publication = new ArrayList<>();
  /**
   * List of intervals at which a serial appears (e.g. daily, weekly, monthly, quarterly, etc.)
   */
  @JsonProperty("publicationFrequency")
  @JsonDeserialize(as = java.util.LinkedHashSet.class)
  @JsonPropertyDescription("List of intervals at which a serial appears (e.g. daily, weekly, monthly, quarterly, etc.)")
  @Valid
  private Set<String> publicationFrequency = new LinkedHashSet<>();
  /**
   * The range of sequential designation/chronology of publication, or date range.
   */
  @JsonProperty("publicationRange")
  @JsonDeserialize(as = java.util.LinkedHashSet.class)
  @JsonPropertyDescription("The range of sequential designation/chronology of publication, or date range")
  @Valid
  private Set<String> publicationRange = new LinkedHashSet<>();
  /**
   * Publication period.
   */
  @JsonProperty("publicationPeriod")
  @JsonPropertyDescription("Publication period")
  @Valid
  private PublicationPeriod publicationPeriod;
  /**
   * List of electronic access items.
   */
  @JsonProperty("electronicAccess")
  @JsonPropertyDescription("List of electronic access items")
  @Valid
  private List<ElectronicAccess> electronicAccess = new ArrayList<>();
  /**
   * A universally unique identifier (UUID), this is a 128-bit number used to identify
   * a record and is shown in hex with dashes, for example 6312d172-f0cf-40f6-b27d-9fa8feaf332f;
   * the UUID version must be from 1-5; see https://dev.folio.org/guides/uuids/
   * (Required)
   */
  @JsonProperty("instanceTypeId")
  @JsonPropertyDescription("A universally unique identifier (UUID), this is a 128-bit number used to identify "
    + "a record and is shown in hex with dashes, for example 6312d172-f0cf-40f6-b27d-9fa8feaf332f; "
    + "the UUID version must be from 1-5; see https://dev.folio.org/guides/uuids/")
  @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
  @NotNull
  private String instanceTypeId;
  /**
   * UUIDs for the unique terms for the format whether it's from the RDA carrier term list of locally defined.
   */
  @JsonProperty("instanceFormatIds")
  @JsonPropertyDescription(
    "UUIDs for the unique terms for the format whether it's from the RDA carrier term list of locally defined")
  @Valid
  private List<String> instanceFormatIds = new ArrayList<>();
  /**
   * List of dereferenced instance formats.
   */
  @JsonProperty("instanceFormats")
  @JsonPropertyDescription("List of dereferenced instance formats")
  @Valid
  private List<InstanceFormat> instanceFormats = new ArrayList<>();
  /**
   * Physical description of the described resource, including its extent, dimensions,
   * and such other physical details as a description of any accompanying materials and unit type and size.
   */
  @JsonProperty("physicalDescriptions")
  @JsonPropertyDescription(
    "Physical description of the described resource, including its extent, dimensions, and such other "
      + "physical details as a description of any accompanying materials and unit type and size")
  @Valid
  private List<String> physicalDescriptions = new ArrayList<>();
  /**
   * The set of languages used by the resource.
   */
  @JsonProperty("languages")
  @JsonPropertyDescription("The set of languages used by the resource")
  @Valid
  private List<String> languages = new ArrayList<>();
  /**
   * Bibliographic notes (e.g. general notes, specialized notes)
   */
  @JsonProperty("notes")
  @JsonPropertyDescription("Bibliographic notes (e.g. general notes, specialized notes)")
  @Valid
  private List<InstanceNote> notes = new ArrayList<>();
  /**
   * Administrative notes.
   */
  @JsonProperty("administrativeNotes")
  @JsonPropertyDescription("Administrative notes")
  @Valid
  private List<String> administrativeNotes = new ArrayList<>();
  /**
   * A universally unique identifier (UUID), this is a 128-bit number used to identify a record and is
   * shown in hex with dashes, for example 6312d172-f0cf-40f6-b27d-9fa8feaf332f; the UUID version must be from 1-5;
   * see https://dev.folio.org/guides/uuids/
   */
  @JsonProperty("modeOfIssuanceId")
  @JsonPropertyDescription(
    "A universally unique identifier (UUID), this is a 128-bit number used to identify a record and is "
      + "shown in hex with dashes, for example 6312d172-f0cf-40f6-b27d-9fa8feaf332f; "
      + "the UUID version must be from 1-5; see https://dev.folio.org/guides/uuids/")
  @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
  private String modeOfIssuanceId;
  /**
   * Date or timestamp on an instance for when is was considered cataloged.
   */
  @JsonProperty("catalogedDate")
  @JsonPropertyDescription("Date or timestamp on an instance for when is was considered cataloged")
  private String catalogedDate;
  /**
   * Records the fact that the resource was previously held by the library for things like Hathi access, etc.
   */
  @JsonProperty("previouslyHeld")
  @JsonPropertyDescription(
    "Records the fact that the resource was previously held by the library for things like Hathi access, etc.")
  private Boolean previouslyHeld = false;
  /**
   * Records the fact that the record should not be displayed for others than catalogers.
   */
  @JsonProperty("staffSuppress")
  @JsonPropertyDescription("Records the fact that the record should not be displayed for others than catalogers")
  private Boolean staffSuppress;
  /**
   * Records the fact that the record should not be displayed in a discovery system.
   */
  @JsonProperty("discoverySuppress")
  @JsonPropertyDescription("Records the fact that the record should not be displayed in a discovery system")
  private Boolean discoverySuppress = false;
  /**
   * List of statistical code IDs.
   */
  @JsonProperty("statisticalCodeIds")
  @JsonDeserialize(as = java.util.LinkedHashSet.class)
  @JsonPropertyDescription("List of statistical code IDs")
  @Valid
  private Set<String> statisticalCodeIds = new LinkedHashSet<>();
  /**
   * Format of the instance source record, if a source record exists (e.g. FOLIO if it's a record created in Inventory,
   * MARC if it's a MARC record created in MARCcat or EPKB if it's a record coming from eHoldings)
   */
  @JsonProperty("sourceRecordFormat")
  @JsonPropertyDescription("Format of the instance source record, if a source record exists "
    + "(e.g. FOLIO if it's a record created in Inventory, MARC if it's a MARC record created in MARCcat "
    + "or EPKB if it's a record coming from eHoldings)")
  private Instance.SourceRecordFormat sourceRecordFormat;
  /**
   * A universally unique identifier (UUID), this is a 128-bit number used to identify a record and is shown in
   * hex with dashes, for example 6312d172-f0cf-40f6-b27d-9fa8feaf332f; the UUID version must be from 1-5;
   * see https://dev.folio.org/guides/uuids/
   */
  @JsonProperty("statusId")
  @JsonPropertyDescription(
    "A universally unique identifier (UUID), this is a 128-bit number used to identify a record and is shown in "
      + "hex with dashes, for example 6312d172-f0cf-40f6-b27d-9fa8feaf332f; the UUID version must be from 1-5; "
      + "see https://dev.folio.org/guides/uuids/")
  @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
  private String statusId;
  /**
   * Date [or timestamp] for when the instance status was updated.
   */
  @JsonProperty("statusUpdatedDate")
  @JsonPropertyDescription("Date [or timestamp] for when the instance status was updated")
  private String statusUpdatedDate;
  /**
   * List of simple tags that can be added to an object.
   */
  @JsonProperty("tags")
  @JsonPropertyDescription("List of simple tags that can be added to an object")
  @Valid
  private Tags tags;
  /**
   * Metadata about creation and changes to records, provided by the server (client should not provide).
   */
  @JsonProperty("metadata")
  @JsonPropertyDescription(
    "Metadata about creation and changes to records, provided by the server (client should not provide)")
  @Valid
  private Metadata metadata;
  /**
   * List of holdings records.
   */
  @JsonProperty("holdingsRecords2")
  @JsonPropertyDescription("List of holdings records")
  @Valid
  private List<HoldingsRecord> holdingsRecords2 = new ArrayList<>();
  /**
   * Array of UUID for the InstanceInternal nature of content (e.g. bibliography, biography, exhibition catalogue,
   * festschrift, newspaper, proceedings, research report, thesis or website)
   */
  @JsonProperty("natureOfContentTermIds")
  @JsonDeserialize(as = java.util.LinkedHashSet.class)
  @JsonPropertyDescription("Array of UUID for the InstanceInternal nature of content (e.g. bibliography, biography, "
    + "exhibition catalogue, festschrift, newspaper, proceedings, research report, thesis or website)")
  @Valid
  private Set<String> natureOfContentTermIds = new LinkedHashSet<>();

  /**
   * A universally unique identifier (UUID), this is a 128-bit number used to identify a record and
   * is shown in hex with dashes, for example 6312d172-f0cf-40f6-b27d-9fa8feaf332f; the UUID version must be from 1-5;
   * see https://dev.folio.org/guides/uuids/
   */
  @JsonProperty("id")
  public String getId() {
    return id;
  }

  /**
   * A universally unique identifier (UUID), this is a 128-bit number used to identify a record and
   * is shown in hex with dashes, for example 6312d172-f0cf-40f6-b27d-9fa8feaf332f; the UUID version must be from 1-5;
   * see https://dev.folio.org/guides/uuids/
   */
  @JsonProperty("id")
  public void setId(String id) {
    this.id = id;
  }

  /**
   * Record version for optimistic locking.
   */
  @JsonProperty("_version")
  public Integer getVersion() {
    return version;
  }

  /**
   * Record version for optimistic locking.
   */
  @JsonProperty("_version")
  public void setVersion(Integer version) {
    this.version = version;
  }

  /**
   * The human readable ID, also called eye readable ID. A system-assigned sequential ID which maps to the
   * InstanceInternal ID.
   */
  @JsonProperty("hrid")
  public String getHrid() {
    return hrid;
  }

  /**
   * The human readable ID, also called eye readable ID. A system-assigned sequential ID which maps to the
   * InstanceInternal ID.
   */
  @JsonProperty("hrid")
  public void setHrid(String hrid) {
    this.hrid = hrid;
  }

  /**
   * A unique instance identifier matching a client-side bibliographic record identification scheme,
   * in particular for a scenario where multiple separate catalogs with no shared record identifiers
   * contribute to the same InstanceInternal in Inventory. A match key is typically generated from select,
   * normalized pieces of metadata in bibliographic records
   */
  @JsonProperty("matchKey")
  public String getMatchKey() {
    return matchKey;
  }

  /**
   * A unique instance identifier matching a client-side bibliographic record identification scheme,
   * in particular for a scenario where multiple separate catalogs with no shared record
   * identifiers contribute to the same InstanceInternal in Inventory. A match key is
   * typically generated from select, normalized pieces of metadata in bibliographic records
   */
  @JsonProperty("matchKey")
  public void setMatchKey(String matchKey) {
    this.matchKey = matchKey;
  }

  /**
   * The metadata source and its format of the underlying record to the instance record.
   * (e.g. FOLIO if it's a record created in Inventory;  MARC if it's a MARC record created in
   * MARCcat or EPKB if it's a record coming from eHoldings)
   * (Required)
   */
  @JsonProperty("source")
  public String getSource() {
    return source;
  }

  /**
   * The metadata source and its format of the underlying record to the instance record.
   * (e.g. FOLIO if it's a record created in Inventory;  MARC if it's a MARC record created
   * in MARCcat or EPKB if it's a record coming from eHoldings)
   * (Required)
   */
  @JsonProperty("source")
  public void setSource(String source) {
    this.source = source;
  }

  /**
   * The primary title (or label) associated with the resource.
   * (Required)
   */
  @JsonProperty("title")
  public String getTitle() {
    return title;
  }

  /**
   * The primary title (or label) associated with the resource.
   * (Required)
   */
  @JsonProperty("title")
  public void setTitle(String title) {
    this.title = title;
  }

  /**
   * Title normalized for browsing and searching; based on the title with articles removed..
   */
  @JsonProperty("indexTitle")
  public String getIndexTitle() {
    return indexTitle;
  }

  /**
   * Title normalized for browsing and searching; based on the title with articles removed.
   */
  @JsonProperty("indexTitle")
  public void setIndexTitle(String indexTitle) {
    this.indexTitle = indexTitle;
  }

  /**
   * List of alternative titles for the resource (e.g. original language version title of a movie)
   */
  @JsonProperty("alternativeTitles")
  public Set<AlternativeTitle> getAlternativeTitles() {
    return alternativeTitles;
  }

  /**
   * List of alternative titles for the resource (e.g. original language version title of a movie)
   */
  @JsonProperty("alternativeTitles")
  public void setAlternativeTitles(Set<AlternativeTitle> alternativeTitles) {
    this.alternativeTitles = alternativeTitles;
  }

  /**
   * The edition statement, imprint and other publication source information.
   */
  @JsonProperty("editions")
  public Set<String> getEditions() {
    return editions;
  }

  /**
   * The edition statement, imprint and other publication source information.
   */
  @JsonProperty("editions")
  public void setEditions(Set<String> editions) {
    this.editions = editions;
  }

  /**
   * List of series titles associated with the resource (e.g. Harry Potter)
   */
  @JsonProperty("series")
  public Set<Series> getSeries() {
    return series;
  }

  /**
   * List of series titles associated with the resource (e.g. Harry Potter)
   */
  @JsonProperty("series")
  public void setSeries(Set<Object> series) {
    this.series = toSeries(series);
  }

  /**
   * An extensible set of name-value pairs of identifiers associated with the resource.
   */
  @JsonProperty("identifiers")
  public List<Identifier> getIdentifiers() {
    return identifiers;
  }

  /**
   * An extensible set of name-value pairs of identifiers associated with the resource.
   */
  @JsonProperty("identifiers")
  public void setIdentifiers(List<Identifier> identifiers) {
    this.identifiers = identifiers;
  }

  /**
   * List of contributors.
   */
  @JsonProperty("contributors")
  public List<Contributor> getContributors() {
    return contributors;
  }

  /**
   * List of contributors.
   */
  @JsonProperty("contributors")
  public void setContributors(List<Contributor> contributors) {
    this.contributors = contributors;
  }

  /**
   * List of subject headings.
   */
  @JsonProperty("subjects")
  public Set<Subject> getSubjects() {
    return subjects;
  }

  /**
   * List of subject headings.
   */
  @JsonProperty("subjects")
  public void setSubjects(Set<Object> subjects) {
    this.subjects = toSubjects(subjects);
  }

  /**
   * List of classifications.
   */
  @JsonProperty("classifications")
  public List<Classification> getClassifications() {
    return classifications;
  }

  /**
   * List of classifications.
   */
  @JsonProperty("classifications")
  public void setClassifications(List<Classification> classifications) {
    this.classifications = classifications;
  }

  /**
   * List of publication items.
   */
  @JsonProperty("publication")
  public List<Publication> getPublication() {
    return publication;
  }

  /**
   * List of publication items.
   */
  @JsonProperty("publication")
  public void setPublication(List<Publication> publication) {
    this.publication = publication;
  }

  /**
   * List of intervals at which a serial appears (e.g. daily, weekly, monthly, quarterly, etc.)
   */
  @JsonProperty("publicationFrequency")
  public Set<String> getPublicationFrequency() {
    return publicationFrequency;
  }

  /**
   * List of intervals at which a serial appears (e.g. daily, weekly, monthly, quarterly, etc.)
   */
  @JsonProperty("publicationFrequency")
  public void setPublicationFrequency(Set<String> publicationFrequency) {
    this.publicationFrequency = publicationFrequency;
  }

  /**
   * The range of sequential designation/chronology of publication, or date range.
   */
  @JsonProperty("publicationRange")
  public Set<String> getPublicationRange() {
    return publicationRange;
  }

  /**
   * The range of sequential designation/chronology of publication, or date range.
   */
  @JsonProperty("publicationRange")
  public void setPublicationRange(Set<String> publicationRange) {
    this.publicationRange = publicationRange;
  }

  /**
   * Publication period.
   */
  @JsonProperty("publicationPeriod")
  public PublicationPeriod getPublicationPeriod() {
    return publicationPeriod;
  }

  /**
   * Publication period.
   */
  @JsonProperty("publicationPeriod")
  public void setPublicationPeriod(PublicationPeriod publicationPeriod) {
    this.publicationPeriod = publicationPeriod;
  }

  /**
   * List of electronic access items.
   */
  @JsonProperty("electronicAccess")
  public List<ElectronicAccess> getElectronicAccess() {
    return electronicAccess;
  }

  /**
   * List of electronic access items.
   */
  @JsonProperty("electronicAccess")
  public void setElectronicAccess(List<ElectronicAccess> electronicAccess) {
    this.electronicAccess = electronicAccess;
  }

  /**
   * A universally unique identifier (UUID), this is a 128-bit number used to identify a record and
   * is shown in hex with dashes, for example 6312d172-f0cf-40f6-b27d-9fa8feaf332f;
   * the UUID version must be from 1-5; see https://dev.folio.org/guides/uuids/
   * (Required)
   */
  @JsonProperty("instanceTypeId")
  public String getInstanceTypeId() {
    return instanceTypeId;
  }

  /**
   * A universally unique identifier (UUID), this is a 128-bit number used to identify
   * a record and is shown in hex with dashes, for example 6312d172-f0cf-40f6-b27d-9fa8feaf332f;
   * the UUID version must be from 1-5; see https://dev.folio.org/guides/uuids/
   * (Required)
   */
  @JsonProperty("instanceTypeId")
  public void setInstanceTypeId(String instanceTypeId) {
    this.instanceTypeId = instanceTypeId;
  }

  /**
   * UUIDs for the unique terms for the format whether it's from the RDA carrier term list of locally defined.
   */
  @JsonProperty("instanceFormatIds")
  public List<String> getInstanceFormatIds() {
    return instanceFormatIds;
  }

  /**
   * UUIDs for the unique terms for the format whether it's from the RDA carrier term list of locally defined.
   */
  @JsonProperty("instanceFormatIds")
  public void setInstanceFormatIds(List<String> instanceFormatIds) {
    this.instanceFormatIds = instanceFormatIds;
  }

  /**
   * List of dereferenced instance formats.
   */
  @JsonProperty("instanceFormats")
  public List<InstanceFormat> getInstanceFormats() {
    return instanceFormats;
  }

  /**
   * List of dereferenced instance formats.
   */
  @JsonProperty("instanceFormats")
  public void setInstanceFormats(List<InstanceFormat> instanceFormats) {
    this.instanceFormats = instanceFormats;
  }

  /**
   * Physical description of the described resource, including its extent, dimensions, and such other physical
   * details as a description of any accompanying materials and unit type and size.
   */
  @JsonProperty("physicalDescriptions")
  public List<String> getPhysicalDescriptions() {
    return physicalDescriptions;
  }

  /**
   * Physical description of the described resource, including its extent, dimensions, and such other
   * physical details as a description of any accompanying materials and unit type and size.
   */
  @JsonProperty("physicalDescriptions")
  public void setPhysicalDescriptions(List<String> physicalDescriptions) {
    this.physicalDescriptions = physicalDescriptions;
  }

  /**
   * The set of languages used by the resource.
   */
  @JsonProperty("languages")
  public List<String> getLanguages() {
    return languages;
  }

  /**
   * The set of languages used by the resource.
   */
  @JsonProperty("languages")
  public void setLanguages(List<String> languages) {
    this.languages = languages;
  }

  /**
   * Bibliographic notes (e.g. general notes, specialized notes)
   */
  @JsonProperty("notes")
  public List<InstanceNote> getNotes() {
    return notes;
  }

  /**
   * Bibliographic notes (e.g. general notes, specialized notes)
   */
  @JsonProperty("notes")
  public void setNotes(List<InstanceNote> notes) {
    this.notes = notes;
  }

  /**
   * Administrative notes.
   */
  @JsonProperty("administrativeNotes")
  public List<String> getAdministrativeNotes() {
    return administrativeNotes;
  }

  /**
   * Administrative notes.
   */
  @JsonProperty("administrativeNotes")
  public void setAdministrativeNotes(List<String> administrativeNotes) {
    this.administrativeNotes = administrativeNotes;
  }

  /**
   * A universally unique identifier (UUID), this is a 128-bit number used to identify a record and
   * is shown in hex with dashes, for example 6312d172-f0cf-40f6-b27d-9fa8feaf332f; the UUID version must be from 1-5;
   * see https://dev.folio.org/guides/uuids/
   */
  @JsonProperty("modeOfIssuanceId")
  public String getModeOfIssuanceId() {
    return modeOfIssuanceId;
  }

  /**
   * A universally unique identifier (UUID), this is a 128-bit number used to identify a record and is shown
   * in hex with dashes, for example 6312d172-f0cf-40f6-b27d-9fa8feaf332f; the UUID version must be from 1-5;
   * see https://dev.folio.org/guides/uuids/
   */
  @JsonProperty("modeOfIssuanceId")
  public void setModeOfIssuanceId(String modeOfIssuanceId) {
    this.modeOfIssuanceId = modeOfIssuanceId;
  }

  /**
   * Date or timestamp on an instance for when was considered cataloged.
   */
  @JsonProperty("catalogedDate")
  public String getCatalogedDate() {
    return catalogedDate;
  }

  /**
   * Date or timestamp on an instance for when was considered cataloged.
   */
  @JsonProperty("catalogedDate")
  public void setCatalogedDate(String catalogedDate) {
    this.catalogedDate = catalogedDate;
  }

  /**
   * Records the fact that the resource was previously held by the library for things like Hathi access, etc.
   */
  @JsonProperty("previouslyHeld")
  public Boolean getPreviouslyHeld() {
    return previouslyHeld;
  }

  /**
   * Records the fact that the resource was previously held by the library for things like Hathi access, etc.
   */
  @JsonProperty("previouslyHeld")
  public void setPreviouslyHeld(Boolean previouslyHeld) {
    this.previouslyHeld = previouslyHeld;
  }

  /**
   * Records the fact that the record should not be displayed for others than catalogers.
   */
  @JsonProperty("staffSuppress")
  public Boolean getStaffSuppress() {
    return staffSuppress;
  }

  /**
   * Records the fact that the record should not be displayed for others than catalogers.
   */
  @JsonProperty("staffSuppress")
  public void setStaffSuppress(Boolean staffSuppress) {
    this.staffSuppress = staffSuppress;
  }

  /**
   * Records the fact that the record should not be displayed in a discovery system.
   */
  @JsonProperty("discoverySuppress")
  public Boolean getDiscoverySuppress() {
    return discoverySuppress;
  }

  /**
   * Records the fact that the record should not be displayed in a discovery system.
   */
  @JsonProperty("discoverySuppress")
  public void setDiscoverySuppress(Boolean discoverySuppress) {
    this.discoverySuppress = discoverySuppress;
  }

  /**
   * List of statistical code IDs.
   */
  @JsonProperty("statisticalCodeIds")
  public Set<String> getStatisticalCodeIds() {
    return statisticalCodeIds;
  }

  /**
   * List of statistical code IDs.
   */
  @JsonProperty("statisticalCodeIds")
  public void setStatisticalCodeIds(Set<String> statisticalCodeIds) {
    this.statisticalCodeIds = statisticalCodeIds;
  }

  /**
   * Format of the instance source record, if a source record exists
   * (e.g. FOLIO if it's a record created in Inventory,  MARC if it's a MARC record created in
   * MARCcat or EPKB if it's a record coming from eHoldings)
   */
  @JsonProperty("sourceRecordFormat")
  public Instance.SourceRecordFormat getSourceRecordFormat() {
    return sourceRecordFormat;
  }

  /**
   * Format of the instance source record, if a source record exists
   * (e.g. FOLIO if it's a record created in Inventory,  MARC if it's a MARC record created in
   * MARCcat or EPKB if it's a record coming from eHoldings)
   */
  @JsonProperty("sourceRecordFormat")
  public void setSourceRecordFormat(Instance.SourceRecordFormat sourceRecordFormat) {
    this.sourceRecordFormat = sourceRecordFormat;
  }

  /**
   * A universally unique identifier (UUID), this is a 128-bit number used to identify a record
   * and is shown in hex with dashes, for example 6312d172-f0cf-40f6-b27d-9fa8feaf332f;
   * the UUID version must be from 1-5; see https://dev.folio.org/guides/uuids/
   */
  @JsonProperty("statusId")
  public String getStatusId() {
    return statusId;
  }

  /**
   * A universally unique identifier (UUID), this is a 128-bit number used to identify a record
   * and is shown in hex with dashes, for example 6312d172-f0cf-40f6-b27d-9fa8feaf332f;
   * the UUID version must be from 1-5; see https://dev.folio.org/guides/uuids/
   */
  @JsonProperty("statusId")
  public void setStatusId(String statusId) {
    this.statusId = statusId;
  }

  /**
   * Date [or timestamp] for when the instance status was updated.
   */
  @JsonProperty("statusUpdatedDate")
  public String getStatusUpdatedDate() {
    return statusUpdatedDate;
  }

  /**
   * Date [or timestamp] for when the instance status was updated.
   */
  @JsonProperty("statusUpdatedDate")
  public void setStatusUpdatedDate(String statusUpdatedDate) {
    this.statusUpdatedDate = statusUpdatedDate;
  }

  /**
   * List of simple tags that can be added to an object.
   */
  @JsonProperty("tags")
  public Tags getTags() {
    return tags;
  }

  /**
   * List of simple tags that can be added to an object.
   */
  @JsonProperty("tags")
  public void setTags(Tags tags) {
    this.tags = tags;
  }

  /**
   * Metadata about creation and changes to records, provided by the server (client should not provide).
   */
  @JsonProperty("metadata")
  public Metadata getMetadata() {
    return metadata;
  }

  /**
   * Metadata about creation and changes to records, provided by the server (client should not provide).
   */
  @JsonProperty("metadata")
  public void setMetadata(Metadata metadata) {
    this.metadata = metadata;
  }

  /**
   * List of holdings records.
   */
  @JsonProperty("holdingsRecords2")
  public List<HoldingsRecord> getHoldingsRecords2() {
    return holdingsRecords2;
  }

  /**
   * List of holdings records.
   */
  @JsonProperty("holdingsRecords2")
  public void setHoldingsRecords2(List<HoldingsRecord> holdingsRecords2) {
    this.holdingsRecords2 = holdingsRecords2;
  }

  /**
   * Array of UUID for the InstanceInternal nature of content
   * (e.g. bibliography, biography, exhibition catalogue, festschrift, newspaper, proceedings,
   * research report, thesis or website)
   */
  @JsonProperty("natureOfContentTermIds")
  public Set<String> getNatureOfContentTermIds() {
    return natureOfContentTermIds;
  }

  /**
   * Array of UUID for the InstanceInternal nature of content (e.g. bibliography,
   * biography, exhibition catalogue, festschrift, newspaper, proceedings, research report, thesis or website)
   */
  @JsonProperty("natureOfContentTermIds")
  public void setNatureOfContentTermIds(Set<String> natureOfContentTermIds) {
    this.natureOfContentTermIds = natureOfContentTermIds;
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder().append(metadata)
      .append(notes)
      .append(previouslyHeld)
      .append(instanceFormats)
      .append(modeOfIssuanceId)
      .append(catalogedDate)
      .append(source)
      .append(title)
      .append(indexTitle)
      .append(publicationFrequency)
      .append(electronicAccess)
      .append(statisticalCodeIds)
      .append(statusUpdatedDate)
      .append(natureOfContentTermIds)
      .append(hrid)
      .append(instanceFormatIds)
      .append(publication)
      .append(sourceRecordFormat)
      .append(publicationPeriod)
      .append(id)
      .append(alternativeTitles)
      .append(physicalDescriptions)
      .append(languages)
      .append(identifiers)
      .append(instanceTypeId)
      .append(subjects)
      .append(holdingsRecords2)
      .append(matchKey)
      .append(version)
      .append(tags)
      .append(classifications)
      .append(publicationRange)
      .append(editions)
      .append(discoverySuppress)
      .append(statusId)
      .append(series)
      .append(staffSuppress)
      .append(contributors)
      .append(administrativeNotes)
      .toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof InstanceInternal)) {
      return false;
    }
    InstanceInternal rhs = (InstanceInternal) other;
    return new EqualsBuilder().append(metadata, rhs.metadata)
      .append(notes, rhs.notes)
      .append(previouslyHeld, rhs.previouslyHeld)
      .append(instanceFormats, rhs.instanceFormats)
      .append(modeOfIssuanceId, rhs.modeOfIssuanceId)
      .append(catalogedDate, rhs.catalogedDate)
      .append(source, rhs.source)
      .append(title, rhs.title)
      .append(indexTitle, rhs.indexTitle)
      .append(publicationFrequency, rhs.publicationFrequency)
      .append(electronicAccess, rhs.electronicAccess)
      .append(statisticalCodeIds, rhs.statisticalCodeIds)
      .append(statusUpdatedDate, rhs.statusUpdatedDate)
      .append(natureOfContentTermIds, rhs.natureOfContentTermIds)
      .append(hrid, rhs.hrid)
      .append(instanceFormatIds, rhs.instanceFormatIds)
      .append(publication, rhs.publication)
      .append(sourceRecordFormat, rhs.sourceRecordFormat)
      .append(publicationPeriod, rhs.publicationPeriod)
      .append(id, rhs.id)
      .append(alternativeTitles, rhs.alternativeTitles)
      .append(physicalDescriptions, rhs.physicalDescriptions)
      .append(languages, rhs.languages)
      .append(identifiers, rhs.identifiers)
      .append(instanceTypeId, rhs.instanceTypeId)
      .append(subjects, rhs.subjects)
      .append(holdingsRecords2, rhs.holdingsRecords2)
      .append(matchKey, rhs.matchKey)
      .append(version, rhs.version)
      .append(tags, rhs.tags)
      .append(classifications, rhs.classifications)
      .append(publicationRange, rhs.publicationRange)
      .append(editions, rhs.editions)
      .append(discoverySuppress, rhs.discoverySuppress)
      .append(statusId, rhs.statusId)
      .append(series, rhs.series)
      .append(staffSuppress, rhs.staffSuppress)
      .append(contributors, rhs.contributors)
      .append(administrativeNotes, rhs.administrativeNotes)
      .isEquals();
  }

  public Instance toInstanceDto() {
    return new Instance().withMetadata(metadata)
      .withNotes(notes)
      .withPreviouslyHeld(previouslyHeld)
      .withInstanceFormats(instanceFormats)
      .withModeOfIssuanceId(modeOfIssuanceId)
      .withCatalogedDate(catalogedDate)
      .withSource(source)
      .withTitle(title)
      .withIndexTitle(indexTitle)
      .withPublicationFrequency(publicationFrequency)
      .withElectronicAccess(electronicAccess)
      .withStatisticalCodeIds(statisticalCodeIds)
      .withStatusUpdatedDate(statusUpdatedDate)
      .withNatureOfContentTermIds(natureOfContentTermIds)
      .withHrid(hrid)
      .withInstanceFormatIds(instanceFormatIds)
      .withPublication(publication)
      .withSourceRecordFormat(sourceRecordFormat)
      .withPublicationPeriod(publicationPeriod)
      .withId(id)
      .withAlternativeTitles(alternativeTitles)
      .withPhysicalDescriptions(physicalDescriptions)
      .withLanguages(languages)
      .withIdentifiers(identifiers)
      .withInstanceTypeId(instanceTypeId)
      .withHoldingsRecords2(holdingsRecords2)
      .withMatchKey(matchKey)
      .withVersion(version)
      .withTags(tags)
      .withClassifications(classifications)
      .withPublicationRange(publicationRange)
      .withEditions(editions)
      .withDiscoverySuppress(discoverySuppress)
      .withStatusId(statusId)
      .withSeries(series)
      .withSubjects(subjects)
      .withStaffSuppress(staffSuppress)
      .withContributors(contributors)
      .withAdministrativeNotes(administrativeNotes);
  }

  private Set<Subject> toSubjects(Set<Object> rawSubjects) {
    Set<Subject> subjectSet = new LinkedHashSet<>();
    for (Object subject : rawSubjects) {
      if (subject instanceof String) {
        subjectSet.add(new Subject().withValue(subject.toString()));
      } else if (subject instanceof Map) {
        @SuppressWarnings("unchecked") var map = (Map<String, String>) subject;
        subjectSet.add(new Subject().withValue(map.get("value")).withAuthorityId(map.get("authorityId")));
      }
    }
    return subjectSet;
  }

  private Set<Series> toSeries(Set<Object> rawSeries) {
    Set<Series> seriesSet = new LinkedHashSet<>();
    for (Object seriesItem : rawSeries) {
      if (seriesItem instanceof String) {
        seriesSet.add(new Series().withValue(seriesItem.toString()));
      } else if (seriesItem instanceof Map) {
        @SuppressWarnings("unchecked") var map = (Map<String, String>) seriesItem;
        seriesSet.add(new Series().withValue(map.get("value")).withAuthorityId(map.get("authorityId")));
      }
    }
    return seriesSet;
  }

}
