package org.folio.validator;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.lang.String.format;

import io.vertx.core.Future;
import java.util.Objects;
import java.util.function.Function;
import org.folio.rest.exceptions.BadRequestException;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.Item;

public final class HridValidators {
  private HridValidators() { }

  public static Future<Instance> refuseWhenHridChanged(Instance oldEntity, Instance newEntity) {
    return refuseWhenHridChanged(oldEntity, newEntity, Instance::getHrid);
  }

  public static Future<HoldingsRecord> refuseWhenHridChanged(
    HoldingsRecord oldEntity, HoldingsRecord newEntity) {

    return refuseWhenHridChanged(oldEntity, newEntity, HoldingsRecord::getHrid);
  }

  public static Future<Item> refuseWhenHridChanged(Item oldEntity, Item newEntity) {
    return refuseWhenHridChanged(oldEntity, newEntity, Item::getHrid);
  }

  private static <T> Future<T> refuseWhenHridChanged(
    T oldEntity, T newEntity, Function<T, String> getHrid) {

    var oldHrid = getHrid.apply(oldEntity);
    var newHrid = getHrid.apply(newEntity);

    if (Objects.equals(oldHrid, newHrid)) {
      return succeededFuture(oldEntity);
    } else {
      return failedFuture(new BadRequestException(format(
        "The hrid field cannot be changed: new=%s, old=%s", newHrid, oldHrid)));
    }
  }
}
