package org.folio.rest.exceptions;

public final class BadRequestException extends InventoryProcessingException {
  public BadRequestException(Object message) {
    super(message);
  }
}
