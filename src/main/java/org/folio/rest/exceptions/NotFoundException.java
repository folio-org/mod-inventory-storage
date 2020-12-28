package org.folio.rest.exceptions;

public class NotFoundException extends InventoryProcessingException {
  public NotFoundException(Object message) {
    super(message);
  }
}
