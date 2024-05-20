package org.folio.validator;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;

import io.vertx.core.Future;
import java.util.function.Function;
import org.folio.rest.exceptions.BadRequestException;
import org.folio.rest.jaxrs.model.HoldingsRecord;

public final class SourceIdValidators {
  private SourceIdValidators() { }

  public static Future<HoldingsRecord> refuseIfNoSourceId(HoldingsRecord oldEntity, HoldingsRecord newEntity) {
    return refuseIfNoSourceId(oldEntity, newEntity, HoldingsRecord::getSourceId);
  }

  private static <T> Future<T> refuseIfNoSourceId(T oldEntity, T newEntity, Function<T, String> getSourceId) {
    var newSourceId = getSourceId.apply(newEntity);
    if (newSourceId != null) {
      return succeededFuture(oldEntity);
    } else {
      return failedFuture(new BadRequestException("The sourceId field required: cannot be null or deleted"));
    }
  }
}
