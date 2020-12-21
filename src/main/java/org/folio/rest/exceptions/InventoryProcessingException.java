package org.folio.rest.exceptions;

import java.util.Objects;

public abstract class InventoryProcessingException extends RuntimeException {
  protected InventoryProcessingException(Object message) {
    super(Objects.toString(message));
  }
}
