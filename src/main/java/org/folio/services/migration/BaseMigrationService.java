package org.folio.services.migration;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.vertx.core.Future;
import org.folio.dbschema.Versioned;
import org.folio.rest.jaxrs.model.TenantAttributes;

public abstract class BaseMigrationService {
  private final Versioned version;

  protected BaseMigrationService(String fromVersion) {
    this.version = versioned(fromVersion);
  }

  public boolean shouldExecuteMigration(TenantAttributes tenantAttributes) {
    return isNotBlank(tenantAttributes.getModuleFrom())
      && version.isNewForThisInstall(tenantAttributes.getModuleFrom());
  }

  public abstract Future<Void> runMigration();

  private static Versioned versioned(String version) {
    var versioned = new Versioned() {};
    versioned.setFromModuleVersion(version);
    return versioned;
  }
}
