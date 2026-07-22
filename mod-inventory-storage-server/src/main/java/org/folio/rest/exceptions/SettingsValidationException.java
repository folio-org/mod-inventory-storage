package org.folio.rest.exceptions;

public class SettingsValidationException extends RuntimeException {
  public SettingsValidationException(String message) {
    super(message);
  }
}
