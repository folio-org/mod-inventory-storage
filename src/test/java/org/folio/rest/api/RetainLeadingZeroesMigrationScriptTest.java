package org.folio.rest.api;

import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import java.util.UUID;
import lombok.SneakyThrows;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class RetainLeadingZeroesMigrationScriptTest extends MigrationTestBase {
  private static final String MIGRATION_SCRIPT = loadScript("populateRetainLeadingZeroesSetting.sql");
  private static final String SQL_GET_HRID_SETTINGS = "SELECT jsonb FROM %s.hrid_settings WHERE id = 'a501f2a8-5b31-48b2-874d-2191e48db8cd'";
  private static final String LEADING_ZEROES_PROPERTY = "commonRetainLeadingZeroes";

  @SneakyThrows
  @Before
  public void beforeEach() {
    clearData();
    setupMaterialTypes();
    setupLoanTypes();
    setupLocations();

    unsetJsonbProperty("hrid_settings", UUID.fromString("a501f2a8-5b31-48b2-874d-2191e48db8cd"), LEADING_ZEROES_PROPERTY);
    removeAllEvents();
  }

  @Test
  public void populateCommonRetainLeadingZeroes() throws Exception {
    RowSet<Row> result = executeSql(String.format(SQL_GET_HRID_SETTINGS, getSchemaName()));
    Assert.assertNotNull(result);
    JsonObject withoutLeadingZeroes = (JsonObject) result.iterator().next().getJson(0);
    Assert.assertFalse(withoutLeadingZeroes.containsKey(LEADING_ZEROES_PROPERTY));

    executeMultipleSqlStatements(MIGRATION_SCRIPT);
    executeMultipleSqlStatements(MIGRATION_SCRIPT); //check 2nd run MODINVSTOR-675

    result = executeSql(String.format(SQL_GET_HRID_SETTINGS, getSchemaName()));
    Assert.assertNotNull(result);
    JsonObject withLeadingZeroes = (JsonObject) result.iterator().next().getJson(0);
    Assert.assertTrue(withLeadingZeroes.containsKey(LEADING_ZEROES_PROPERTY));
  }

}
