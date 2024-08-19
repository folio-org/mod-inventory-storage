package org.folio.rest.support;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.folio.rest.jaxrs.model.AlternativeTitle;
import org.folio.rest.jaxrs.model.Classification;
import org.folio.rest.jaxrs.model.Contributor;
import org.folio.rest.jaxrs.model.ElectronicAccess;
import org.folio.rest.jaxrs.model.Identifier;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.Instance.SourceRecordFormat;
import org.folio.rest.jaxrs.model.InstanceNote;
import org.folio.rest.jaxrs.model.Publication;
import org.folio.rest.jaxrs.model.PublicationPeriod;
import org.folio.rest.jaxrs.model.Series;
import org.folio.rest.jaxrs.model.Subject;
import org.folio.rest.jaxrs.model.Tags;

/**
 * Class that provides utility methods for work with instance.
 */
public final class InstanceUtil {

  public static final String ID_FIELD = "id";
  public static final String VERSION_FIELD = "_version";
  public static final String HRID_FIELD = "hrid";
  public static final String SOURCE_FIELD = "source";
  public static final String MATCH_KEY_FIELD = "matchKey";
  public static final String TITLE_FIELD = "title";
  public static final String INDEX_TITLE_FIELD = "indexTitle";
  public static final String ALTERNATIVE_TITLES_FIELD = "alternativeTitles";
  public static final String EDITIONS_FIELD = "editions";
  public static final String SERIES_FIELD = "series";
  public static final String IDENTIFIERS_FIELD = "identifiers";
  public static final String CONTRIBUTORS_FIELD = "contributors";
  public static final String SUBJECTS_FIELD = "subjects";
  public static final String CLASSIFICATIONS_FIELD = "classifications";
  public static final String PUBLICATION_FIELD = "publication";
  public static final String PUBLICATION_FREQUENCY_FIELD = "publicationFrequency";
  public static final String PUBLICATION_RANGE_FIELD = "publicationRange";
  public static final String PUBLICATION_PERIOD_FIELD = "publicationPeriod";
  public static final String ELECTRONIC_ACCESS_FIELD = "electronicAccess";
  public static final String INSTANCE_TYPE_ID_FIELD = "instanceTypeId";
  public static final String INSTANCE_FORMAT_IDS_FIELD = "instanceFormatIds";
  public static final String PHYSICAL_DESCRIPTIONS_FIELD = "physicalDescriptions";
  public static final String LANGUAGES_FIELD = "languages";
  public static final String NOTES_FIELD = "notes";
  public static final String ADMINISTRATIVE_NOTES_FIELD = "administrativeNotes";
  public static final String MODE_OF_ISSUANCE_ID_FIELD = "modeOfIssuanceId";
  public static final String CATALOGED_DATE_FIELD = "catalogedDate";
  public static final String PREVIOUSLY_HELD_FIELD = "previouslyHeld";
  public static final String STAFF_SUPPRESS_FIELD = "staffSuppress";
  public static final String DISCOVERY_SUPPRESS_FIELD = "discoverySuppress";
  public static final String STATISTICAL_CODE_IDS_FIELD = "statisticalCodeIds";
  public static final String SOURCE_RECORD_FORMAT_FIELD = "sourceRecordFormat";
  public static final String STATUS_ID_FIELD = "statusId";
  public static final String STATUS_UPDATED_DATE_FIELD = "statusUpdatedDate";
  public static final String TAGS_FIELD = "tags";
  public static final String NATURE_OF_CONTENT_TERM_IDS_FIELD = "natureOfContentTermIds";
  public static final String AUTHORITY_ID_FIELD = "authorityId";
  public static final String VALUE_FIELD = "value";

  private InstanceUtil() {
    throw new UnsupportedOperationException("Cannot instantiate utility class");
  }

  /**
   * Maps instance json representation that corresponds to the mod-inventory module instance schema to instance object
   * corresponding to the inventory storage module schema.
   *
   * @param instanceDtoJson - mod-inventory instance representation as json object
   * @return {@link Instance} object
   */
  public static Instance mapInstanceDtoJsonToInstance(JsonObject instanceDtoJson) {
    return new Instance()
      .withId(instanceDtoJson.getString(ID_FIELD))
      .withVersion(instanceDtoJson.getInteger(VERSION_FIELD))
      .withHrid(instanceDtoJson.getString(HRID_FIELD))
      .withSource(instanceDtoJson.getString(SOURCE_FIELD))
      .withMatchKey(instanceDtoJson.getString(MATCH_KEY_FIELD))
      .withTitle(instanceDtoJson.getString(TITLE_FIELD))
      .withIndexTitle(instanceDtoJson.getString(INDEX_TITLE_FIELD))
      .withAlternativeTitles(toSetOfObjects(
        instanceDtoJson.getJsonArray(ALTERNATIVE_TITLES_FIELD), InstanceUtil::mapJsonObjectToAlternativeTitle))
      .withEditions(toSetOfStrings(instanceDtoJson.getJsonArray(EDITIONS_FIELD)))
      .withSeries(toSetOfObjects(instanceDtoJson.getJsonArray(SERIES_FIELD), InstanceUtil::mapJsonObjectToSeries))
      .withIdentifiers(
        toListOfObjects(instanceDtoJson.getJsonArray(IDENTIFIERS_FIELD), InstanceUtil::mapJsonObjectToIdentifier))
      .withContributors(
        toListOfObjects(instanceDtoJson.getJsonArray(CONTRIBUTORS_FIELD), InstanceUtil::mapJsonObjectToContributor))
      .withSubjects(toSetOfObjects(instanceDtoJson.getJsonArray(SUBJECTS_FIELD), InstanceUtil::mapJsonObjectToSubject))
      .withClassifications(toListOfObjects(
        instanceDtoJson.getJsonArray(CLASSIFICATIONS_FIELD), InstanceUtil::mapJsonObjectToClassification))
      .withPublication(
        toListOfObjects(instanceDtoJson.getJsonArray(PUBLICATION_FIELD), InstanceUtil::mapJsonObjectToPublication))
      .withPublicationFrequency(toSetOfStrings(instanceDtoJson.getJsonArray(PUBLICATION_FREQUENCY_FIELD)))
      .withPublicationRange(toSetOfStrings(instanceDtoJson.getJsonArray(PUBLICATION_RANGE_FIELD)))
      .withPublicationPeriod(mapJsonObjectToPublicationPeriod(instanceDtoJson.getJsonObject(PUBLICATION_PERIOD_FIELD)))
      .withElectronicAccess(toListOfObjects(
        instanceDtoJson.getJsonArray(ELECTRONIC_ACCESS_FIELD), InstanceUtil::mapJsonObjectToElectronicAccess))
      .withInstanceTypeId(instanceDtoJson.getString(INSTANCE_TYPE_ID_FIELD))
      .withInstanceFormatIds(toListOfStrings(instanceDtoJson.getJsonArray(INSTANCE_FORMAT_IDS_FIELD)))
      .withPhysicalDescriptions(toListOfStrings(instanceDtoJson.getJsonArray(PHYSICAL_DESCRIPTIONS_FIELD)))
      .withLanguages(toListOfStrings(instanceDtoJson.getJsonArray(LANGUAGES_FIELD)))
      .withNotes(toListOfObjects(instanceDtoJson.getJsonArray(NOTES_FIELD), InstanceUtil::mapJsonObjectToInstanceNote))
      .withAdministrativeNotes(toListOfStrings(instanceDtoJson.getJsonArray(ADMINISTRATIVE_NOTES_FIELD)))
      .withModeOfIssuanceId(instanceDtoJson.getString(MODE_OF_ISSUANCE_ID_FIELD))
      .withCatalogedDate(instanceDtoJson.getString(CATALOGED_DATE_FIELD))
      .withPreviouslyHeld(instanceDtoJson.getBoolean(PREVIOUSLY_HELD_FIELD))
      .withStaffSuppress(instanceDtoJson.getBoolean(STAFF_SUPPRESS_FIELD))
      .withDiscoverySuppress(instanceDtoJson.getBoolean(DISCOVERY_SUPPRESS_FIELD))
      .withStatisticalCodeIds(toSetOfStrings(instanceDtoJson.getJsonArray(STATISTICAL_CODE_IDS_FIELD)))
      .withSourceRecordFormat(mapStringToSourceRecordFormat(instanceDtoJson.getString(SOURCE_RECORD_FORMAT_FIELD)))
      .withStatusId(instanceDtoJson.getString(STATUS_ID_FIELD))
      .withStatusUpdatedDate(instanceDtoJson.getString(STATUS_UPDATED_DATE_FIELD))
      .withTags(mapJsonObjectToTags(instanceDtoJson.getJsonObject(TAGS_FIELD)))
      .withNatureOfContentTermIds(toSetOfStrings(instanceDtoJson.getJsonArray(NATURE_OF_CONTENT_TERM_IDS_FIELD)));
  }

  private static List<String> toListOfStrings(JsonArray array) {
    if (array == null) {
      return new ArrayList<>();
    }

    return IntStream.range(0, array.size())
      .mapToObj(array::getString)
      .toList();
  }

  private static <T> List<T> toListOfObjects(JsonArray array, Function<JsonObject, T> objectMapper) {
    if (array == null) {
      return new ArrayList<>();
    }

    return array.stream()
      .map(JsonObject.class::cast)
      .map(objectMapper)
      .toList();
  }

  private static Set<String> toSetOfStrings(JsonArray array) {
    if (array == null) {
      return new LinkedHashSet<>();
    }

    return IntStream.range(0, array.size())
      .mapToObj(array::getString)
      .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private static <T> Set<T> toSetOfObjects(JsonArray array, Function<JsonObject, T> objectMapper) {
    if (array == null) {
      return new LinkedHashSet<>();
    }

    return array.stream()
      .map(JsonObject.class::cast)
      .map(objectMapper)
      .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private static AlternativeTitle mapJsonObjectToAlternativeTitle(JsonObject json) {
    return new AlternativeTitle()
      .withAlternativeTitleTypeId(json.getString("alternativeTitleTypeId"))
      .withAlternativeTitle(json.getString("alternativeTitle"))
      .withAuthorityId(json.getString(AUTHORITY_ID_FIELD));
  }

  private static Series mapJsonObjectToSeries(JsonObject json) {
    return new Series()
      .withValue(json.getString(VALUE_FIELD))
      .withAuthorityId(json.getString(AUTHORITY_ID_FIELD));
  }

  private static Identifier mapJsonObjectToIdentifier(JsonObject json) {
    return new Identifier()
      .withValue(json.getString(VALUE_FIELD))
      .withIdentifierTypeId(json.getString("identifierTypeId"));
  }

  private static Contributor mapJsonObjectToContributor(JsonObject json) {
    return new Contributor()
      .withName(json.getString("name"))
      .withContributorTypeId(json.getString("contributorTypeId"))
      .withContributorTypeText(json.getString("contributorTypeText"))
      .withContributorNameTypeId(json.getString("contributorNameTypeId"))
      .withAuthorityId(json.getString(AUTHORITY_ID_FIELD))
      .withPrimary(json.getBoolean("primary"));
  }

  private static Subject mapJsonObjectToSubject(JsonObject json) {
    return new Subject()
      .withValue(json.getString(VALUE_FIELD))
      .withAuthorityId(json.getString(AUTHORITY_ID_FIELD));
  }

  private static Classification mapJsonObjectToClassification(JsonObject json) {
    return new Classification()
      .withClassificationNumber(json.getString("classificationNumber"))
      .withClassificationTypeId(json.getString("classificationTypeId"));
  }

  private static Publication mapJsonObjectToPublication(JsonObject json) {
    return new Publication()
      .withPublisher(json.getString("publisher"))
      .withPlace(json.getString("place"))
      .withDateOfPublication(json.getString("dateOfPublication"))
      .withRole(json.getString("role"));
  }

  private static PublicationPeriod mapJsonObjectToPublicationPeriod(JsonObject json) {
    return json == null ? null : new PublicationPeriod()
      .withStart(json.getInteger("start"))
      .withEnd(json.getInteger("end"));
  }

  private static ElectronicAccess mapJsonObjectToElectronicAccess(JsonObject json) {
    return new ElectronicAccess()
      .withUri(json.getString("uri"))
      .withLinkText(json.getString("linkText"))
      .withMaterialsSpecification(json.getString("materialsSpecification"))
      .withPublicNote(json.getString("publicNote"))
      .withRelationshipId(json.getString("relationshipId"));
  }

  private static InstanceNote mapJsonObjectToInstanceNote(JsonObject json) {
    return new InstanceNote()
      .withInstanceNoteTypeId(json.getString("instanceNoteTypeId"))
      .withNote(json.getString("note"))
      .withStaffOnly(json.getBoolean("staffOnly"));
  }

  private static SourceRecordFormat mapStringToSourceRecordFormat(String value) {
    return value == null ? null : SourceRecordFormat.fromValue(value);
  }

  private static Tags mapJsonObjectToTags(JsonObject json) {
    return json == null ? null : new Tags()
      .withTagList(toListOfStrings(json.getJsonArray("tagList")));
  }

  /**
   * Copies fields that are not controlled by underlying MARC record from the specified {@code sourceInstance}
   * to the {@code targetInstance}.
   *
   * @param targetInstance - instance object that is populated with not controlled by MARC fields values
   * @param sourceInstance - instance object from which the field values will be copied
   */
  public static void copyNonMarcControlledFields(Instance targetInstance, Instance sourceInstance) {
    targetInstance.setStaffSuppress(sourceInstance.getStaffSuppress());
    targetInstance.setDiscoverySuppress(sourceInstance.getDiscoverySuppress());
    targetInstance.setCatalogedDate(sourceInstance.getCatalogedDate());
    targetInstance.setStatusId(sourceInstance.getStatusId());
    targetInstance.setStatusUpdatedDate(sourceInstance.getStatusUpdatedDate());
    targetInstance.setStatisticalCodeIds(sourceInstance.getStatisticalCodeIds());
    targetInstance.setAdministrativeNotes(sourceInstance.getAdministrativeNotes());
    targetInstance.setNatureOfContentTermIds(sourceInstance.getNatureOfContentTermIds());
    targetInstance.setTags(sourceInstance.getTags());
    targetInstance.setPreviouslyHeld(sourceInstance.getPreviouslyHeld());
  }

}
