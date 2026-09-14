package org.folio.it.api;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;
import static org.folio.validator.NotesValidators.MAX_NOTE_LENGTH;

import io.vertx.core.json.JsonArray;
import java.util.Set;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.HoldingsNote;
import org.folio.support.ResourcePaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HoldingsStorageValidationIT extends HoldingsStorageTestBase {

  @Test
  @DisplayName("should return 422 when the holding id is not a UUID")
  void shouldReturn422_whenHoldingIdIsNotUuid() {
    var instanceId = createInstanceRecord();
    var request = holdingRequest(instanceId).create().put("id", "6556456");

    var response = await(doPost(client, ResourcePaths.HOLDINGS, request));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    var error = response.jsonBody().mapTo(Errors.class).getErrors().getFirst();
    assertThat(error.getMessage()).contains("must match");
    assertThat(error.getParameters().getFirst().getKey()).isEqualTo("id");
  }

  @Test
  @DisplayName("should return 400 when a statistical code id is invalid on create")
  void shouldReturn400_whenStatisticalCodeIdIsInvalidOnCreate() {
    var instanceId = createInstanceRecord();
    var request = holdingRequest(instanceId).create().put(STATISTICAL_CODE_IDS_KEY, Set.of(INVALID_VALUE));

    var response = await(doPost(client, ResourcePaths.HOLDINGS, request));

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString()).contains(INVALID_TYPE_ERROR_MESSAGE);
  }

  @Test
  @DisplayName("should return 400 when a statistical code id is invalid on update")
  void shouldReturn400_whenStatisticalCodeIdIsInvalidOnUpdate() {
    var instanceId = createInstanceRecord();
    var holding = createHolding(holdingRequest(instanceId));
    holding.put(STATISTICAL_CODE_IDS_KEY, Set.of(INVALID_VALUE));

    var response = updateHolding(holding);

    assertThat(response.status()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().toString()).contains(INVALID_TYPE_ERROR_MESSAGE);
  }

  @Test
  @DisplayName("should return 422 when the source id is removed")
  void shouldReturn422_whenSourceIdRemoved() {
    var instanceId = createInstanceRecord();
    var holding = createHolding(holdingRequest(instanceId).withCallNumber("testCallNumber"));

    holding.putNull("sourceId").put("callNumber", "updatedTestCallNumber");
    var response = updateHolding(holding);

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    var updated = getHoldingById(holding.getString("id")).jsonBody();
    assertThat(updated.getString("callNumber")).isEqualTo("testCallNumber");
  }

  @Test
  @DisplayName("should return 422 when creating a holding whose administrative note exceeds the maximum length")
  void shouldReturn422_whenCreatingHoldingWithLongAdministrativeNote() {
    var request = holdingRequest(createInstanceRecord()).create()
      .put(ADMINISTRATIVE_NOTES_KEY, new JsonArray().add("x".repeat(MAX_NOTE_LENGTH + 1)));

    var response = await(doPost(client, ResourcePaths.HOLDINGS, request));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  @DisplayName("should return 422 when creating a holding whose note exceeds the maximum length")
  void shouldReturn422_whenCreatingHoldingWithLongNote() {
    var request = holdingRequest(createInstanceRecord()).create()
      .put("notes", new JsonArray().add(pojo2JsonObject(new HoldingsNote().withNote("x".repeat(MAX_NOTE_LENGTH + 1)))));

    var response = await(doPost(client, ResourcePaths.HOLDINGS, request));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  @DisplayName("should return 422 when updating a holding's administrative note to exceed the maximum length")
  void shouldReturn422_whenUpdatingHoldingWithLongAdministrativeNote() {
    var holding = createHolding(holdingRequest(createInstanceRecord()));
    holding.put(ADMINISTRATIVE_NOTES_KEY, new JsonArray().add("x".repeat(MAX_NOTE_LENGTH + 1)));

    assertThat(updateHolding(holding).status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  @DisplayName("should return 422 when updating a holding's note to exceed the maximum length")
  void shouldReturn422_whenUpdatingHoldingWithLongNote() {
    var holding = createHolding(holdingRequest(createInstanceRecord()));
    holding.put("notes", new JsonArray()
      .add(pojo2JsonObject(new HoldingsNote().withNote("x".repeat(MAX_NOTE_LENGTH + 1)))));

    assertThat(updateHolding(holding).status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  @DisplayName("should return 422 when creating a holding without a permanent location")
  void shouldReturn422_whenCreatingWithoutPermanentLocation() {
    var request = holdingRequest(createInstanceRecord()).create();
    request.remove(PERMANENT_LOCATION_ID_KEY);

    var response = await(doPost(client, ResourcePaths.HOLDINGS, request));

    assertThat(response.status()).isEqualTo(SC_UNPROCESSABLE_ENTITY);
    var error = response.jsonBody().mapTo(Errors.class).getErrors().getFirst();
    assertThat(error.getMessage()).containsAnyOf("may not be null", "must not be null");
    assertThat(error.getParameters().getFirst().getKey()).isEqualTo(PERMANENT_LOCATION_ID_KEY);
  }
}
