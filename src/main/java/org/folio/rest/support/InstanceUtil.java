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

public final class InstanceUtil {

  private InstanceUtil() {
  }

  public static Instance mapInstanceDtoJsonToInstance(JsonObject instanceDtoJson) {
    return new Instance()
      .withId(instanceDtoJson.getString("id"))
      .withVersion(Integer.parseInt(instanceDtoJson.getString("_version")))
      .withHrid(instanceDtoJson.getString("hrid"))
      .withSource(instanceDtoJson.getString("source"))
      .withMatchKey(instanceDtoJson.getString("matchKey"))
      .withTitle(instanceDtoJson.getString("title"))
      .withIndexTitle(instanceDtoJson.getString("indexTitle"))
      .withAlternativeTitles(toSetOfObjects(
        instanceDtoJson.getJsonArray("alternativeTitles"), InstanceUtil::mapJsonObjectToAlternativeTitle))
      .withEditions(new LinkedHashSet<>(toListOfStrings(instanceDtoJson.getJsonArray("editions"))))
      .withSeries(toSetOfObjects(instanceDtoJson.getJsonArray("series"), InstanceUtil::mapJsonObjectToSeries))
      .withIdentifiers(
        toListOfObjects(instanceDtoJson.getJsonArray("identifiers"), InstanceUtil::mapJsonObjectToIdentifier))
      .withContributors(
        toListOfObjects(instanceDtoJson.getJsonArray("contributors"), InstanceUtil::mapJsonObjectToContributor))
      .withSubjects(toSetOfObjects(instanceDtoJson.getJsonArray("subjects"), InstanceUtil::mapJsonObjectToSubject))
      .withClassifications(toListOfObjects(
        instanceDtoJson.getJsonArray("classifications"), InstanceUtil::mapJsonObjectToClassification))
      .withPublication(
        toListOfObjects(instanceDtoJson.getJsonArray("publication"), InstanceUtil::mapJsonObjectToPublication))
      .withPublicationFrequency(new LinkedHashSet<>(toListOfStrings(instanceDtoJson.getJsonArray("publicationFrequency"))))
      .withPublicationRange(new LinkedHashSet<>(toListOfStrings(instanceDtoJson.getJsonArray("publicationRange"))))
      .withPublicationPeriod(mapJsonObjectToPublicationPeriod(instanceDtoJson.getJsonObject("publicationPeriod")))
      .withElectronicAccess(toListOfObjects(
        instanceDtoJson.getJsonArray("electronicAccess"), InstanceUtil::mapJsonObjectToElectronicAccess))
      .withInstanceTypeId(instanceDtoJson.getString("instanceTypeId"))
      .withInstanceFormatIds(toListOfStrings(instanceDtoJson.getJsonArray("instanceFormatIds")))
      .withPhysicalDescriptions(toListOfStrings(instanceDtoJson.getJsonArray("physicalDescriptions")))
      .withLanguages(toListOfStrings(instanceDtoJson.getJsonArray("languages")))
      .withNotes(toListOfObjects(instanceDtoJson.getJsonArray("notes"), InstanceUtil::mapJsonObjectToInstanceNote))
      .withAdministrativeNotes(toListOfStrings(instanceDtoJson.getJsonArray("administrativeNotes")))
      .withModeOfIssuanceId(instanceDtoJson.getString("modeOfIssuanceId"))
      .withCatalogedDate(instanceDtoJson.getString("catalogedDate"))
      .withPreviouslyHeld(instanceDtoJson.getBoolean("previouslyHeld"))
      .withStaffSuppress(instanceDtoJson.getBoolean("staffSuppress"))
      .withDiscoverySuppress(instanceDtoJson.getBoolean("discoverySuppress"))
      .withStatisticalCodeIds(new LinkedHashSet<>(toListOfStrings(instanceDtoJson.getJsonArray("statisticalCodeIds"))))
      .withSourceRecordFormat(mapStringToSourceRecordFormat(instanceDtoJson.getString("sourceRecordFormat")))
      .withStatusId(instanceDtoJson.getString("statusId"))
      .withStatusUpdatedDate(instanceDtoJson.getString("statusUpdatedDate"))
      .withTags(mapJsonObjectToTags(instanceDtoJson.getJsonObject("tags")))
      .withNatureOfContentTermIds(new LinkedHashSet<>(toListOfStrings(instanceDtoJson.getJsonArray("natureOfContentTermIds"))));
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

  private static <T> Set<T> toSetOfObjects(JsonArray array, Function<JsonObject, T> objectMapper) {
    if (array == null) {
      return new LinkedHashSet<>();
    }

    return array.stream()
      .map(JsonObject.class::cast)
      .map(objectMapper)
      .collect(Collectors.toSet());
  }

  private static List<String> toListOfStrings(JsonArray array) {
    if (array == null) {
      return new ArrayList<>();
    }

    return IntStream.range(0, array.size())
      .mapToObj(array::getString)
      .collect(Collectors.toList());
  }

  private static AlternativeTitle mapJsonObjectToAlternativeTitle(JsonObject json) {
    return new AlternativeTitle()
      .withAlternativeTitleTypeId(json.getString("alternativeTitleTypeId"))
      .withAlternativeTitle(json.getString("alternativeTitle"))
      .withAuthorityId(json.getString("authorityId"));
  }

  private static Series mapJsonObjectToSeries(JsonObject json) {
    return new Series()
      .withValue(json.getString("value"))
      .withAuthorityId(json.getString("authorityId"));
  }

  private static Identifier mapJsonObjectToIdentifier(JsonObject json) {
    return new Identifier()
      .withValue(json.getString("value"))
      .withIdentifierTypeId(json.getString("identifierTypeId"));
  }

  private static Contributor mapJsonObjectToContributor(JsonObject json) {
    return new Contributor()
      .withName(json.getString("name"))
      .withContributorTypeId(json.getString("contributorTypeId"))
      .withContributorTypeText(json.getString("contributorTypeText"))
      .withContributorNameTypeId(json.getString("contributorNameTypeId"))
      .withAuthorityId(json.getString("authorityId"))
      .withPrimary(json.getBoolean("primary"));
  }

  private static Subject mapJsonObjectToSubject(JsonObject json) {
    return new Subject()
      .withValue(json.getString("value"))
      .withAuthorityId(json.getString("authorityId"));
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

}
