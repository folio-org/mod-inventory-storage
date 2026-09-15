package org.folio.it.api.instance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.HttpStatus.SC_BAD_REQUEST;
import static org.folio.HttpStatus.SC_NO_CONTENT;
import static org.folio.HttpStatus.SC_UNPROCESSABLE_CONTENT;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;
import static org.folio.support.ResourcePaths.INSTANCES;
import static org.folio.validator.NotesValidators.MAX_NOTE_LENGTH;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.InstanceNote;
import org.folio.rest.jaxrs.model.Subject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InstanceStorageValidationIT extends InstanceStorageTestBase {

  @Test
  @DisplayName("should return 422 when the instance id is not a UUID")
  void shouldReturn422_whenInstanceIdIsNotUuid() {
    var instanceToCreate = new JsonObject()
      .put("id", "6556456")
      .put("source", "TEST")
      .put("title", "Long Way to a Small Angry Planet")
      .put("identifiers", new JsonArray().add(identifier(isbnTypeId, "9781473619777")))
      .put("contributors", new JsonArray().add(contributor(personalNameTypeId, "Chambers, Becky")))
      .put("instanceTypeId", instanceTypeId);

    var response = await(doPost(client, INSTANCES, instanceToCreate));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
    assertThat(response.body().toString()).contains("must match");
  }

  @Test
  @DisplayName("should return 400 when a statistical code id is invalid")
  void shouldReturn400_whenStatisticalCodeIdIsInvalid() {
    var instanceToCreate = smallAngryPlanet(null).put(STATISTICAL_CODE_IDS_KEY, Set.of(INVALID_VALUE));

    var response = await(doPost(client, INSTANCES, instanceToCreate));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString()).contains(INVALID_TYPE_ERROR_MESSAGE);
  }

  @Test
  @DisplayName("should return 400 when updating an instance with a subject id that does not exist")
  void shouldReturn400_whenUpdatingInstanceWithNotExistingSubjectId() {
    var instanceId = createInstance(smallAngryPlanet(null)).getString("id");
    var instanceFromGet = getById(instanceId).jsonBody();
    var subject = new Subject().withSourceId(UUID.randomUUID().toString()).withValue("subject");
    instanceFromGet.put(SUBJECTS_KEY, new JsonArray().add(pojo2JsonObject(subject)));

    var response = update(instanceFromGet);

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
  }

  @Test
  @DisplayName("should return 400 when creating an instance with a subject id that does not exist")
  void shouldReturn400_whenCreatingInstanceWithNotExistingSubjectId() {
    var subject = new Subject()
      .withSourceId(UUID.randomUUID().toString())
      .withTypeId(UUID.randomUUID().toString())
      .withValue("subject");
    var instanceToCreate = smallAngryPlanet(null).put(SUBJECTS_KEY, new JsonArray().add(pojo2JsonObject(subject)));

    var response = await(doPost(client, INSTANCES, instanceToCreate));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
  }

  @Test
  @DisplayName("should update an instance unlinking the subject source and type")
  void shouldUpdateInstance_unlinkingSubjectSourceAndType() {
    var subject = new Subject().withSourceId(subjectSourceId).withTypeId(subjectTypeId).withValue("subject");
    var instanceToCreate = smallAngryPlanet(UUID.randomUUID())
      .put(SUBJECTS_KEY, new JsonArray().add(pojo2JsonObject(subject)));
    var newId = createInstance(instanceToCreate).getString("id");

    var instanceFromGet = getById(newId).jsonBody();
    instanceFromGet.putNull(SUBJECTS_KEY);

    var response = update(instanceFromGet);

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
  }

  @Test
  @DisplayName("should update an instance linking and unlinking the subject source and type")
  void shouldUpdateInstance_linkingAndUnlinkingSubjectSourceAndType() {
    var subject = new Subject().withSourceId(subjectSourceId).withTypeId(subjectTypeId).withValue("subject");
    var instanceToCreate = smallAngryPlanet(UUID.randomUUID())
      .put(SUBJECTS_KEY, new JsonArray().add(pojo2JsonObject(subject)));
    var newId = createInstance(instanceToCreate).getString("id");

    var instanceFromGet = getById(newId).jsonBody();
    var updatedSubject = new Subject()
      .withSourceId(subjectSourceId)
      .withTypeId(subjectTypeId)
      .withValue("subject upd");
    instanceFromGet.put(SUBJECTS_KEY, new JsonArray().add(pojo2JsonObject(updatedSubject)));

    var response = update(instanceFromGet);

    assertThat(response.status()).isEqualTo(SC_NO_CONTENT);
  }

  @Test
  @DisplayName("should return 422 when creating an instance whose note exceeds the maximum length")
  void shouldReturn422_whenCreatingInstanceNoteExceedsMaximumLength() {
    var id = UUID.randomUUID();
    var instanceToCreate = smallAngryPlanet(id)
      .put("notes", new JsonArray().add(new InstanceNote().withNote("x".repeat(MAX_NOTE_LENGTH + 1))));

    var response = await(doPost(client, INSTANCES, instanceToCreate));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
  }

  @Test
  @DisplayName("should return 422 when creating an instance whose administrative note exceeds the maximum length")
  void shouldReturn422_whenCreatingInstanceAdministrativeNoteExceedsMaximumLength() {
    var id = UUID.randomUUID();
    var instanceToCreate = smallAngryPlanet(id)
      .put("administrativeNotes", new JsonArray().add("x".repeat(MAX_NOTE_LENGTH + 1)));

    var response = await(doPost(client, INSTANCES, instanceToCreate));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
  }

  @Test
  @DisplayName("should return 422 when updating an instance's administrative note to exceed the maximum length")
  void shouldReturn422_whenUpdatingInstanceAdministrativeNoteExceedsMaximumLength() {
    var id = UUID.randomUUID();
    createInstance(smallAngryPlanet(id));
    var instance = getById(id).jsonBody();
    instance.put("administrativeNotes", new JsonArray().add("x".repeat(MAX_NOTE_LENGTH + 1)));

    assertThat(update(instance).status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
  }

  @Test
  @DisplayName("should return 422 when updating an instance's note to exceed the maximum length")
  void shouldReturn422_whenUpdatingInstanceNoteExceedsMaximumLength() {
    var id = UUID.randomUUID();
    createInstance(smallAngryPlanet(id));
    var instance = getById(id).jsonBody();
    var longNote = new InstanceNote().withNote("x".repeat(MAX_NOTE_LENGTH + 1));
    instance.put("notes", new JsonArray().add(pojo2JsonObject(longNote)));

    assertThat(update(instance).status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
  }

  @Test
  @DisplayName("should return 422 when an instance has an additional unrecognized property")
  void shouldReturn422_whenInstanceHasAdditionalProperty() {
    var request = smallAngryPlanet(UUID.randomUUID()).put("somethingAdditional", "foo");

    var response = await(doPost(client, INSTANCES, request));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
    assertThat(response.jsonBody().mapTo(Errors.class).getErrors().getFirst().getMessage())
      .contains("Unrecognized field");
  }

  @Test
  @DisplayName("should return 422 when an instance identifier has an additional unrecognized property")
  void shouldReturn422_whenInstanceIdentifierHasAdditionalProperty() {
    var request = nod(UUID.randomUUID());
    request.getJsonArray("identifiers").add(identifier(isbnTypeId, "5645678432576").put("somethingAdditional", "foo"));

    var response = await(doPost(client, INSTANCES, request));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
    assertThat(response.jsonBody().mapTo(Errors.class).getErrors().getFirst().getMessage())
      .contains("Unrecognized field");
  }

  @Test
  @DisplayName("should return 422 when patching an instance with an administrative note that is too long")
  void shouldReturn422_whenPatchingInstanceWithLongAdministrativeNote() {
    var newId = createInstance(smallAngryPlanet(UUID.randomUUID())).getString("id");
    var patchJson = new JsonObject()
      .put("administrativeNotes", new JsonArray().add(StringUtils.repeat("a", MAX_NOTE_LENGTH + 1)));

    var response = await(doPatch(client, INSTANCES + "/" + newId, patchJson));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
  }

  @Test
  @DisplayName("should return 422 when patching an instance with a note that is too long")
  void shouldReturn422_whenPatchingInstanceWithLongNote() {
    var newId = createInstance(smallAngryPlanet(UUID.randomUUID())).getString("id");
    var longNote = new InstanceNote()
      .withInstanceNoteTypeId(UUID.randomUUID().toString())
      .withNote(StringUtils.repeat("a", MAX_NOTE_LENGTH + 1));
    var patchJson = new JsonObject().put("notes", new JsonArray().add(pojo2JsonObject(longNote)));

    var response = await(doPatch(client, INSTANCES + "/" + newId, patchJson));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_CONTENT);
  }
}
