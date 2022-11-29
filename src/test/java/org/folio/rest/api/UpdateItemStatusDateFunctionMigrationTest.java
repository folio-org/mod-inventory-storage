package org.folio.rest.api;

import static org.folio.rest.support.matchers.DateTimeMatchers.withinSecondsBeforeNowAsString;
import static org.folio.utility.ModuleUtility.prepareTenant;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.joda.time.Seconds.seconds;

import io.vertx.core.json.JsonObject;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.builders.ItemRequestBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class UpdateItemStatusDateFunctionMigrationTest extends MigrationTestBase {
  private static final String OLD_DATETIME = "0001-01-01T01:01:01.01Z";

  @Parameters({
    // Here only the "from" version is in play, since 18.2.3 < 19.2.0 (fromModuleVersion of the script)
    // then the upgrade will be executed and new version of the function will be deployed.
    "18.2.3, 19.1.1",
    "18.2.3, 19.2.0",
    "19.1.1, 19.2.0",
    "19.1.1, 20.0.0",
  })
  @Test
  public void canMigrateStatusUpdateDateFunctionBetweenReleases(
    String fromVersion, String toVersion) throws Exception {

    executeMultipleSqlStatements(getPreviousFunctionSql());

    final UUID firstItem = createAndChangeItemStatus();
    // The 'old' function should set '0001-01-01T01:01:01.01Z' datetime
    assertThat(executeSelect(
      "select jsonb->'status'->>'date' from %s.item where id = '%s'",
      getSchemaName(), firstItem).iterator().next().getString(0), is(OLD_DATETIME));

    upgradeTenant(fromVersion, toVersion);

    final UUID secondItem = createAndChangeItemStatus();
    final JsonObject updatedStatus = itemsClient.getById(secondItem).getJson()
      .getJsonObject("status");

    // The new function will set current date time
    assertThat(updatedStatus.getString("name"), is("Checked out"));
    assertThat(updatedStatus.getString("date"), not(is(OLD_DATETIME)));
    assertThat(updatedStatus.getString("date"), withinSecondsBeforeNowAsString(seconds(1)));
  }

  private void upgradeTenant(String fromVersion, String toVersion) throws InterruptedException,
    ExecutionException, TimeoutException {

    prepareTenant(TENANT_ID, "mod-inventory-storage-" + fromVersion,
      "mod-inventory-storage-" + toVersion, false);
  }

  private static String getPreviousFunctionSql() {
    String sql = "CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.update_item_status_date() RETURNS TRIGGER" +
      " AS $$" +
      "  DECLARE" +
      "  newStatus text;" +
      "  BEGIN" +
      "  newStatus = NEW.jsonb->'status'->>'name';" +
      "  IF (newStatus IS DISTINCT FROM OLD.jsonb->'status'->>'name') THEN" +
      "      NEW.jsonb = jsonb_set(NEW.jsonb, '{status,date}'," +
      "       to_jsonb(to_char(CURRENT_TIMESTAMP(3) AT TIME ZONE 'UTC', '" + OLD_DATETIME + "')), true);" +
      "  END IF;" +
      "  RETURN NEW;" +
      "  END;" +
      "  $$ LANGUAGE 'plpgsql';";

    return replaceSchema(sql);
  }

  private UUID createAndChangeItemStatus() {
    final UUID holdingsId = createInstanceAndHolding(mainLibraryLocationId);
    final IndividualResource item = itemsClient.create(new ItemRequestBuilder()
      .available()
      .withMaterialType(bookMaterialTypeId)
      .withPermanentLoanType(canCirculateLoanTypeId)
      .forHolding(holdingsId));

    itemsClient.replace(item.getId(), item.copyJson()
      .put("status", new JsonObject().put("name", "Checked out")));

    return item.getId();
  }
}
