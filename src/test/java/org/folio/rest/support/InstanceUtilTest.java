package org.folio.rest.support;

import static org.folio.rest.support.InstanceUtil.ADMINISTRATIVE_NOTES_FIELD;
import static org.folio.rest.support.InstanceUtil.ALTERNATIVE_TITLES_FIELD;
import static org.folio.rest.support.InstanceUtil.CATALOGED_DATE_FIELD;
import static org.folio.rest.support.InstanceUtil.CLASSIFICATIONS_FIELD;
import static org.folio.rest.support.InstanceUtil.CONTRIBUTORS_FIELD;
import static org.folio.rest.support.InstanceUtil.DISCOVERY_SUPPRESS_FIELD;
import static org.folio.rest.support.InstanceUtil.EDITIONS_FIELD;
import static org.folio.rest.support.InstanceUtil.ELECTRONIC_ACCESS_FIELD;
import static org.folio.rest.support.InstanceUtil.HRID_FIELD;
import static org.folio.rest.support.InstanceUtil.IDENTIFIERS_FIELD;
import static org.folio.rest.support.InstanceUtil.ID_FIELD;
import static org.folio.rest.support.InstanceUtil.INDEX_TITLE_FIELD;
import static org.folio.rest.support.InstanceUtil.INSTANCE_FORMAT_IDS_FIELD;
import static org.folio.rest.support.InstanceUtil.INSTANCE_TYPE_ID_FIELD;
import static org.folio.rest.support.InstanceUtil.LANGUAGES_FIELD;
import static org.folio.rest.support.InstanceUtil.MATCH_KEY_FIELD;
import static org.folio.rest.support.InstanceUtil.MODE_OF_ISSUANCE_ID_FIELD;
import static org.folio.rest.support.InstanceUtil.NATURE_OF_CONTENT_TERM_IDS_FIELD;
import static org.folio.rest.support.InstanceUtil.NOTES_FIELD;
import static org.folio.rest.support.InstanceUtil.PHYSICAL_DESCRIPTIONS_FIELD;
import static org.folio.rest.support.InstanceUtil.PREVIOUSLY_HELD_FIELD;
import static org.folio.rest.support.InstanceUtil.PUBLICATION_FIELD;
import static org.folio.rest.support.InstanceUtil.PUBLICATION_FREQUENCY_FIELD;
import static org.folio.rest.support.InstanceUtil.PUBLICATION_PERIOD_FIELD;
import static org.folio.rest.support.InstanceUtil.PUBLICATION_RANGE_FIELD;
import static org.folio.rest.support.InstanceUtil.SERIES_FIELD;
import static org.folio.rest.support.InstanceUtil.SOURCE_FIELD;
import static org.folio.rest.support.InstanceUtil.SOURCE_RECORD_FORMAT_FIELD;
import static org.folio.rest.support.InstanceUtil.STAFF_SUPPRESS_FIELD;
import static org.folio.rest.support.InstanceUtil.STATISTICAL_CODE_IDS_FIELD;
import static org.folio.rest.support.InstanceUtil.STATUS_ID_FIELD;
import static org.folio.rest.support.InstanceUtil.STATUS_UPDATED_DATE_FIELD;
import static org.folio.rest.support.InstanceUtil.SUBJECTS_FIELD;
import static org.folio.rest.support.InstanceUtil.TAGS_FIELD;
import static org.folio.rest.support.InstanceUtil.TITLE_FIELD;
import static org.folio.rest.support.InstanceUtil.VERSION_FIELD;
import static org.folio.rest.support.InstanceUtil.copyNonMarcControlledFields;
import static org.junit.Assert.assertEquals;

import io.vertx.core.json.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.folio.rest.jaxrs.model.Instance;
import org.junit.Test;

public class InstanceUtilTest {

  public static final String INSTANCE_JSON_PATH =
    "src/test/resources/instances/bulk/modInventoryInstanceRepresentation.json";

  @Test
  @SuppressWarnings("java:S5961")
  public void shouldMapInventoryInstanceJsonRepresentationToInstanceEntity() throws IOException {
    JsonObject instanceJson = new JsonObject(Files.readString(Path.of(INSTANCE_JSON_PATH)));

    JsonObject actualInstanceJson = JsonObject.mapFrom(InstanceUtil.mapInstanceDtoJsonToInstance(instanceJson));

    assertEquals(instanceJson.getString(ID_FIELD), actualInstanceJson.getString(ID_FIELD));
    assertEquals(instanceJson.getString(HRID_FIELD), actualInstanceJson.getString(HRID_FIELD));
    assertEquals(instanceJson.getInteger(VERSION_FIELD), actualInstanceJson.getInteger(VERSION_FIELD));
    assertEquals(instanceJson.getString(SOURCE_FIELD), actualInstanceJson.getString(SOURCE_FIELD));
    assertEquals(instanceJson.getString(MATCH_KEY_FIELD), actualInstanceJson.getString(MATCH_KEY_FIELD));
    assertEquals(instanceJson.getString(TITLE_FIELD), actualInstanceJson.getString(TITLE_FIELD));
    assertEquals(instanceJson.getString(INDEX_TITLE_FIELD), actualInstanceJson.getString(INDEX_TITLE_FIELD));
    assertEquals(
      instanceJson.getJsonArray(ALTERNATIVE_TITLES_FIELD), actualInstanceJson.getJsonArray(ALTERNATIVE_TITLES_FIELD));
    assertEquals(instanceJson.getJsonArray(EDITIONS_FIELD), actualInstanceJson.getJsonArray(EDITIONS_FIELD));
    assertEquals(instanceJson.getJsonArray(SERIES_FIELD), actualInstanceJson.getJsonArray(SERIES_FIELD));
    assertEquals(instanceJson.getJsonArray(IDENTIFIERS_FIELD), actualInstanceJson.getJsonArray(IDENTIFIERS_FIELD));
    assertEquals(instanceJson.getJsonArray(CONTRIBUTORS_FIELD), actualInstanceJson.getJsonArray(CONTRIBUTORS_FIELD));
    assertEquals(instanceJson.getJsonArray(SUBJECTS_FIELD), actualInstanceJson.getJsonArray(SUBJECTS_FIELD));
    assertEquals(
      instanceJson.getJsonArray(CLASSIFICATIONS_FIELD), actualInstanceJson.getJsonArray(CLASSIFICATIONS_FIELD));
    assertEquals(instanceJson.getJsonArray(PUBLICATION_FIELD), actualInstanceJson.getJsonArray(PUBLICATION_FIELD));
    assertEquals(instanceJson.getJsonArray(PUBLICATION_FREQUENCY_FIELD),
      actualInstanceJson.getJsonArray(PUBLICATION_FREQUENCY_FIELD));
    assertEquals(
      instanceJson.getJsonArray(PUBLICATION_RANGE_FIELD), actualInstanceJson.getJsonArray(PUBLICATION_RANGE_FIELD));
    assertEquals(
      instanceJson.getJsonObject(PUBLICATION_PERIOD_FIELD), actualInstanceJson.getJsonObject(PUBLICATION_PERIOD_FIELD));
    assertEquals(
      instanceJson.getJsonArray(ELECTRONIC_ACCESS_FIELD), actualInstanceJson.getJsonArray(ELECTRONIC_ACCESS_FIELD));
    assertEquals(instanceJson.getString(INSTANCE_TYPE_ID_FIELD), actualInstanceJson.getString(INSTANCE_TYPE_ID_FIELD));
    assertEquals(
      instanceJson.getJsonArray(INSTANCE_FORMAT_IDS_FIELD), actualInstanceJson.getJsonArray(INSTANCE_FORMAT_IDS_FIELD));
    assertEquals(instanceJson.getJsonArray(PHYSICAL_DESCRIPTIONS_FIELD),
      actualInstanceJson.getJsonArray(PHYSICAL_DESCRIPTIONS_FIELD));
    assertEquals(instanceJson.getJsonArray(LANGUAGES_FIELD), actualInstanceJson.getJsonArray(LANGUAGES_FIELD));
    assertEquals(instanceJson.getJsonArray(NOTES_FIELD), actualInstanceJson.getJsonArray(NOTES_FIELD));
    assertEquals(instanceJson.getJsonArray(ADMINISTRATIVE_NOTES_FIELD),
      actualInstanceJson.getJsonArray(ADMINISTRATIVE_NOTES_FIELD));
    assertEquals(
      instanceJson.getString(MODE_OF_ISSUANCE_ID_FIELD), actualInstanceJson.getString(MODE_OF_ISSUANCE_ID_FIELD));
    assertEquals(instanceJson.getString(CATALOGED_DATE_FIELD), actualInstanceJson.getString(CATALOGED_DATE_FIELD));
    assertEquals(instanceJson.getBoolean(PREVIOUSLY_HELD_FIELD), actualInstanceJson.getBoolean(PREVIOUSLY_HELD_FIELD));
    assertEquals(instanceJson.getBoolean(STAFF_SUPPRESS_FIELD), actualInstanceJson.getBoolean(STAFF_SUPPRESS_FIELD));
    assertEquals(
      instanceJson.getBoolean(DISCOVERY_SUPPRESS_FIELD), actualInstanceJson.getBoolean(DISCOVERY_SUPPRESS_FIELD));
    assertEquals(instanceJson.getJsonArray(STATISTICAL_CODE_IDS_FIELD),
      actualInstanceJson.getJsonArray(STATISTICAL_CODE_IDS_FIELD));
    assertEquals(
      instanceJson.getString(SOURCE_RECORD_FORMAT_FIELD), actualInstanceJson.getString(SOURCE_RECORD_FORMAT_FIELD));
    assertEquals(instanceJson.getString(STATUS_ID_FIELD), actualInstanceJson.getString(STATUS_ID_FIELD));
    assertEquals(
      instanceJson.getString(STATUS_UPDATED_DATE_FIELD), actualInstanceJson.getString(STATUS_UPDATED_DATE_FIELD));
    assertEquals(instanceJson.getJsonObject(TAGS_FIELD), actualInstanceJson.getJsonObject(TAGS_FIELD));
    assertEquals(instanceJson.getJsonArray(NATURE_OF_CONTENT_TERM_IDS_FIELD),
      actualInstanceJson.getJsonArray(NATURE_OF_CONTENT_TERM_IDS_FIELD));
  }

  @Test
  public void shouldPopulateFieldsNotControlledByMarc() {
    Instance targetInstance = new Instance();
    Instance sourceInstance = new Instance()
      .withDiscoverySuppress(Boolean.TRUE)
      .withStaffSuppress(Boolean.TRUE)
      .withPreviouslyHeld(Boolean.TRUE)
      .withCatalogedDate("1970-01-01")
      .withStatusId(UUID.randomUUID().toString())
      .withStatusUpdatedDate("1970-01-01T12:07:47.602+0000")
      .withStatisticalCodeIds(Set.of(UUID.randomUUID().toString()))
      .withAdministrativeNotes(List.of("test-note1", "test-note2"))
      .withNatureOfContentTermIds(Set.of(UUID.randomUUID().toString()));

    copyNonMarcControlledFields(targetInstance, sourceInstance);

    assertEquals(sourceInstance.getDiscoverySuppress(), targetInstance.getDiscoverySuppress());
    assertEquals(sourceInstance.getStaffSuppress(), targetInstance.getStaffSuppress());
    assertEquals(sourceInstance.getPreviouslyHeld(), targetInstance.getPreviouslyHeld());
    assertEquals(sourceInstance.getCatalogedDate(), targetInstance.getCatalogedDate());
    assertEquals(sourceInstance.getStatusId(), targetInstance.getStatusId());
    assertEquals(sourceInstance.getStatusUpdatedDate(), targetInstance.getStatusUpdatedDate());
    assertEquals(sourceInstance.getStatisticalCodeIds(), targetInstance.getStatisticalCodeIds());
    assertEquals(sourceInstance.getAdministrativeNotes(), targetInstance.getAdministrativeNotes());
    assertEquals(sourceInstance.getNatureOfContentTermIds(), targetInstance.getNatureOfContentTermIds());
  }

}
