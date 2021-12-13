package org.folio.validator;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.jaxrs.model.Status.Name.CHECKED_OUT;

import java.util.List;

import org.folio.rest.exceptions.ValidationException;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.Parameter;

import io.vertx.core.Future;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ItemValidator {

  public static Future<Item> refuseIfAlreadyCheckedOut(Item item) {
    return item.getStatus().getName() != CHECKED_OUT
      ? succeededFuture(item)
      : failedFuture(new ValidationException(
        new Errors().withErrors(List.of(
          new Error().withMessage("Item " + item.getId() + " is already checked out")
            .withParameters(List.of(
              new Parameter().withKey("status.name").withValue(CHECKED_OUT.value())))))));
  }

}
