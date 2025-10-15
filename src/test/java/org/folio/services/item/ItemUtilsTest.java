package org.folio.services.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.folio.rest.exceptions.ValidationException;
import org.folio.rest.jaxrs.model.EffectiveCallNumberComponents;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.ItemPatch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("ItemUtils Tests")
class ItemUtilsTest {

  @Test
  @DisplayName("Should throw UnsupportedOperationException when trying to instantiate")
  void constructor_shouldThrowExceptionOnInstantiation() throws Exception {
    // Given
    var constructor = ItemUtils.class.getDeclaredConstructor();
    constructor.setAccessible(true);

    // When & Then
    var exception = assertThrows(InvocationTargetException.class, constructor::newInstance);
    assertEquals(UnsupportedOperationException.class, exception.getCause().getClass());
    assertEquals("Utility class", exception.getCause().getMessage());
  }

  @ParameterizedTest
  @MethodSource("provideValidRequiredFieldsCases")
  @DisplayName("Should succeed validation when all required fields are valid")
  void validateRequiredFields_shouldSucceedValidationWhenRequiredFieldsAreValid(String description,
                                                                                List<ItemPatch> items) {
    // When
    var result = ItemUtils.validateRequiredFields(items);

    // Then
    assertTrue(result.succeeded(), description);
    assertNull(result.result(), description);
  }

  @ParameterizedTest
  @MethodSource("provideInvalidRequiredFieldsCases")
  @DisplayName("Should fail validation when required fields are missing or invalid")
  void validateRequiredFields_shouldFailValidationWhenRequiredFieldsAreInvalid(String description,
                                                                               List<ItemPatch> items,
                                                                               int expectedErrorCount) {
    // When
    var result = ItemUtils.validateRequiredFields(items);

    // Then
    assertTrue(result.failed(), description);
    var exception = (ValidationException) result.cause();
    assertNotNull(exception.getErrors(), description);
    assertEquals(expectedErrorCount, exception.getErrors().getErrors().size(), description);
  }

  @ParameterizedTest
  @MethodSource("provideNullOrBlankValues")
  @DisplayName("Should correctly identify null or blank values")
  void isNullOrBlank_shouldIdentifyNullOrBlankValues(String description, Object value, boolean expectedResult) {
    // When
    var result = ItemUtils.isNullOrBlank(value);

    // Then
    assertEquals(expectedResult, result, description);
  }

  @Test
  @DisplayName("Should create error with correct message and parameters")
  void requiredFieldsError_shouldCreateErrorWithCorrectMessageAndParameters() {
    // Given
    var itemId = "test-item-id";
    var fieldNames = List.of("materialTypeId", "status.name");

    // When
    var error = ItemUtils.requiredFieldsError(itemId, fieldNames);

    // Then
    assertNotNull(error);
    assertEquals("Required fields cannot be removed. ItemId: " + itemId, error.getMessage());
    assertEquals("field.required", error.getCode());
    assertEquals(2, error.getParameters().size());
    assertEquals("field", error.getParameters().get(0).getKey());
    assertEquals("materialTypeId", error.getParameters().get(0).getValue());
    assertEquals("field", error.getParameters().get(1).getKey());
    assertEquals("status.name", error.getParameters().get(1).getValue());
  }

  @Test
  @DisplayName("Should transfer all effective values to patch")
  void transferEffectiveValuesToPatch_shouldTransferAllEffectiveValuesToPatch() {
    // Given
    var item = new Item();
    item.setEffectiveLocationId("location-id");
    var callNumberComponents = new EffectiveCallNumberComponents();
    callNumberComponents.setCallNumber("call-number");
    item.setEffectiveCallNumberComponents(callNumberComponents);
    item.setEffectiveShelvingOrder("shelving-order");

    var itemPatch = new ItemPatch()
      .withAdditionalProperty("someProperty", "value"); // Initialize with some property

    // When
    ItemUtils.transferEffectiveValuesToPatch(item, itemPatch);

    // Then
    var additionalProperties = itemPatch.getAdditionalProperties();
    assertEquals("location-id", additionalProperties.get("effectiveLocationId"));
    assertEquals(callNumberComponents, additionalProperties.get("effectiveCallNumberComponents"));
    assertEquals("shelving-order", additionalProperties.get("effectiveShelvingOrder"));
  }

  @Test
  @DisplayName("Should not transfer null effective values to patch")
  void transferEffectiveValuesToPatch_shouldNotTransferNullEffectiveValuesToPatch() {
    // Given
    var item = new Item();
    item.setEffectiveLocationId(null);
    item.setEffectiveCallNumberComponents(null);
    item.setEffectiveShelvingOrder(null);

    var itemPatch = new ItemPatch()
      .withAdditionalProperty("someProperty", "value"); // Initialize with some property

    // When
    ItemUtils.transferEffectiveValuesToPatch(item, itemPatch);

    // Then
    var additionalProperties = itemPatch.getAdditionalProperties();
    assertFalse(additionalProperties.containsKey("effectiveLocationId"));
    assertFalse(additionalProperties.containsKey("effectiveCallNumberComponents"));
    assertFalse(additionalProperties.containsKey("effectiveShelvingOrder"));
  }

  @Test
  @DisplayName("Should transfer only non-null effective values to patch")
  void transferEffectiveValuesToPatch_shouldTransferOnlyNonNullEffectiveValuesToPatch() {
    // Given
    var item = new Item();
    item.setEffectiveLocationId("location-id");
    item.setEffectiveCallNumberComponents(null);
    item.setEffectiveShelvingOrder("shelving-order");

    var itemPatch = new ItemPatch()
      .withAdditionalProperty("someProperty", "value"); // Initialize with some property

    // When
    ItemUtils.transferEffectiveValuesToPatch(item, itemPatch);

    // Then
    var additionalProperties = itemPatch.getAdditionalProperties();
    assertEquals("location-id", additionalProperties.get("effectiveLocationId"));
    assertFalse(additionalProperties.containsKey("effectiveCallNumberComponents"));
    assertEquals("shelving-order", additionalProperties.get("effectiveShelvingOrder"));
  }

  @Test
  @DisplayName("Should handle update items error and return failed future")
  void handleUpdateItemsError_shouldHandleUpdateItemsErrorAndReturnFailedFuture() {
    // Given
    var throwable = new RuntimeException("Test error");

    // When
    var result = ItemUtils.handleUpdateItemsError(throwable);

    // Then
    assertTrue(result.failed());
    assertTrue(result.cause() instanceof ValidationException || result.cause() instanceof RuntimeException);
  }

  @Test
  @DisplayName("Should normalize item fields correctly")
  void normalizeItemFields_shouldNormalizeFieldsCorrectly() {
    // Given
    var props = new HashMap<String, Object>();
    props.put("order", "1");
    props.put("discoverySuppress", "true");
    props.put("notes", List.of(
      new HashMap<>(Map.of("staffOnly", "true")),
      new HashMap<>(Map.of("staffOnly", "false"))
    ));
    props.put("circulationNotes", List.of(
      new HashMap<>(Map.of("staffOnly", "false"))
    ));

    var itemPatch = new ItemPatch();
    props.forEach(itemPatch::withAdditionalProperty);

    // When
    ItemUtils.normalizeItemFields(List.of(itemPatch));

    // Then
    var normalizedProps = itemPatch.getAdditionalProperties();
    assertEquals(1, normalizedProps.get("order"));
    assertEquals(true, normalizedProps.get("discoverySuppress"));

    // Notes normalization
    var notes = (List<Map<String, Object>>) normalizedProps.get("notes");
    assertEquals(true, notes.getFirst().get("staffOnly"));
    assertEquals(false, notes.get(1).get("staffOnly"));

    // Circulation notes normalization
    var circulationNotes = (List<Map<String, Object>>) normalizedProps.get("circulationNotes");
    assertEquals(false, circulationNotes.getFirst().get("staffOnly"));
  }

  @Test
  @DisplayName("Should not fail when properties are missing")
  void normalizeItemFields_shouldHandleMissingProperties() {
    // Given
    var itemPatch = new ItemPatch();

    // When
    ItemUtils.normalizeItemFields(List.of(itemPatch));

    // Then
    assertTrue(itemPatch.getAdditionalProperties().isEmpty());
  }

  @Test
  @DisplayName("Should not change properties with incorrect types for normalization")
  void normalizeItemFields_shouldNotChangeNotStringType() {
    // Given
    var props = new HashMap<String, Object>();
    props.put("order", 5);
    props.put("discoverySuppress", true);
    var itemPatch = new ItemPatch();
    props.forEach(itemPatch::withAdditionalProperty);
    var items = List.of(itemPatch);

    // When
    ItemUtils.normalizeItemFields(items);

    // Then
    var normalizedProps = itemPatch.getAdditionalProperties();
    assertEquals(5, normalizedProps.get("order"));
    assertEquals(true, normalizedProps.get("discoverySuppress"));
  }

  private static Stream<Arguments> provideValidRequiredFieldsCases() {
    return Stream.of(
      Arguments.of(
        "Empty list of items",
        List.<ItemPatch>of()
      ),
      Arguments.of(
        "Item with no additional properties",
        List.of(new ItemPatch())
      ),
      Arguments.of(
        "Item with valid materialTypeId",
        List.of(new ItemPatch().withAdditionalProperty("materialTypeId", "valid-material-type"))
      ),
      Arguments.of(
        "Item with valid permanentLoanTypeId",
        List.of(new ItemPatch().withAdditionalProperty("permanentLoanTypeId", "valid-loan-type"))
      ),
      Arguments.of(
        "Item with valid holdingsRecordId",
        List.of(new ItemPatch().withAdditionalProperty("holdingsRecordId", "valid-holdings-id"))
      ),
      Arguments.of(
        "Item with valid status",
        List.of(new ItemPatch().withAdditionalProperty("status", Map.of("name", "Available")))
      ),
      Arguments.of(
        "Item with all valid required fields",
        List.of(new ItemPatch()
          .withAdditionalProperty("materialTypeId", "material-type")
          .withAdditionalProperty("permanentLoanTypeId", "loan-type")
          .withAdditionalProperty("holdingsRecordId", "holdings-id")
          .withAdditionalProperty("status", Map.of("name", "Available")))
      ),
      Arguments.of(
        "Multiple items with valid fields",
        List.of(
          new ItemPatch().withAdditionalProperty("materialTypeId", "material-type-1"),
          new ItemPatch().withAdditionalProperty("permanentLoanTypeId", "loan-type-2")
        )
      )
    );
  }

  private static Stream<Arguments> provideInvalidRequiredFieldsCases() {
    var statusWithNullName = new HashMap<String, Object>();
    statusWithNullName.put("name", null);

    return Stream.of(
      Arguments.of(
        "Item with null materialTypeId",
        List.of(new ItemPatch().withAdditionalProperty("materialTypeId", null)),
        1
      ),
      Arguments.of(
        "Item with empty materialTypeId",
        List.of(new ItemPatch().withAdditionalProperty("materialTypeId", "")),
        1
      ),
      Arguments.of(
        "Item with blank materialTypeId",
        List.of(new ItemPatch().withAdditionalProperty("materialTypeId", "   ")),
        1
      ),
      Arguments.of(
        "Item with null permanentLoanTypeId",
        List.of(new ItemPatch().withAdditionalProperty("permanentLoanTypeId", null)),
        1
      ),
      Arguments.of(
        "Item with empty permanentLoanTypeId",
        List.of(new ItemPatch().withAdditionalProperty("permanentLoanTypeId", "")),
        1
      ),
      Arguments.of(
        "Item with null holdingsRecordId",
        List.of(new ItemPatch().withAdditionalProperty("holdingsRecordId", null)),
        1
      ),
      Arguments.of(
        "Item with empty holdingsRecordId",
        List.of(new ItemPatch().withAdditionalProperty("holdingsRecordId", "")),
        1
      ),
      Arguments.of(
        "Item with null status",
        List.of(new ItemPatch().withAdditionalProperty("status", null)),
        1
      ),
      Arguments.of(
        "Item with status having null name",
        List.of(new ItemPatch().withAdditionalProperty("status", statusWithNullName)),
        1
      ),
      Arguments.of(
        "Item with status having empty name",
        List.of(new ItemPatch().withAdditionalProperty("status", Map.of("name", ""))),
        1
      ),
      Arguments.of(
        "Item with status having blank name",
        List.of(new ItemPatch().withAdditionalProperty("status", Map.of("name", "   "))),
        1
      ),
      Arguments.of(
        "Item with multiple missing required fields",
        List.of(new ItemPatch()
          .withAdditionalProperty("materialTypeId", null)
          .withAdditionalProperty("permanentLoanTypeId", "")
          .withAdditionalProperty("holdingsRecordId", "   ")
          .withAdditionalProperty("status", statusWithNullName)),
        1 // One error per item, but multiple field parameters
      ),
      Arguments.of(
        "Multiple items with missing fields",
        List.of(
          new ItemPatch().withAdditionalProperty("materialTypeId", null),
          new ItemPatch().withAdditionalProperty("permanentLoanTypeId", "")
        ),
        2
      )
    );
  }

  private static Stream<Arguments> provideNullOrBlankValues() {
    return Stream.of(
      Arguments.of("Null value", null, true),
      Arguments.of("Empty string", "", true),
      Arguments.of("Blank string with spaces", "   ", true),
      Arguments.of("Blank string with tabs", "\t\t", true),
      Arguments.of("Blank string with newlines", "\n\n", true),
      Arguments.of("Valid string", "valid-value", false),
      Arguments.of("String with content and spaces", " valid ", false),
      Arguments.of("Number zero", 0, false),
      Arguments.of("Boolean false", false, false),
      Arguments.of("Boolean true", true, false),
      Arguments.of("Non-empty object", Map.of("key", "value"), false)
    );
  }
}
