package org.folio.services.migration;

public enum MigrationName {
  SUBJECT_SERIES_MIGRATION("subjectSeriesMigration"),
  ITEM_SHELVING_ORDER_MIGRATION("itemShelvingOrderMigration");

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
