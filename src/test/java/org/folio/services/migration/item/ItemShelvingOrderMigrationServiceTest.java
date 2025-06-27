package org.folio.services.migration.item;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.folio.persist.ItemRepository;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.persist.PostgresClient;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class ItemShelvingOrderMigrationServiceTest {
  private final PostgresClient postgresClient = mock(PostgresClient.class);
  private final ItemShelvingOrderMigrationService migrationService =
    new ItemShelvingOrderMigrationService(postgresClient, mock(ItemRepository.class));

  @Parameters({
    "18.2.3, 19.2.0",
    "19.1.1, 20.0.0",
    "20.1.10, 20.2.0",
    "20.2.0, 20.2.1",
    "20.2.0, 20.3.0"
  })
  @Test
  public void shouldTriggerMigration(String from, String to) {
    var ta = new TenantAttributes().withModuleFrom(from).withModuleTo(to);
    assertTrue(migrationService.shouldExecuteMigration(ta));
  }

  @Parameters({
    "20.2.1, 20.2.2",
    "20.3.0, 20.3.2",
    "21.0.0, 21.0.1",
  })
  @Test
  public void shouldNotTriggerMigration(String from, String to) {
    var ta = new TenantAttributes().withModuleFrom(from).withModuleTo(to);
    assertFalse(migrationService.shouldExecuteMigration(ta));
  }
}
