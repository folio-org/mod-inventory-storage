package org.folio.services.instance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.withSettings;

import io.vertx.core.json.JsonObject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.ws.rs.core.Response;
import org.folio.rest.impl.StorageHelper;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.InstancePatchRequest;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.tools.utils.OptimisticLockingUtil;
import org.folio.services.caches.ConsortiumData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * InstanceService's constructor self-constructs 7 dependencies from a real Context, so this
 * targets its logic-bearing private helpers via reflection on a constructor-bypassed instance
 * (same approach as HoldingsServiceTest) rather than attempting full public-API orchestration,
 * which the class's dependency shape makes disproportionately expensive relative to the
 * end-to-end coverage InstanceStorageIT already provides.
 */
class InstanceServiceTest {

  @Test
  @DisplayName("should keep the old instance unchanged when the new source is a consortium shadow copy")
  void shouldKeepOldInstanceUnchanged_whenNewSourceIsConsortiumShadow() {
    var oldInstance = new Instance().withHrid("old-hrid");
    var newInstance = new Instance().withHrid("different-hrid").withSource("CONSORTIUM-MARC");

    var result = validateHridChange(oldInstance, newInstance);

    assertThat(result.result()).isSameAs(oldInstance);
  }

  @Test
  @DisplayName("should reject a changed hrid when the source is not a consortium shadow copy")
  void shouldRejectChangedHrid_whenSourceIsNotConsortiumShadow() {
    var oldInstance = new Instance().withHrid("old-hrid");
    var newInstance = new Instance().withHrid("different-hrid").withSource("MARC");

    var result = validateHridChange(oldInstance, newInstance);

    assertThat(result.failed()).isTrue();
  }

  @Test
  @DisplayName("should identify the tenant as central when it matches the consortium's central tenant")
  void shouldIdentifyTenantAsCentral_whenItMatchesConsortiumCentralTenant() {
    var consortiumData = new ConsortiumData("central-tenant", "consortium-1", List.of());

    assertThat(isCentralTenantId("central-tenant", consortiumData)).isTrue();
  }

  @Test
  @DisplayName("should not identify the tenant as central when it does not match the consortium's central tenant")
  void shouldNotIdentifyTenantAsCentral_whenItDoesNotMatchConsortiumCentralTenant() {
    var consortiumData = new ConsortiumData("central-tenant", "consortium-1", List.of());

    assertThat(isCentralTenantId("member-tenant", consortiumData)).isFalse();
  }

  @Test
  @DisplayName("should assign a new id to instances without one")
  void shouldAssignNewId_whenInstanceHasNoId() {
    var instance = new Instance();

    setMissingInstanceIds(List.of(instance));

    assertThat(UUID.fromString(instance.getId())).isNotNull();
  }

  @Test
  @DisplayName("should keep the existing id for instances that already have one")
  void shouldKeepExistingId_whenInstanceAlreadyHasOne() {
    var instance = new Instance().withId("existing-id");

    setMissingInstanceIds(List.of(instance));

    assertThat(instance.getId()).isEqualTo("existing-id");
  }

  @Test
  @DisplayName("should do nothing when the instance list is null")
  void shouldDoNothing_whenInstanceListIsNull() {
    Assertions.assertDoesNotThrow(() -> setMissingInstanceIds(null));
  }

  @Test
  @DisplayName("should pass validation when the batch is within the max entities limit")
  void shouldPassValidation_whenBatchIsWithinMaxEntitiesLimit() {
    var instances = Collections.nCopies(StorageHelper.MAX_ENTITIES, new Instance());

    assertThat(validateInstancesSize(instances)).isNull();
  }

  @Test
  @DisplayName("should reject the batch when it exceeds the max entities limit")
  void shouldRejectBatch_whenItExceedsMaxEntitiesLimit() {
    var instances = Collections.nCopies(StorageHelper.MAX_ENTITIES + 1, new Instance());

    var result = validateInstancesSize(instances);

    assertThat(result).isNotNull();
    assertThat(result.result().getStatus()).isEqualTo(413);
  }

  @Test
  @DisplayName("should unset the placeholder version when optimistic locking is requested")
  void shouldUnsetPlaceholderVersion_whenOptimisticLockingRequested() {
    var instance = new Instance().withVersion(-1);

    var result = handleInstanceOptimisticLocking(List.of(instance), true);

    assertThat(result).isNull();
    assertThat(instance.getVersion()).isNull();
  }

  @Test
  @DisplayName("should mark every instance with the suppression sentinel when locking is disabled and allowed")
  void shouldMarkSuppressionSentinel_whenLockingDisabledAndAllowed() {
    var instance = new Instance();
    try (var lockingUtil = mockStatic(OptimisticLockingUtil.class)) {
      lockingUtil.when(OptimisticLockingUtil::isSuppressingOptimisticLockingAllowed).thenReturn(true);

      var result = handleInstanceOptimisticLocking(List.of(instance), false);

      assertThat(result).isNull();
      lockingUtil.verify(() -> OptimisticLockingUtil.setVersionToMinusOne(List.of(instance)));
    }
  }

  @Test
  @DisplayName("should reject the request when locking is disabled but suppression is not allowed")
  void shouldRejectRequest_whenLockingDisabledButSuppressionNotAllowed() {
    var instance = new Instance();
    try (var lockingUtil = mockStatic(OptimisticLockingUtil.class)) {
      lockingUtil.when(OptimisticLockingUtil::isSuppressingOptimisticLockingAllowed).thenReturn(false);

      var result = handleInstanceOptimisticLocking(List.of(instance), false);

      assertThat(result).isNotNull();
      assertThat(result.result().getStatus()).isEqualTo(413);
    }
  }

  @Test
  @DisplayName("should stamp the metadata updated date and user when applying a patch")
  void shouldStampMetadataUpdatedDateAndUser_whenApplyingPatch() {
    var instance = new Instance().withId("id-1").withTitle("old title")
      .withMetadata(new Metadata().withUpdatedDate(new Date(0)));
    var patch = new InstancePatchRequest();

    var result = applyPatch(instance, JsonObject.mapFrom(patch), "user-1");

    assertThat(result.result().getMetadata().getUpdatedByUserId()).isEqualTo("user-1");
    assertThat(result.result().getMetadata().getUpdatedDate()).isNotEqualTo(new Date(0));
  }

  @Test
  @DisplayName("should reject a blank cql when deleting instances by query")
  void shouldRejectBlankCql_whenDeletingInstancesByQuery() {
    var service = mock(InstanceService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));

    var result = service.deleteInstances("   ");

    assertThat(result.result().getStatus()).isEqualTo(400);
  }

  private static io.vertx.core.Future<Instance> validateHridChange(Instance oldInstance, Instance newInstance) {
    return invokePrivate(uninitializedService(), "validateHridChange",
      new Class<?>[] {Instance.class, Instance.class}, oldInstance, newInstance);
  }

  private static boolean isCentralTenantId(String tenantId, ConsortiumData consortiumData) {
    return invokePrivate(uninitializedService(), "isCentralTenantId",
      new Class<?>[] {String.class, ConsortiumData.class}, tenantId, consortiumData);
  }

  private static void setMissingInstanceIds(List<Instance> instances) {
    invokePrivate(uninitializedService(), "setMissingInstanceIds", new Class<?>[] {List.class}, instances);
  }

  private static io.vertx.core.Future<Response> validateInstancesSize(List<Instance> instances) {
    return invokePrivate(uninitializedService(), "validateInstancesSize", new Class<?>[] {List.class}, instances);
  }

  private static io.vertx.core.Future<Response> handleInstanceOptimisticLocking(
    List<Instance> instances, boolean optimisticLocking) {
    return invokePrivate(uninitializedService(), "handleInstanceOptimisticLocking",
      new Class<?>[] {List.class, boolean.class}, instances, optimisticLocking);
  }

  private static io.vertx.core.Future<Instance> applyPatch(
    Instance instance, JsonObject patchJson, String userId) {
    return invokePrivate(uninitializedService(), "applyPatch",
      new Class<?>[] {Instance.class, JsonObject.class, String.class}, instance, patchJson, userId);
  }

  private static InstanceService uninitializedService() {
    return mock(InstanceService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
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
