package org.folio.validator;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;

import org.folio.rest.exceptions.NotFoundException;

import io.vertx.core.Future;

public final class CommonValidators {
  private CommonValidators() {}

  public static <T> Future<T> refuseIfNotFound(T record) {
    return record != null ? succeededFuture(record) : failedFuture(new NotFoundException("Not found"));
  }
}
