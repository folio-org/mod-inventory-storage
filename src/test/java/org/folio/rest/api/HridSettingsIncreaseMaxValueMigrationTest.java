package org.folio.rest.api;

import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.folio.rest.api.StorageTestSuite.getVertx;

import java.util.List;

import org.folio.rest.persist.PostgresClient;
import org.folio.util.ResourceUtil;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class HridSettingsIncreaseMaxValueMigrationTest extends TestBase {
  private static final String CREATE_SEQUENCE_SQL = loadCreateSequenceSqlFile();
  private static final String ALTER_SEQUENCE_SQL = loadAlterSequenceSqlFile();
  private static final String INSTANCES_SEQ = "hrid_instances_seq";
  private static final String HOLDINGS_SEQ = "hrid_holdings_seq";
  private static final String ITEMS_SEQ = "hrid_items_seq";

  @Test
  public void canSaveCurrentValueOfSequences(TestContext testContext) {
    reCreateSequences()
      // Increment sequences and and set a new value
      .compose(notUsed -> incrementSequence(INSTANCES_SEQ, 10))
      .map(hrid -> testContext.assertEquals(10L, hrid))
      .compose(notUsed -> incrementSequence(HOLDINGS_SEQ, 7))
      .map(hrid -> testContext.assertEquals(7L, hrid))
      .compose(notUsed -> setValue(ITEMS_SEQ, 999_999_99L))
      .compose(notUsed -> incrementSequence(ITEMS_SEQ))
      .map(hrid -> testContext.assertEquals(999_999_99L, hrid))
      // Alter sequences
      .compose(notUsed -> alterSequences())
      // Assert that current value has not been changed
      .compose(notUsed -> incrementSequence(INSTANCES_SEQ))
      .map(hrid -> testContext.assertEquals(11L, hrid))
      .compose(notUsed -> incrementSequence(HOLDINGS_SEQ))
      .map(hrid -> testContext.assertEquals(8L, hrid))
      .compose(notUsed -> incrementSequence(ITEMS_SEQ))
      .map(hrid -> testContext.assertEquals(1_000_000_00L, hrid))
      // Handle test result
      .setHandler(testContext.asyncAssertSuccess());
  }

  @Test
  public void canSetBigIntValue(TestContext testContext) {
    reCreateSequences()
      .compose(notUsed -> alterSequences())
      // Set large values
      .compose(notUsed -> setValue(INSTANCES_SEQ, 99_999_999_990L))
      .compose(notUsed -> setValue(HOLDINGS_SEQ, 99_999_999_991L))
      .compose(notUsed -> setValue(ITEMS_SEQ, 99_999_999_992L))
      // Assert that the large values are set
      .compose(notUsed -> incrementSequence(INSTANCES_SEQ))
      .map(hrid -> testContext.assertEquals(99_999_999_990L, hrid))
      .compose(notUsed -> incrementSequence(HOLDINGS_SEQ))
      .map(hrid -> testContext.assertEquals(99_999_999_991L, hrid))
      .compose(notUsed -> incrementSequence(ITEMS_SEQ))
      .map(hrid -> testContext.assertEquals(99_999_999_992L, hrid))
      // Handle test result
      .setHandler(testContext.asyncAssertSuccess());
  }

  private Future<Void> reCreateSequences() {
    return CompositeFuture.all(
      executeSql(dropSequenceSql(INSTANCES_SEQ)),
      executeSql(dropSequenceSql(HOLDINGS_SEQ)),
      executeSql(dropSequenceSql(ITEMS_SEQ))
    ).compose(notUsed -> executeSqlFile(CREATE_SEQUENCE_SQL));
  }

  private Future<Void> alterSequences() {
    return executeSqlFile(ALTER_SEQUENCE_SQL);
  }

  private Future<Long> incrementSequence(String sequenceName) {
    final Promise<Long> nextValue = Promise.promise();
    final String sql = String.format(
      "SELECT nextVal('%s.%s')", getSchemaName(), sequenceName
    );

    PostgresClient.getInstance(getVertx()).select(sql, result -> {
      if (result.failed()) {
        nextValue.fail(result.cause());
      } else {
        List<JsonArray> results = result.result().getResults();
        if (results.isEmpty()) {
          nextValue.fail("Empty result set");
        } else {
          nextValue.complete(results.get(0).getLong(0));
        }
      }
    });

    return nextValue.future();
  }

  private Future<Long> incrementSequence(String sequenceName, int times) {
    Future<Long> last = Future.succeededFuture();

    for (int i = 0; i < times; i++) {
      last = last.compose(prev -> incrementSequence(sequenceName));
    }

    return last;
  }

  private Future<Void> setValue(String sequenceName, Long value) {
    return executeSql(String.format(
      "SELECT setVal('%s.%s', %d, FALSE)", getSchemaName(), sequenceName, value
    ));
  }

  private String dropSequenceSql(String sequenceName) {
    return String.format("DROP SEQUENCE %s.%s", getSchemaName(), sequenceName);
  }

  private static String loadCreateSequenceSqlFile() {
    return ResourceUtil.asString("/templates/db_scripts/hridSettings.sql")
      .replace("${myuniversity}_${mymodule}", getSchemaName())
      .replace("${table.tableName}", "hrid_settings");
  }

  private static String loadAlterSequenceSqlFile() {
    return ResourceUtil.asString("/templates/db_scripts/alterHridSequences.sql")
      .replace("${myuniversity}_${mymodule}", getSchemaName());
  }

  private static String getSchemaName() {
    return String.format("%s_mod_inventory_storage", TENANT_ID);
  }
}
