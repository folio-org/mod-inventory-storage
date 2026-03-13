package org.folio.validator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import io.vertx.core.json.JsonObject;
import org.folio.rest.exceptions.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HoldingsValidatorsTest {

  private static final String VALID_UUID = "123e4567-e89b-12d3-a456-426614174000";

  @Test
  void shouldSucceedWhenNoTrackedFieldsPresent() {
    var patchJson = new JsonObject().put("nonTracked", "value");

    var result = HoldingsValidators.refuseNullValueInRequiredFields(patchJson);

    assertThat(result.succeeded(), is(true));
    assertThat(result.result(), is(patchJson));
  }

  @Test
  void shouldSucceedWhenTrackedFieldsPresentAndValidUuids() {
    var patchJson = new JsonObject()
      .put("sourceId", VALID_UUID)
      .put("instanceId", VALID_UUID)
      .put("permanentLocationId", VALID_UUID);

    var result = HoldingsValidators.refuseNullValueInRequiredFields(patchJson);

    assertThat(result.succeeded(), is(true));
    assertThat(result.result(), is(patchJson));
  }

  @ParameterizedTest
  @ValueSource(strings = {"sourceId", "instanceId", "permanentLocationId"})
  void shouldFailWhenTrackedFieldPresentButNullValue(String fieldName) {
    var patchJson = new JsonObject().putNull(fieldName);

    var result = HoldingsValidators.refuseNullValueInRequiredFields(patchJson);

    assertThat(result.failed(), is(true));
    assertThat(result.cause() instanceof ValidationException, is(true));
    assertThat(result.cause().getMessage(), containsString(fieldName));
  }
}
