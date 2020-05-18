package org.folio.rest.api;

import static io.vertx.core.json.JsonObject.mapFrom;
import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.folio.rest.support.matchers.DateTimeMatchers.withinSecondsBeforeNowAsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.joda.time.Seconds.seconds;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.builders.ItemRequestBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.json.JsonObject;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class UpdateItemStatusDateFunctionMigrationTest extends MigrationTestBase {
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
    // The 'old' function should add 'OLD ' prefix for datetime, verify it
    assertThat(executeSelect(
      "select jsonb->'status'->>'date' from %s.item where id = '%s'",
      getSchemaName(), firstItem).iterator().next().getString(0), startsWith("OLD "));

    upgradeTenant(fromVersion, toVersion);

    final UUID secondItem = createAndChangeItemStatus();
    final JsonObject updatedStatus = itemsClient.getById(secondItem).getJson()
      .getJsonObject("status");

    // The new function won't add the 'OLD ' prefix, and will work as expected.
    assertThat(updatedStatus.getString("name"), is("Checked out"));
    assertThat(updatedStatus.getString("date"), not(startsWith("OLD")));
    assertThat(updatedStatus.getString("date"), withinSecondsBeforeNowAsString(seconds(1)));
  }

  private void upgradeTenant(String fromVersion, String toVersion) throws InterruptedException,
    ExecutionException, TimeoutException {

    final TenantAttributes tenantAttributes = new TenantAttributes()
      .withModuleFrom("mod-inventory-storage-" + fromVersion)
      .withModuleTo("mod-inventory-storage-" + toVersion);

    client.post(StorageTestSuite.storageUrl("/_/tenant"), mapFrom(tenantAttributes), TENANT_ID)
      .get(5, TimeUnit.SECONDS);
  }


  /**
   * The "old" version appends 'OLD' prefix to the datetime.
   */
  private static String getPreviousFunctionSql() {
    String sql = "CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.update_item_status_date() RETURNS TRIGGER" +
      " AS $$" +
      "  DECLARE" +
      "  newStatus text;" +
      "  BEGIN" +
      "  newStatus = NEW.jsonb->'status'->>'name';" +
      "  IF (newStatus IS DISTINCT FROM OLD.jsonb->'status'->>'name') THEN" +
      "      NEW.jsonb = jsonb_set(NEW.jsonb, '{status,date}'," +
      "       to_jsonb(to_char(CURRENT_TIMESTAMP(3) AT TIME ZONE 'UTC', '\"OLD \"YYYY-MM-DD\"T\"HH24:MI:SS.ms\"Z\"')), true);" +
      "  END IF;" +
      "  RETURN NEW;" +
      "  END;" +
      "  $$ LANGUAGE 'plpgsql';";

    return replaceSchema(sql);
  }

  private UUID createAndChangeItemStatus() throws InterruptedException,
    ExecutionException, MalformedURLException, TimeoutException {

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
