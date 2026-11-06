package org.folio.persist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

import java.util.Arrays;
import java.util.List;
import org.folio.persist.helper.EntityAccessor;
import org.folio.persist.helper.EntityAccessors;
import org.folio.persist.helper.OptimisticLocking;
import org.folio.rest.exceptions.RequestEntityTooLargeException;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.tools.utils.OptimisticLockingUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OptimisticLockingTest {

  private static final EntityAccessor<Item> ITEM = EntityAccessors.of(Item.class);

  @Test
  @DisplayName("should unset only the version minus one when optimistic locking is enabled")
  void shouldUnsetOnlyVersionMinusOne_whenOptimisticLockingIsEnabled() {
    var disabled = new Item().withVersion(-1);
    var enforced = new Item().withVersion(4);
    var unversioned = new Item();

    OptimisticLocking.apply(ITEM, List.of(disabled, enforced, unversioned), true);

    assertThat(disabled.getVersion()).isNull();
    assertThat(enforced.getVersion()).isEqualTo(4);
    assertThat(unversioned.getVersion()).isNull();
  }

  @Test
  @DisplayName("should accept null records and skip null elements when optimistic locking is enabled")
  void shouldAcceptNullRecordsAndElements_whenOptimisticLockingIsEnabled() {
    var item = new Item().withVersion(-1);

    assertThatCode(() -> OptimisticLocking.apply(ITEM, null, true)).doesNotThrowAnyException();
    assertThatCode(() -> OptimisticLocking.apply(ITEM, Arrays.asList(null, item), true))
      .doesNotThrowAnyException();
    assertThat(item.getVersion()).isNull();
  }

  @Test
  @DisplayName("should ignore entities without a version when optimistic locking is enabled")
  void shouldIgnoreEntitiesWithoutVersion_whenOptimisticLockingIsEnabled() {
    var location = new Location();

    OptimisticLocking.apply(EntityAccessors.of(Location.class), List.of(location), true);

    assertThat(location).isEqualTo(new Location());
  }

  @Test
  @DisplayName("should set the version minus one on every record when locking is disabled and allowed")
  void shouldSetVersionMinusOne_whenLockingDisabledAndAllowed() {
    var first = new Item();
    var second = new Item().withVersion(2);
    try (var lockingUtil = mockStatic(OptimisticLockingUtil.class)) {
      lockingUtil.when(OptimisticLockingUtil::isSuppressingOptimisticLockingAllowed).thenReturn(true);

      OptimisticLocking.apply(ITEM, List.of(first, second), false);
    }

    assertThat(first.getVersion()).isEqualTo(-1);
    assertThat(second.getVersion()).isEqualTo(-1);
  }

  @Test
  @DisplayName("should reject the request and keep the versions when locking is disabled but not allowed")
  void shouldRejectRequest_whenLockingDisabledButNotAllowed() {
    var item = new Item().withVersion(2);
    try (var lockingUtil = mockStatic(OptimisticLockingUtil.class)) {
      lockingUtil.when(OptimisticLockingUtil::isSuppressingOptimisticLockingAllowed).thenReturn(false);

      var itemsToProcess = List.of(item);
      assertThatThrownBy(() -> OptimisticLocking.apply(ITEM, itemsToProcess, false))
        .isInstanceOf(RequestEntityTooLargeException.class)
        .hasMessageContaining("DB_ALLOW_SUPPRESS_OPTIMISTIC_LOCKING");
    }

    assertThat(item.getVersion()).isEqualTo(2);
  }
}
