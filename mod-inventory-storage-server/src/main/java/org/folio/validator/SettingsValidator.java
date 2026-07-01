package org.folio.validator;

import org.folio.rest.exceptions.SettingsValidationException;
import org.folio.rest.jaxrs.model.Setting;
import org.jspecify.annotations.NonNull;

public class SettingsValidator {

  public void validate(@NonNull Object value, @NonNull Setting entity) {
    switch (entity.getType()) {
      case STRING -> validateType(value, String.class, "Setting value should be a string");
      case INTEGER -> validateType(value, Integer.class, "Setting value should be an integer");
      case BOOLEAN -> validateType(value, Boolean.class, "Setting value should be a boolean");
      default -> throw new IllegalStateException("Unexpected value: " + entity.getType());
    }
  }

  private <T> void validateType(Object value, Class<T> type, String errorMessage) {
    if (!type.isInstance(value)) {
      throw new SettingsValidationException(errorMessage);
    }
  }
}
