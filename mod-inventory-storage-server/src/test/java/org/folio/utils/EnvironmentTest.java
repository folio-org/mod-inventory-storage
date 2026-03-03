package org.folio.utils;

import static org.folio.utils.Environment.MAX_REQUEST_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EnvironmentTest {

  private static final String TEST_KEY = "TEST_ENVIRONMENT_PROPERTY";
  private static final String MISSING_TEST_KEY = "TEST_ENVIRONMENT_PROPERTY_MISSING";
  private static final String TEST_BOOL_KEY = "TEST_ENVIRONMENT_BOOL_PROPERTY";
  private static final String TEST_INT_KEY = "TEST_ENVIRONMENT_INT_PROPERTY";
  private static final String TEST_BLANK_KEY = "TEST_ENVIRONMENT_BLANK_PROPERTY";

  @AfterEach
  void tearDown() {
    System.clearProperty(TEST_KEY);
    System.clearProperty(MISSING_TEST_KEY);
    System.clearProperty(TEST_BOOL_KEY);
    System.clearProperty(TEST_INT_KEY);
    System.clearProperty(TEST_BLANK_KEY);
    System.clearProperty(MAX_REQUEST_SIZE);
  }

  @Test
  void getValue_keyIsMissing_returnsNull() {
    assertNull(Environment.getValue(MISSING_TEST_KEY));
  }

  @Test
  void getValue_withDefaultAndMissingKey_returnsDefault() {
    assertEquals("fallback", Environment.getValue(MISSING_TEST_KEY, "fallback"));
  }

  @Test
  void getValue_systemPropertyIsSet_returnsSystemPropertyValue() {
    System.setProperty(TEST_KEY, "property-value");

    assertEquals("property-value", Environment.getValue(TEST_KEY, "fallback"));
  }

  @Test
  void getValueOrEmpty_keyIsMissing_returnsEmptyString() {
    assertEquals("", Environment.getValueOrEmpty(MISSING_TEST_KEY));
  }

  @Test
  void getValueOrFail_keyIsMissing_throwsIllegalStateException() {
    var exception = assertThrows(IllegalStateException.class, () -> Environment.getValueOrFail(MISSING_TEST_KEY));

    assertTrue(exception.getMessage().contains("Required S3 configuration property is missing: " + MISSING_TEST_KEY));
  }

  @Test
  void getValueOrFail_valueIsBlank_throwsIllegalStateException() {
    System.setProperty(TEST_BLANK_KEY, "   ");

    var exception = assertThrows(IllegalStateException.class, () -> Environment.getValueOrFail(TEST_BLANK_KEY));

    assertTrue(exception.getMessage().contains("Required S3 configuration property is missing: " + TEST_BLANK_KEY));
  }

  @Test
  void getValueOrFail_valueExists_returnsValue() {
    System.setProperty(TEST_KEY, "configured-value");

    assertEquals("configured-value", Environment.getValueOrFail(TEST_KEY));
  }

  @Test
  void getBoolValue_valueMissing_returnsDefault() {
    assertEquals(Boolean.TRUE, Environment.getBoolValue(MISSING_TEST_KEY, Boolean.TRUE));
  }

  @Test
  void getBoolValue_valueExists_returnsParsedValue() {
    System.setProperty(TEST_BOOL_KEY, "false");

    assertEquals(Boolean.FALSE, Environment.getBoolValue(TEST_BOOL_KEY, Boolean.TRUE));
  }

  @Test
  void getIntValue_valueMissingAndDefaultProvided_returnsDefault() {
    assertEquals(42, Environment.getIntValue(MISSING_TEST_KEY, 42));
  }

  @Test
  void getIntValue_valueMissingAndDefaultNotProvided_throwsIllegalStateException() {
    var exception = assertThrows(IllegalStateException.class, () -> Environment.getIntValue(MISSING_TEST_KEY, null));

    assertTrue(exception.getMessage().contains("Required configuration property is missing: " + MISSING_TEST_KEY));
  }

  @Test
  void getIntValue_valueExists_returnsParsedInteger() {
    System.setProperty(TEST_INT_KEY, "31457280");

    assertEquals(31457280, Environment.getIntValue(TEST_INT_KEY, 10));
  }

  @Test
  void getKafkaProducerMaxRequestSize_propertyMissing_returnsDefaultValue() {
    assertEquals(10485760, Environment.getKafkaProducerMaxRequestSize());
  }

  @Test
  void getKafkaProducerMaxRequestSize_propertyExists_returnsParsedValue() {
    System.setProperty(MAX_REQUEST_SIZE, "20971520");

    assertEquals(20971520, Environment.getKafkaProducerMaxRequestSize());
  }
}
