package org.folio.rest.exceptions;

import lombok.Getter;
import org.folio.rest.jaxrs.model.Errors;

@Getter
public final class ValidationException extends InventoryProcessingException {
  private final transient Errors errors;

  public ValidationException(Errors errors) {
    super("Validation exception: " + errors);
    this.errors = errors;
  }

}
