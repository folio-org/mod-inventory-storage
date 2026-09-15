package org.folio.services.holding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import org.folio.persist.HoldingsRepository;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.resource.HoldingsStorageBatchSynchronous.PostHoldingsStorageBatchSynchronousResponse;
import org.folio.rest.tools.utils.OptimisticLockingUtil;
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
  @DisplayName("should clear the placeholder version and keep locking enabled when optimistic locking is requested")
  void shouldClearPlaceholderVersion_whenOptimisticLockingRequested() {
    var placeholder = new HoldingsRecord().withVersion(-1);
    var untouched = new HoldingsRecord().withVersion(3);

    var result = handleOptimisticLocking(List.of(placeholder, untouched), true);

    assertThat(result).isNull();
    assertThat(placeholder.getVersion()).isNull();
    assertThat(untouched.getVersion()).isEqualTo(3);
  }

  @Test
  @DisplayName("should mark every holding with the suppression sentinel when locking is disabled and allowed")
  void shouldMarkSuppressionSentinel_whenLockingDisabledAndAllowed() {
    var holding = new HoldingsRecord();
    try (var lockingUtil = mockStatic(OptimisticLockingUtil.class)) {
      lockingUtil.when(OptimisticLockingUtil::isSuppressingOptimisticLockingAllowed).thenReturn(true);

      var result = handleOptimisticLocking(List.of(holding), false);

      assertThat(result).isNull();
      assertThat(holding.getVersion()).isEqualTo(-1);
    }
  }

  @Test
  @DisplayName("should reject the request when locking is disabled but suppression is not allowed")
  void shouldRejectRequest_whenLockingDisabledButSuppressionNotAllowed() {
    var holding = new HoldingsRecord();
    try (var lockingUtil = mockStatic(OptimisticLockingUtil.class)) {
      lockingUtil.when(OptimisticLockingUtil::isSuppressingOptimisticLockingAllowed).thenReturn(false);

      var result = handleOptimisticLocking(List.of(holding), false);

      assertThat(result).isNotNull();
      var response = result.result();
      assertThat(response.getStatus())
        .isEqualTo(PostHoldingsStorageBatchSynchronousResponse.respond413WithTextPlain("x").getStatus());
      assertThat(holding.getVersion()).isNull();
    }
  }

  @Test
  @DisplayName("should return an empty result when the query is null")
  void shouldReturnEmptyResult_whenQueryIsNull() {
    var service = uninitializedService();

    var result = service.getByInstanceId(0, 10, null);

    assertThat(result.result()).isNull();
  }

  @Test
  @DisplayName("should return an empty result when the query does not match the instanceId-sortBy shape")
  void shouldReturnEmptyResult_whenQueryDoesNotMatch() {
    var service = uninitializedService();

    var result = service.getByInstanceId(0, 10, "title == foo");

    assertThat(result.result()).isNull();
  }

  @Test
  @DisplayName("should extract the instance id and sort fields when the query matches")
  void shouldExtractInstanceIdAndSortFields_whenQueryMatches() throws Exception {
    var service = uninitializedService();
    var holdingsRepository = mock(HoldingsRepository.class);
    injectRepository(service, holdingsRepository);
    var row = mock(io.vertx.sqlclient.Row.class);
    when(row.getString("holdings")).thenReturn("[]");
    when(row.getLong("total_records")).thenReturn(0L);
    // The sortBy capture group includes its own leading space before the first field, so
    // splitting on runs of spaces yields a leading empty element - documenting current
    // behavior, not asserting it's ideal.
    when(holdingsRepository.getByInstanceId(
      "11111111-1111-1111-1111-111111111111", new String[] {"", "callNumber", "callNumberSuffix"}, 0, 10))
      .thenReturn(io.vertx.core.Future.succeededFuture(row));

    var result = service.getByInstanceId(0, 10,
      "instanceId == \"11111111-1111-1111-1111-111111111111\" sortBy callNumber callNumberSuffix");

    assertThat(result.result().getStatus()).isEqualTo(200);
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

  private static io.vertx.core.Future<javax.ws.rs.core.Response> handleOptimisticLocking(
    List<HoldingsRecord> holdings, boolean optimisticLocking) {
    return invokePrivate(uninitializedService(), "handleOptimisticLocking",
      new Class<?>[] {List.class, boolean.class}, holdings, optimisticLocking);
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

  private static void injectRepository(Object target, Object value) throws ReflectiveOperationException {
    Field field = target.getClass().getDeclaredField("holdingsRepository");
    field.setAccessible(true);
    field.set(target, value);
  }
}
