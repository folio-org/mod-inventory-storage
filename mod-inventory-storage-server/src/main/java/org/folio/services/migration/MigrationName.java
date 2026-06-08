package org.folio.services.migration;

public enum MigrationName {

  ITEM_ORDER_MIGRATION("itemOrderMigration");

  private final String value;

  MigrationName(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  @Override
  public String toString() {
    return String.valueOf(value);
  }
}
