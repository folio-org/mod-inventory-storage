package org.folio.validator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import io.vertx.core.json.JsonObject;
import org.folio.rest.exceptions.ValidationException;
import org.junit.jupiter.api.Test;

class HoldingsValidatorsTest {

  private static final String VALID_UUID = "123e4567-e89b-12d3-a456-426614174000";

  @Test
  void shouldSucceedWhenNoTrackedFieldsPresent() {
    var patchJson = new JsonObject().put("nonTracked", "value");

    var result = HoldingsValidators.checkRequiredFieldsIfPresent(patchJson);

    assertThat(result.succeeded(), is(true));
    assertThat(result.result(), is(patchJson));
  }

  @Test
  void shouldSucceedWhenTrackedFieldsPresentAndValidUuids() {
    var patchJson = new JsonObject()
      .put("sourceId", VALID_UUID)
      .put("instanceId", VALID_UUID)
      .put("permanentLocationId", VALID_UUID);

    var result = HoldingsValidators.checkRequiredFieldsIfPresent(patchJson);

    assertThat(result.succeeded(), is(true));
    assertThat(result.result(), is(patchJson));
  }

  @Test
  void shouldFailWhenSourceIdInvalid() {
    var patchJson = new JsonObject().put("sourceId", "not-a-uuid");

    var result = HoldingsValidators.checkRequiredFieldsIfPresent(patchJson);

    assertThat(result.failed(), is(true));
    assertThat(result.cause() instanceof ValidationException, is(true));
    assertThat(result.cause().getMessage(), containsString("sourceId"));
  }

  @Test
  void shouldFailWhenInstanceIdInvalid() {
    var patchJson = new JsonObject().put("instanceId", "not-a-uuid");

    var result = HoldingsValidators.checkRequiredFieldsIfPresent(patchJson);

    assertThat(result.failed(), is(true));
    assertThat(result.cause() instanceof ValidationException, is(true));
    assertThat(result.cause().getMessage(), containsString("instanceId"));
  }

  @Test
  void shouldFailWhenPermanentLocationIdInvalid() {
    var patchJson = new JsonObject().put("permanentLocationId", "not-a-uuid");

    var result = HoldingsValidators.checkRequiredFieldsIfPresent(patchJson);

    assertThat(result.failed(), is(true));
    assertThat(result.cause() instanceof ValidationException, is(true));
    assertThat(result.cause().getMessage(), containsString("permanentLocationId"));
  }

  @Test
  void shouldFailWhenTrackedFieldPresentButNullValue() {
    var patchJson = new JsonObject().putNull("sourceId");

    var result = HoldingsValidators.checkRequiredFieldsIfPresent(patchJson);

    assertThat(result.failed(), is(true));
    assertThat(result.cause() instanceof ValidationException, is(true));
    assertThat(result.cause().getMessage(), containsString("sourceId"));
  }

  @Test
  void shouldFailWhenTrackedFieldPresentButNonStringValue() {
    // getString("sourceId") returns null when underlying value isn't a String
    var patchJson = new JsonObject().put("sourceId", 123);

    var result = HoldingsValidators.checkRequiredFieldsIfPresent(patchJson);

    assertThat(result.failed(), is(true));
    assertThat(result.cause() instanceof ValidationException, is(true));
    assertThat(result.cause().getMessage(), containsString("sourceId"));
  }
}
