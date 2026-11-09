package org.folio.rest.exceptions;

public final class RequestEntityTooLargeException extends InventoryProcessingException {
  public RequestEntityTooLargeException(Object message) {
    super(message);
  }
}
