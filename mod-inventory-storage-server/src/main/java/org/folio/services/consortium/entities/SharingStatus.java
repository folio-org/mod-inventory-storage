package org.folio.services.consortium.entities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum SharingStatus {
  COMPLETE("COMPLETE"),

  ERROR("ERROR"),

  IN_PROGRESS("IN_PROGRESS");

  private final String value;

  SharingStatus(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  @Override
  public String toString() {
    return String.valueOf(value);
  }

  @JsonCreator
  public static SharingStatus fromValue(String value) {
    for (SharingStatus b : SharingStatus.values()) {
      if (b.value.equals(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }
}
