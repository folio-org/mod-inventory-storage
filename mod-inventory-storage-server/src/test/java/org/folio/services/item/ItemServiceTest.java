package org.folio.services.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.withSettings;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import javax.ws.rs.core.Response;
import org.folio.rest.jaxrs.model.CirculationNote;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.ItemPatchRequest;
import org.folio.rest.persist.PgExceptionUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ItemService's constructor self-constructs 6 dependencies from a real Context, so this targets
 * its logic-bearing private helpers via reflection on a constructor-bypassed instance (same
 * approach as HoldingsServiceTest / InstanceServiceTest), plus its two private static methods
 * directly, rather than attempting full public-API orchestration.
 */
class ItemServiceTest {

  @Test
  @DisplayName("should be affected when the item's call number is blank and the holdings has one")
  void shouldBeAffected_whenItemCallNumberIsBlankAndHoldingsHasOne() {
    var item = new Item();
    var holdings = new HoldingsRecord().withCallNumber("A1");

    assertThat(isItemFieldsAffected(holdings, item)).isTrue();
  }

  @Test
  @DisplayName("should be affected when the item's call number prefix is blank and the holdings has one")
  void shouldBeAffected_whenItemCallNumberPrefixIsBlankAndHoldingsHasOne() {
    var item = new Item();
    var holdings = new HoldingsRecord().withCallNumberPrefix("PRE");

    assertThat(isItemFieldsAffected(holdings, item)).isTrue();
  }

  @Test
  @DisplayName("should be affected when the item's call number suffix is blank and the holdings has one")
  void shouldBeAffected_whenItemCallNumberSuffixIsBlankAndHoldingsHasOne() {
    var item = new Item();
    var holdings = new HoldingsRecord().withCallNumberSuffix("SUF");

    assertThat(isItemFieldsAffected(holdings, item)).isTrue();
  }

  @Test
  @DisplayName("should be affected when the item's call number type is blank and the holdings has one")
  void shouldBeAffected_whenItemCallNumberTypeIsBlankAndHoldingsHasOne() {
    var item = new Item();
    var holdings = new HoldingsRecord().withCallNumberTypeId("type-1");

    assertThat(isItemFieldsAffected(holdings, item)).isTrue();
  }

  @Test
  @DisplayName("should be affected when the item has no location and the holdings has one")
  void shouldBeAffected_whenItemHasNoLocationAndHoldingsHasOne() {
    var item = new Item();
    var holdings = new HoldingsRecord().withPermanentLocationId("location-1");

    assertThat(isItemFieldsAffected(holdings, item)).isTrue();
  }

  @Test
  @DisplayName("should not be affected when the item already has its own values and a location")
  void shouldNotBeAffected_whenItemAlreadyHasOwnValuesAndLocation() {
    var item = new Item()
      .withItemLevelCallNumber("A1")
      .withItemLevelCallNumberPrefix("PRE")
      .withItemLevelCallNumberSuffix("SUF")
      .withItemLevelCallNumberTypeId("type-1")
      .withPermanentLocationId("location-1");
    var holdings = new HoldingsRecord()
      .withCallNumber("B2")
      .withCallNumberPrefix("OTHER")
      .withCallNumberSuffix("OTHER")
      .withCallNumberTypeId("type-2")
      .withPermanentLocationId("location-2");

    assertThat(isItemFieldsAffected(holdings, item)).isFalse();
  }

  @Test
  @DisplayName("should respond with a conflict when the failure is a version conflict")
  void shouldRespondWithConflict_whenFailureIsVersionConflict() {
    try (var pgExceptionUtil = mockStatic(PgExceptionUtil.class)) {
      var cause = new RuntimeException("version conflict");
      pgExceptionUtil.when(() -> PgExceptionUtil.badRequestMessage(cause)).thenReturn("Optimistic locking failed");
      pgExceptionUtil.when(() -> PgExceptionUtil.isVersionConflict(cause)).thenReturn(true);

      var response = putFailure(cause);

      assertThat(response.getStatus()).isEqualTo(409);
    }
  }

  @Test
  @DisplayName("should explain the missing foreign key when the failure is a key-not-present violation")
  void shouldExplainMissingForeignKey_whenFailureIsKeyNotPresentViolation() {
    try (var pgExceptionUtil = mockStatic(PgExceptionUtil.class)) {
      var cause = new RuntimeException("fk violation");
      pgExceptionUtil.when(() -> PgExceptionUtil.badRequestMessage(cause)).thenReturn(
        "insert or update: Key (materialtypeid)=(bad-id) is not present in table \"material_type\".");
      pgExceptionUtil.when(() -> PgExceptionUtil.isVersionConflict(cause)).thenReturn(false);

      var response = putFailure(cause);

      assertThat(response.getStatus()).isEqualTo(400);
      assertThat((String) response.getEntity())
        .isEqualTo("Cannot set item materialtypeid = bad-id because it does not exist in material_type.id.");
    }
  }

  @Test
  @DisplayName("should explain the duplicate value when the failure is a key-already-exists violation")
  void shouldExplainDuplicateValue_whenFailureIsKeyAlreadyExistsViolation() {
    try (var pgExceptionUtil = mockStatic(PgExceptionUtil.class)) {
      var cause = new RuntimeException("unique violation");
      pgExceptionUtil.when(() -> PgExceptionUtil.badRequestMessage(cause)).thenReturn(
        "insert: Key (hrid)=(it0001) already exists.");
      pgExceptionUtil.when(() -> PgExceptionUtil.isVersionConflict(cause)).thenReturn(false);

      var response = putFailure(cause);

      assertThat(response.getStatus()).isEqualTo(400);
      assertThat((String) response.getEntity()).isEqualTo("hrid value already exists in table item: it0001");
    }
  }

  @Test
  @DisplayName("should fall back to the throwable's own message when there is no bad-request message")
  void shouldFallBackToThrowablesOwnMessage_whenNoBadRequestMessage() {
    try (var pgExceptionUtil = mockStatic(PgExceptionUtil.class)) {
      var cause = new RuntimeException("plain failure");
      pgExceptionUtil.when(() -> PgExceptionUtil.badRequestMessage(cause)).thenReturn(null);
      pgExceptionUtil.when(() -> PgExceptionUtil.isVersionConflict(cause)).thenReturn(false);

      var response = putFailure(cause);

      assertThat(response.getStatus()).isEqualTo(400);
      assertThat((String) response.getEntity()).isEqualTo("plain failure");
    }
  }

  @Test
  @DisplayName("should assign a new id to items without one")
  void shouldAssignNewId_whenItemHasNoId() {
    var item = new Item();

    ensureItemsHaveIds(List.of(item));

    assertThat(UUID.fromString(item.getId())).isNotNull();
  }

  @Test
  @DisplayName("should keep the existing id for items that already have one")
  void shouldKeepExistingId_whenItemAlreadyHasOne() {
    var item = new Item().withId("existing-id");

    ensureItemsHaveIds(List.of(item));

    assertThat(item.getId()).isEqualTo("existing-id");
  }

  @Test
  @DisplayName("should remove read-only fields from the patch's additional properties")
  void shouldRemoveReadOnlyFields_fromPatchAdditionalProperties() {
    var patch = new ItemPatchRequest();
    patch.setAdditionalProperty("metadata", "should be removed");
    patch.setAdditionalProperty("materialType", "should be removed");
    patch.setAdditionalProperty("barcode", "should stay");

    removeReadOnlyFields(patch);

    assertThat(patch.getAdditionalProperties()).containsOnlyKeys("barcode");
  }

  @Test
  @DisplayName("should do nothing when the patch has no additional properties")
  void shouldDoNothing_whenPatchHasNoAdditionalProperties() {
    assertDoesNotThrow(() -> removeReadOnlyFields(new ItemPatchRequest()));
  }

  @Test
  @DisplayName("should assign a new circulation note id when missing")
  void shouldAssignNewCirculationNoteId_whenMissing() {
    var item = new Item().withCirculationNotes(List.of(new CirculationNote()));

    var result = populateCirculationNoteId(item);

    assertThat(UUID.fromString(result.result().getCirculationNotes().getFirst().getId())).isNotNull();
  }

  @Test
  @DisplayName("should keep the existing circulation note id")
  void shouldKeepExistingCirculationNoteId() {
    var item = new Item().withCirculationNotes(List.of(new CirculationNote().withId("existing-note-id")));

    var result = populateCirculationNoteId(item);

    assertThat(result.result().getCirculationNotes().getFirst().getId()).isEqualTo("existing-note-id");
  }

  @Test
  @DisplayName("should do nothing when the item has no circulation notes")
  void shouldDoNothing_whenItemHasNoCirculationNotes() {
    var item = new Item();

    var result = populateCirculationNoteId(item);

    assertThat(result.result()).isSameAs(item);
  }

  private static boolean isItemFieldsAffected(HoldingsRecord holdings, Item item) {
    return invokeStaticPrivate("isItemFieldsAffected",
      new Class<?>[] {HoldingsRecord.class, Item.class}, holdings, item);
  }

  private static Response putFailure(Throwable throwable) {
    return invokeStaticPrivate("putFailure", new Class<?>[] {Throwable.class}, throwable);
  }

  private static void ensureItemsHaveIds(List<Item> items) {
    invokePrivate(uninitializedService(), "ensureItemsHaveIds", new Class<?>[] {List.class}, items);
  }

  private static void removeReadOnlyFields(ItemPatchRequest itemPatch) {
    invokePrivate(uninitializedService(), "removeReadOnlyFields", new Class<?>[] {ItemPatchRequest.class}, itemPatch);
  }

  private static io.vertx.core.Future<Item> populateCirculationNoteId(Item item) {
    return invokePrivate(uninitializedService(), "populateCirculationNoteId", new Class<?>[] {Item.class}, item);
  }

  private static ItemService uninitializedService() {
    return mock(ItemService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
  }

  @SuppressWarnings("unchecked")
  private static <T> T invokeStaticPrivate(String methodName, Class<?>[] paramTypes, Object... args) {
    try {
      Method method = ItemService.class.getDeclaredMethod(methodName, paramTypes);
      method.setAccessible(true);
      return (T) method.invoke(null, args);
    } catch (InvocationTargetException e) {
      throw new RuntimeException(e.getCause());
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> T invokePrivate(Object target, String methodName, Class<?>[] paramTypes, Object... args) {
    try {
      Method method = target.getClass().getDeclaredMethod(methodName, paramTypes);
      method.setAccessible(true);
      return (T) method.invoke(target, args);
    } catch (InvocationTargetException e) {
      throw new RuntimeException(e.getCause());
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }
}
