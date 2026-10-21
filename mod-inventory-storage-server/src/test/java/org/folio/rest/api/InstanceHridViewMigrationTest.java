package org.folio.rest.api;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.Test;

public class InstanceHridViewMigrationTest extends MigrationTestBase {
  private static final String MIGRATION_SCRIPT =
    loadScript("inventory-hierarchy/addInstanceHridToInstanceHoldingsItemView.sql");

  @Test
  public void shouldExposeLowerCasedUnaccentedHridInInstanceHridColumn() {
    var id = randomUUID();
    instancesClient.create(instance(id).put("hrid", "ÉCLAIR-Migration-1"));

    executeMultipleSqlStatements(MIGRATION_SCRIPT);

    var rows = executeSelect("SELECT instance_hrid FROM ${myuniversity}_${mymodule}.instance_holdings_item_view "
      + "WHERE id = '%s'", id);

    assertThat(rows.size(), is(1));
    assertThat(rows.iterator().next().getString("instance_hrid"), is("eclair-migration-1"));
  }

  @Test
  public void shouldBeRepeatableWhenScriptIsRunTwice() {
    var id = randomUUID();
    instancesClient.create(instance(id).put("hrid", "Migration-Repeat-1"));

    executeMultipleSqlStatements(MIGRATION_SCRIPT);
    executeMultipleSqlStatements(MIGRATION_SCRIPT);

    var rows = executeSelect("SELECT instance_hrid FROM ${myuniversity}_${mymodule}.instance_holdings_item_view "
      + "WHERE instance_hrid = 'migration-repeat-1'");

    assertThat(rows.size(), is(1));
  }
}
