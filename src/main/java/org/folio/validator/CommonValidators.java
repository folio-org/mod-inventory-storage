package org.folio.validator;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;

import io.vertx.core.Future;
import org.folio.rest.exceptions.NotFoundException;

public final class CommonValidators {
  private CommonValidators() { }

  public static <T> Future<T> refuseIfNotFound(T entity) {
    return entity != null ? succeededFuture(entity) : failedFuture(new NotFoundException("Not found"));
  }
}
