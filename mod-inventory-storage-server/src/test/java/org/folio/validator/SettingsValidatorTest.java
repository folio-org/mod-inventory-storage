package org.folio.validator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import java.util.stream.Stream;
import org.folio.rest.exceptions.SettingsValidationException;
import org.folio.rest.jaxrs.model.Setting;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class SettingsValidatorTest {

  private final SettingsValidator settingsValidator = new SettingsValidator();

  // ============================
  // STRING TYPE
  // ============================

  @ParameterizedTest
  @ValueSource(strings = {
    "test_string_value",
    "",
    "test@#$%^&*()_+-=[]{}|;:,.<>?"
  })
  void validateStringTypeShouldAcceptStringValues(String value) {
    assertDoesNotThrow(() ->
      settingsValidator.validate(value, createStringSetting()));
  }

  @ParameterizedTest
  @MethodSource("invalidStringValues")
  void validateStringTypeShouldRejectNonStringValues(Object value) {
    var setting = createStringSetting();
    var exception = assertThrows(SettingsValidationException.class,
      () -> settingsValidator.validate(value, setting));

    assertThat(exception.getMessage(), is("Setting value should be a string"));
  }

  static Stream<Object> invalidStringValues() {
    return Stream.of(
      123,
      true
    );
  }

  // ============================
  // INTEGER TYPE
  // ============================

  @ParameterizedTest
  @ValueSource(ints = {42, 0, -100})
  void validateIntegerTypeShouldAcceptIntegerValues(Integer value) {
    assertDoesNotThrow(() ->
      settingsValidator.validate(value, createIntegerSetting()));
  }

  @ParameterizedTest
  @MethodSource("invalidIntegerValues")
  void validateIntegerTypeShouldRejectNonIntegerValues(Object value) {
    var setting = createIntegerSetting();
    var exception = assertThrows(SettingsValidationException.class,
      () -> settingsValidator.validate(value, setting));

    assertThat(exception.getMessage(), is("Setting value should be an integer"));
  }

  static Stream<Object> invalidIntegerValues() {
    return Stream.of(
      "123",
      true,
      3.14,
      999L
    );
  }

  // ============================
  // BOOLEAN TYPE
  // ============================

  @ParameterizedTest
  @MethodSource("validBooleanValues")
  void validateBooleanTypeShouldAcceptValidValues(Object value) {
    assertDoesNotThrow(() ->
      settingsValidator.validate(value, createBooleanSetting()));
  }

  static Stream<Object> validBooleanValues() {
    return Stream.of(true, false, "true", "false", "TRUE", "fAlSe");
  }

  @ParameterizedTest
  @MethodSource("invalidBooleanValues")
  void validateBooleanTypeShouldRejectInvalidValues(Object value) {
    var setting = createBooleanSetting();
    var exception = assertThrows(SettingsValidationException.class,
      () -> settingsValidator.validate(value, setting));

    assertThat(exception.getMessage(), is("Setting value should be a boolean"));
  }

  static Stream<Object> invalidBooleanValues() {
    return Stream.of("true true", 1, 0);
  }

  // ============================
  // NULLS
  // ============================

  @ParameterizedTest
  @MethodSource("nullArguments")
  void validateShouldThrowForNullArguments(Object value, Setting setting) {
    assertThrows(Exception.class,
      () -> settingsValidator.validate(value, setting));
  }

  static Stream<Arguments> nullArguments() {
    return Stream.of(
      Arguments.of(null, createStringSetting()),
      Arguments.of("value", null),
      Arguments.of(null, null)
    );
  }

  private static Setting createStringSetting() {
    return new Setting()
      .withId(UUID.randomUUID())
      .withKey("STRING_SETTING")
      .withValue("test_value")
      .withType(Setting.Type.STRING)
      .withCentralManaged(false)
      .withDescription("Test STRING setting");
  }

  private static Setting createIntegerSetting() {
    return new Setting()
      .withId(UUID.randomUUID())
      .withKey("INTEGER_SETTING")
      .withValue("42")
      .withType(Setting.Type.INTEGER)
      .withCentralManaged(false)
      .withDescription("Test INTEGER setting");
  }

  private static Setting createBooleanSetting() {
    return new Setting()
      .withId(UUID.randomUUID())
      .withKey("BOOLEAN_SETTING")
      .withValue("true")
      .withType(Setting.Type.BOOLEAN)
      .withCentralManaged(false)
      .withDescription("Test BOOLEAN setting");
  }
}
