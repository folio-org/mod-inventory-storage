package org.folio.services.migration.async;

public class InvalidMigrationRequestException extends IllegalArgumentException {

  public InvalidMigrationRequestException(String message) {
    super(message);
  }
}
