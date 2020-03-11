package org.folio.rest.api;

import org.folio.rest.jaxrs.model.Instance;

public class InstanceDiscoverySuppressMigrationScriptTest
  extends InstanceDefaultValueMigrationScriptTest {

  private static final String DISCOVERY_SUPPRESS = "discoverySuppress";
  private static final String MIGRATION_SCRIPT
    = loadScript("populateDiscoverySuppressIfNotSet.sql");

  @Override
  public Boolean getFieldValue(Instance instanceInStorage) {
    return instanceInStorage.getDiscoverySuppress();
  }

  @Override
  public String getFieldName() {
    return DISCOVERY_SUPPRESS;
  }

  @Override
  public String getMigrationScript() {
    return MIGRATION_SCRIPT;
  }
}
