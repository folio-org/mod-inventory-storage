package org.folio.rest.exceptions;

import static java.util.stream.Collectors.joining;

import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;

public final class ValidationException extends InventoryProcessingException {
  private final transient Errors errors;

  public ValidationException(Errors errors) {
    super("Validation exception: " + errors.getErrors()
      .stream()
      .map(Error::getMessage)
      .collect(joining("; "))
    );
    this.errors = errors;
  }

  public Errors getErrors() {
    return errors;
  }
}
