package org.folio.validator;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;

import io.vertx.core.Future;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.rest.exceptions.BadRequestException;
import org.folio.rest.exceptions.NotFoundException;
import org.folio.util.UuidUtil;

public final class CommonValidators {
  private CommonValidators() { }

  public static <T> Future<T> refuseIfNotFound(T entity) {
    return entity != null ? succeededFuture(entity) : failedFuture(new NotFoundException("Not found"));
  }

  public static Future<Void> validateUuidFormat(Set<String> ids) {
    if (CollectionUtils.isNotEmpty(ids)) {
      for (var id : ids) {
        if (!UuidUtil.isUuid(id)) {
          return failedFuture(new BadRequestException(String.format("invalid input syntax for type uuid: \"%s\"", id)));
        }
      }
    }
    return succeededFuture();
  }

  public static <T> Future<Void> validateUuidFormatForList(List<T> entities, Function<T, Set<String>> uuidExtractor) {
    return entities.stream()
      .map(entity -> validateUuidFormat(uuidExtractor.apply(entity)))
      .filter(Future::failed)
      .findFirst()
      .orElse(succeededFuture());
  }
}
