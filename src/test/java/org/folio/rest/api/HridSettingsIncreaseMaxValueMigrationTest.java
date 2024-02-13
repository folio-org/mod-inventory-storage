package org.folio.rest.api;

import static org.folio.utility.ModuleUtility.getVertx;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.SneakyThrows;
import org.folio.rest.persist.PostgresClient;
import org.junit.Before;
import org.junit.Test;

public class HridSettingsIncreaseMaxValueMigrationTest extends MigrationTestBase {
  private static final String CREATE_SEQUENCE_SQL = loadCreateSequenceSqlFile();
  private static final String ALTER_SEQUENCE_SQL = loadScript("alterHridSequences.sql");
  private static final String INSTANCES_SEQ = "hrid_instances_seq";
  private static final String HOLDINGS_SEQ = "hrid_holdings_seq";
  private static final String ITEMS_SEQ = "hrid_items_seq";

  private static String loadCreateSequenceSqlFile() {
    return "SET ROLE " + getSchemaName() + ";"
        + loadScript("hridSettings.sql",
            MigrationTestBase::replaceSchema,
            resource -> resource.replace("${table.tableName}", "hrid_settings"))
        + loadScript("hridSettingsView.sql");
  }

  @SneakyThrows
  @Before
  public void beforeEach() {
    clearData();
    setupMaterialTypes();
    setupLoanTypes();
    setupLocations();
    removeAllEvents();
  }

  @Test
  public void retainsCurrentValueOfSequences() throws Exception {
    reCreateSequences();

    assertThat(incrementSequence(INSTANCES_SEQ, 10), is(10L));
    assertThat(incrementSequence(HOLDINGS_SEQ, 7), is(7L));

    setValue(ITEMS_SEQ, 999_999_99L);
    assertThat(incrementSequence(ITEMS_SEQ), is(999_999_99L));

    alterSequences();

    assertThat(incrementSequence(INSTANCES_SEQ), is(11L));
    assertThat(incrementSequence(HOLDINGS_SEQ), is(8L));
    assertThat(incrementSequence(ITEMS_SEQ), is(1_000_000_00L));
  }

  private void reCreateSequences() throws Exception {
    executeSql(String.format("DROP VIEW %s.hrid_settings_view", getSchemaName()));
    executeSql(String.format("DROP SEQUENCE %s.%s, %s.%s, %s.%s",
        getSchemaName(), INSTANCES_SEQ,
        getSchemaName(), HOLDINGS_SEQ,
        getSchemaName(), ITEMS_SEQ));

    executeMultipleSqlStatements(CREATE_SEQUENCE_SQL);
  }

  private void alterSequences() throws InterruptedException, ExecutionException,
    TimeoutException {

    executeMultipleSqlStatements(ALTER_SEQUENCE_SQL);
  }

  private Long incrementSequence(String sequenceName)
    throws InterruptedException, ExecutionException, TimeoutException {

    final CompletableFuture<Long> nextValue = new CompletableFuture<>();
    final String sql = String.format(
      "SELECT nextVal('%s.%s')", getSchemaName(), sequenceName
    );

    PostgresClient.getInstance(getVertx()).select(sql, result -> {
      if (result.failed()) {
        nextValue.completeExceptionally(result.cause());
      } else {
        if (result.result().size() == 0) {
          nextValue.completeExceptionally(new RuntimeException("Empty result set"));
        } else {
          nextValue.complete(result.result().iterator().next().getLong(0));
        }
      }
    });

    return nextValue.get(TIMEOUT, TimeUnit.SECONDS);
  }

  private Long incrementSequence(String sequenceName, int times)
    throws InterruptedException, ExecutionException, TimeoutException {

    Long last = null;

    for (int i = 0; i < times; i++) {
      last = incrementSequence(sequenceName);
    }

    return last;
  }

  private void setValue(String sequenceName, Long value)
    throws InterruptedException, ExecutionException, TimeoutException {

    executeSql(String.format(
      "SELECT setVal('%s.%s', %d, FALSE)", getSchemaName(), sequenceName, value
    ));
  }
}
