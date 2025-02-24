package org.folio.rest.api;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import lombok.SneakyThrows;
import org.junit.Test;

public class LegacyItemEffectiveLocationMigrationScriptTest extends MigrationTestBase {

  private static final String CREATE_FUNCTIONS_SCRIPT = loadScript("effectiveLocationFunctionCreator.sql");
  private static final String DROP_EFFECTIVE_LOCATION_FUNCTION_SCRIPT =
    loadScript("dropLegacyItemEffectiveLocationFunctions.sql");

  @Test
  public void canDropLegacyEffectiveItemLocationFunctions() {
    executeMultipleSqlStatements(CREATE_FUNCTIONS_SCRIPT);

    assertThat(doesFunctionExist("update_effective_location_on_item_update"), is(true));
    assertThat(doesFunctionExist("update_effective_location_on_holding_update"), is(true));

    executeMultipleSqlStatements(DROP_EFFECTIVE_LOCATION_FUNCTION_SCRIPT);

    assertThat(doesFunctionExist("update_effective_location_on_item_update"), is(false));
    assertThat(doesFunctionExist("update_effective_location_on_holding_update"), is(false));

  }

  @SneakyThrows
  private Boolean doesFunctionExist(String functionName) {

    String query = "SELECT * FROM pg_proc WHERE proname LIKE '" + functionName + "';";
    RowSet<Row> results = executeSelect(query);
    return results.size() != 0;
  }
}
