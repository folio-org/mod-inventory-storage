package org.folio.persist.helper;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.folio.rest.exceptions.RequestEntityTooLargeException;
import org.folio.rest.tools.utils.OptimisticLockingUtil;

/**
 * Prepares the version of the records of a batch for optimistic locking.
 */
public final class OptimisticLocking {

  static final String SUPPRESSING_NOT_ALLOWED =
    "DB_ALLOW_SUPPRESS_OPTIMISTIC_LOCKING environment variable doesn't allow to disable optimistic locking";

  private OptimisticLocking() {
  }

  /**
   * Enforces optimistic locking by unsetting the version -1, or disables it by setting the version -1.
   *
   * @param optimisticLocking false disables optimistic locking, this is only allowed if the environment says so
   * @throws RequestEntityTooLargeException when disabling is requested but the environment doesn't allow it
   */
  public static <T> void apply(EntityAccessor<T> accessor, List<T> records, boolean optimisticLocking) {
    if (optimisticLocking) {
      forEachRecord(records, accessor::unsetVersionIfMinusOne);
      return;
    }
    if (!OptimisticLockingUtil.isSuppressingOptimisticLockingAllowed()) {
      throw new RequestEntityTooLargeException(SUPPRESSING_NOT_ALLOWED);
    }
    forEachRecord(records, accessor::setVersionToMinusOne);
  }

  private static <T> void forEachRecord(List<T> records, Consumer<T> action) {
    if (records != null) {
      records.stream().filter(Objects::nonNull).forEach(action);
    }
  }
}
