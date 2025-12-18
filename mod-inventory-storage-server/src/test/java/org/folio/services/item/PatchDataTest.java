package org.folio.services.item;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.stream.Stream;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.ItemPatchRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("PatchData Tests")
class PatchDataTest {

  private PatchData patchData;

  @BeforeEach
  void setUp() {
    var oldItem = new Item();
    oldItem.setId("old-item-id");
    oldItem.setHoldingsRecordId("old-holdings-id");

    patchData = new PatchData();
    patchData.setOldItem(oldItem);
  }

  @ParameterizedTest
  @MethodSource("provideFalseTestCases")
  @DisplayName("Should return false when hasChanges conditions are not met")
  void hasChanges_shouldReturnFalse(String description, ItemPatchRequest itemPatch, Item oldItemOverride) {
    // Given
    if (oldItemOverride != null) {
      patchData.setOldItem(oldItemOverride);
    }
    patchData.setPatchRequest(itemPatch);

    // When
    var hasChanges = patchData.hasChanges();
    
    // Then
    assertFalse(hasChanges, description);
  }

  @ParameterizedTest
  @MethodSource("provideTrueTestCases")
  @DisplayName("Should return true when hasChanges conditions are met")
  void hasChanges_shouldReturnTrue(String description, ItemPatchRequest itemPatch, Item oldItemOverride) {
    // Given
    if (oldItemOverride != null) {
      patchData.setOldItem(oldItemOverride);
    }
    patchData.setPatchRequest(itemPatch);

    // When
    var hasChanges = patchData.hasChanges();
    
    // Then
    assertTrue(hasChanges, description);
  }

  private static Stream<Arguments> provideFalseTestCases() {
    return Stream.of(
      Arguments.of(
        "No additional properties exist",
        new ItemPatchRequest(),
        null
      ),
      Arguments.of(
        "Only holdingsRecordId is present and unchanged",
        new ItemPatchRequest().withAdditionalProperty("holdingsRecordId", "old-holdings-id"),
        null
      ),
      Arguments.of(
        "HoldingsRecordId is null in both old and patch",
        new ItemPatchRequest().withAdditionalProperty("holdingsRecordId", null),
        new Item().withId("old-item-id").withHoldingsRecordId(null)
      ),
      Arguments.of(
        "Empty string holdingsRecordId in both old and patch",
        new ItemPatchRequest().withAdditionalProperty("holdingsRecordId", ""),
        new Item().withId("old-item-id").withHoldingsRecordId("")
      )
    );
  }

  private static Stream<Arguments> provideTrueTestCases() {
    return Stream.concat(
      provideHoldingsRecordIdChangeTestCases(),
      Stream.concat(
        provideOtherPropertiesTestCases(),
        provideEmptyStringToValueTestCases()
      )
    );
  }

  private static Stream<Arguments> provideHoldingsRecordIdChangeTestCases() {
    return Stream.of(
      Arguments.of("Only holdingsRecordId is present and changed",
        new ItemPatchRequest().withAdditionalProperty("holdingsRecordId", "different-holdings-id"), null),
      Arguments.of("HoldingsRecordId changes from non-null to null",
        new ItemPatchRequest().withAdditionalProperty("holdingsRecordId", null), null)
    );
  }

  private static Stream<Arguments> provideOtherPropertiesTestCases() {
    return Stream.of(
      Arguments.of("Other properties are present",
        new ItemPatchRequest().withAdditionalProperty("materialTypeId", "some-material-type-id"), null),
      Arguments.of("Multiple properties including holdingsRecordId are present",
        new ItemPatchRequest()
          .withAdditionalProperty("holdingsRecordId", "old-holdings-id")
          .withAdditionalProperty("materialTypeId", "some-material-type-id"), null),
      Arguments.of("Status property is present",
        new ItemPatchRequest().withAdditionalProperty("status", Map.of("name", "Available")), null),
      Arguments.of("PermanentLoanTypeId property is present",
        new ItemPatchRequest().withAdditionalProperty("permanentLoanTypeId", "loan-type-id"), null)
    );
  }

  private static Stream<Arguments> provideEmptyStringToValueTestCases() {
    return Stream.of(
      Arguments.of("HoldingsRecordId changes from empty string to actual value",
        new ItemPatchRequest().withAdditionalProperty("holdingsRecordId", "new-holdings-id"),
        new Item().withId("old-item-id").withHoldingsRecordId(""))
    );
  }
}
