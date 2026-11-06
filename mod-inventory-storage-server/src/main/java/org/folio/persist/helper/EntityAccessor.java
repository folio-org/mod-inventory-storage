package org.folio.persist.helper;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Reads and writes the id and the optimistic locking version of an entity without reflection.
 *
 * @param <T> the entity type
 */
public final class EntityAccessor<T> {

  private static final Integer MINUS_ONE = -1;

  private final Function<T, String> idGetter;
  private final BiConsumer<T, String> idSetter;
  private final Function<T, Integer> versionGetter;
  private final BiConsumer<T, Integer> versionSetter;

  private EntityAccessor(Function<T, String> idGetter, BiConsumer<T, String> idSetter,
                         Function<T, Integer> versionGetter, BiConsumer<T, Integer> versionSetter) {
    this.idGetter = idGetter;
    this.idSetter = idSetter;
    this.versionGetter = versionGetter;
    this.versionSetter = versionSetter;
  }

  /**
   * Accessor of an entity without a version.
   */
  public static <T> EntityAccessor<T> of(Function<T, String> idGetter, BiConsumer<T, String> idSetter) {
    return new EntityAccessor<>(idGetter, idSetter, entity -> null, (entity, version) -> { });
  }

  public static <T> EntityAccessor<T> of(Function<T, String> idGetter,
                                         BiConsumer<T, String> idSetter,
                                         Function<T, Integer> getter,
                                         BiConsumer<T, Integer> setter) {
    return new EntityAccessor<>(idGetter, idSetter, getter, setter);
  }

  /**
   * Accessor of an entity that is only read, using its id fails.
   */
  public static <T> EntityAccessor<T> unsupported(Class<T> type) {
    return new EntityAccessor<>(
      entity -> {
        throw new UnsupportedOperationException(type.getName() + " has no id accessor");
      },
      (entity, id) -> {
        throw new UnsupportedOperationException(type.getName() + " has no id accessor");
      },
      entity -> null, (entity, version) -> { });
  }

  public String id(T entity) {
    return idGetter.apply(entity);
  }

  public void setId(T entity, String id) {
    idSetter.accept(entity, id);
  }

  /**
   * Sets the version to null if it is -1, this enforces optimistic locking as -1 disables it.
   */
  public void unsetVersionIfMinusOne(T entity) {
    if (MINUS_ONE.equals(versionGetter.apply(entity))) {
      versionSetter.accept(entity, null);
    }
  }

  /**
   * Sets the version to -1, this disables optimistic locking.
   */
  public void setVersionToMinusOne(T entity) {
    versionSetter.accept(entity, MINUS_ONE);
  }
}
