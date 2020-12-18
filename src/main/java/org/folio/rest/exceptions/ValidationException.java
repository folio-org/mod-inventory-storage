package org.folio.rest.exceptions;

import org.folio.rest.jaxrs.model.Errors;

public final class ValidationException extends InventoryProcessingException {
  private final transient Errors errors;

  public ValidationException(Errors errors) {
    super("Validation exception: " + errors);
    this.errors = errors;
  }

  public Errors getErrors() {
    return errors;
  }
}
