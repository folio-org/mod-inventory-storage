package org.folio.services.holding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.rest.tools.utils.TenantTool.tenantId;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import io.vertx.core.Context;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.folio.persist.HoldingsRepository;
import org.folio.persist.InstanceRepository;
import org.folio.persist.ItemRepository;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.services.HridManager;
import org.folio.services.ItemEffectiveValuesService;
import org.folio.services.consortium.ConsortiumService;
import org.folio.services.item.ItemService;
import org.folio.services.reindex.ReindexExportOrchestrator;
import org.folio.services.sanitizer.Sanitizer;
import org.folio.services.setting.SettingsService;
import org.folio.support.integration.TestRailCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * HoldingsService's constructor builds 9 dependencies directly from a real Context, so full
 * public-API orchestration tests would need either a production refactor for testability or
 * mockConstruction chaining across all 9 - disproportionate for a class already exercised
 * end-to-end by HoldingsStorageIT. Instead, this targets its genuinely logic-bearing private
 * helpers via reflection on a constructor-bypassed instance (Mockito never runs the real
 * constructor for a class mock), which is safe as long as the method under test doesn't touch
 * an uninitialized field - true for all of these.
 */
class HoldingsServiceTest {

  @Test
  @DisplayName("should require an item update when there is no old holdings record")
  void shouldRequireItemUpdate_whenNoOldHoldingsRecord() {
    assertThat(shouldUpdateItems(null, new HoldingsRecord())).isTrue();
  }

  @Test
  @DisplayName("should not require an item update when nothing item-relevant changed")
  void shouldNotRequireItemUpdate_whenNothingRelevantChanged() {
    var holding = identicalHoldingPair();

    assertThat(shouldUpdateItems(holding, holding)).isFalse();
  }

  @Test
  @DisplayName("should require an item update when the instance id changed")
  void shouldRequireItemUpdate_whenInstanceIdChanged() {
    var oldHolding = new HoldingsRecord().withInstanceId("instance-1");
    var newHolding = new HoldingsRecord().withInstanceId("instance-2");

    assertThat(shouldUpdateItems(oldHolding, newHolding)).isTrue();
  }

  @Test
  @DisplayName("should require an item update when the call number changed")
  void shouldRequireItemUpdate_whenCallNumberChanged() {
    var oldHolding = new HoldingsRecord().withCallNumber("A1");
    var newHolding = new HoldingsRecord().withCallNumber("A2");

    assertThat(shouldUpdateItems(oldHolding, newHolding)).isTrue();
  }

  @Test
  @TestRailCase(388513)
  @DisplayName("should not require an item update when only the ILL policy or acquisition method changed")
  void shouldNotRequireItemUpdate_whenOnlyIllPolicyOrAcquisitionMethodChanged() {
    var oldHolding = identicalHoldingPair().withIllPolicyId("ill-policy-1").withAcquisitionMethod("gift");
    var newHolding = identicalHoldingPair().withIllPolicyId("ill-policy-2").withAcquisitionMethod("purchase");

    assertThat(shouldUpdateItems(oldHolding, newHolding)).isFalse();
  }

  @Test
  @DisplayName("should use the temporary location when present")
  void shouldUseTemporaryLocation_whenPresent() {
    var holding = new HoldingsRecord()
      .withPermanentLocationId("permanent-1")
      .withTemporaryLocationId("temporary-1");

    assertThat(calculateEffectiveLocation(holding)).isEqualTo("temporary-1");
  }

  @Test
  @DisplayName("should fall back to the permanent location when no temporary location is set")
  void shouldFallBackToPermanentLocation_whenNoTemporaryLocation() {
    var holding = new HoldingsRecord().withPermanentLocationId("permanent-1");

    assertThat(calculateEffectiveLocation(holding)).isEqualTo("permanent-1");
  }

  @Test
  @DisplayName("should assign a new id to holdings without one")
  void shouldAssignNewId_whenHoldingHasNoId() {
    var holding = new HoldingsRecord();

    ensureHoldingsHaveIds(List.of(holding));

    assertThat(holding.getId()).isNotNull();
    assertThat(UUID.fromString(holding.getId())).isNotNull();
  }

  @Test
  @DisplayName("should keep the existing id for holdings that already have one")
  void shouldKeepExistingId_whenHoldingAlreadyHasOne() {
    var holding = new HoldingsRecord().withId("existing-id");

    ensureHoldingsHaveIds(List.of(holding));

    assertThat(holding.getId()).isEqualTo("existing-id");
  }

  @Test
  @DisplayName("should return an empty result when the query is null")
  void shouldReturnEmptyResult_whenQueryIsNull() {
    var service = uninitializedService();

    var result = service.getByInstanceId(0, 10, null, Map.of());

    assertThat(result.result()).isNull();
  }

  @Test
  @DisplayName("should return an empty result when the query does not match the instanceId-sortBy shape")
  void shouldReturnEmptyResult_whenQueryDoesNotMatch() {
    var service = uninitializedService();

    var result = service.getByInstanceId(0, 10, "title == foo", Map.of());

    assertThat(result.result()).isNull();
  }

  @Test
  @DisplayName("should extract the instance id and sort fields when the query matches")
  void shouldExtractInstanceIdAndSortFields_whenQueryMatches() {
    var holdingsRepository = mock(HoldingsRepository.class);
    var service = serviceWith(holdingsRepository);
    var row = mock(io.vertx.sqlclient.Row.class);
    when(row.getString("holdings")).thenReturn("[]");
    when(row.getLong("total_records")).thenReturn(0L);
    // The sortBy capture group includes its own leading space before the first field, so
    // splitting on runs of spaces yields a leading empty element - documenting current
    // behavior, not asserting it's ideal.
    when(holdingsRepository.getByInstanceId("11111111-1111-1111-1111-111111111111",
      new String[] {"", "callNumber", "callNumberSuffix"}, 0, 10, tenantId(Map.of())))
      .thenReturn(io.vertx.core.Future.succeededFuture(row));

    var result = service.getByInstanceId(0, 10,
      "instanceId == \"11111111-1111-1111-1111-111111111111\" sortBy callNumber callNumberSuffix", Map.of());

    assertThat(result.result()).contains("\"holdingsRecords\": []");
  }

  private static HoldingsService serviceWith(HoldingsRepository holdingsRepository) {
    return new HoldingsService(mock(Context.class), mock(ItemService.class), mock(ConsortiumService.class),
      mock(ItemEffectiveValuesService.class), mock(SettingsService.class), mock(HoldingsEventPublisher.class),
      mock(ReindexExportOrchestrator.class), holdingsRepository, mock(InstanceRepository.class),
      mock(ItemRepository.class), mock(HridManager.class), mock(Sanitizer.class));
  }

  private static HoldingsRecord identicalHoldingPair() {
    return new HoldingsRecord()
      .withInstanceId("instance-1")
      .withPermanentLocationId("permanent-1")
      .withTemporaryLocationId("temporary-1")
      .withCallNumber("A1")
      .withCallNumberPrefix("prefix")
      .withCallNumberSuffix("suffix")
      .withCallNumberTypeId("type-1");
  }

  private static HoldingsService uninitializedService() {
    return mock(HoldingsService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
  }

  private static boolean shouldUpdateItems(HoldingsRecord oldHoldings, HoldingsRecord newHoldings) {
    return invokePrivate(uninitializedService(), "shouldUpdateItems",
      new Class<?>[] {HoldingsRecord.class, HoldingsRecord.class}, oldHoldings, newHoldings);
  }

  private static String calculateEffectiveLocation(HoldingsRecord holding) {
    return invokePrivate(uninitializedService(), "calculateEffectiveLocation",
      new Class<?>[] {HoldingsRecord.class}, holding);
  }

  private static void ensureHoldingsHaveIds(List<HoldingsRecord> holdings) {
    invokePrivate(uninitializedService(), "ensureHoldingsHaveIds", new Class<?>[] {List.class}, holdings);
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
