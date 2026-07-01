package org.folio.services.consortium.entities;

public enum Settings {

  INVENTORY_OPTIMIZE_UPDATES_ENABLED("inventory.optimize-updates.enabled");

  private final String value;

  Settings(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}
