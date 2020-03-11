package org.folio.rest.api;

import org.folio.rest.jaxrs.model.Instance;

public class InstanceStaffSuppressMigrationScriptTest
  extends InstanceDefaultValueMigrationScriptTest {
  private static final String STAFF_SUPPRESS = "staffSuppress";
  private static final String MIGRATION_SCRIPT
    = loadScript("populateStaffSuppressIfNotSet.sql");

  @Override
  public Boolean getFieldValue(Instance instanceInStorage) {
    return instanceInStorage.getStaffSuppress();
  }

  @Override
  public String getFieldName() {
    return STAFF_SUPPRESS;
  }

  @Override
  public String getMigrationScript() {
    return MIGRATION_SCRIPT;
  }
}
