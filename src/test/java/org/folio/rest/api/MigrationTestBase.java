package org.folio.rest.api;

import static org.folio.utility.ModuleUtility.getVertx;
import static org.folio.utility.RestUtility.TENANT_ID;

import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.UnaryOperator;
import org.folio.rest.persist.PostgresClient;
import org.folio.util.ResourceUtil;

abstract class MigrationTestBase extends TestBaseWithInventoryUtil {
  static String loadScript(String scriptName) {
    return loadScript(scriptName, MigrationTestBase::replaceSchema);
  }

  @SafeVarargs
  static String loadScript(String scriptName, UnaryOperator<String>... replacementFunctions) {
    String resource = ResourceUtil.asString("/templates/db_scripts/" + scriptName);

    for (UnaryOperator<String> replacementFunction : replacementFunctions) {
      resource = replacementFunction.apply(resource);
    }

    return resource;
  }

  static String getSchemaName() {
    return String.format("%s_mod_inventory_storage", TENANT_ID);
  }

  static String replaceSchema(String resource) {
    return resource.replace("${myuniversity}_${mymodule}", getSchemaName());
  }

  /**
   * Executes multiply SQL statements separated by a line separator (either CRLF|CR|LF).
   *
   * @param allStatements - Statements to execute (separated by a line separator).
   */
  void executeMultipleSqlStatements(String allStatements)
    throws InterruptedException, ExecutionException, TimeoutException {

    final CompletableFuture<Void> result = new CompletableFuture<>();

    getPostgresClient().runSQLFile(allStatements, true, handler -> {
      if (handler.failed()) {
        result.completeExceptionally(handler.cause());
      } else if (!handler.result().isEmpty()) {
        result.completeExceptionally(new RuntimeException("Failing SQL: " + handler.result().toString()));
      } else {
        result.complete(null);
      }
    });

    get(result);
  }

  RowSet<Row> executeSql(String sql) {

    final CompletableFuture<RowSet<Row>> result = new CompletableFuture<>();

    getPostgresClient().execute(sql, updateResult -> {
      if (updateResult.failed()) {
        result.completeExceptionally(updateResult.cause());
      } else {
        result.complete(updateResult.result());
      }
    });

    return get(result);
  }

  void updateJsonbProperty(String tableName, UUID id, String postgresPropertyPath, String postgresPropertyValue) {

    executeSql(String.format(
      "UPDATE %s.%s as tbl SET jsonb = jsonb_set(tbl.jsonb, '{%s}', to_jsonb(%s)) WHERE id::text = '%s'",
      getSchemaName(),
      tableName,
      postgresPropertyPath,
      postgresPropertyValue,
      id.toString()
    ));
  }

  RowSet<Row> unsetJsonbProperty(String tableName, UUID id, String propertyName)
    throws InterruptedException, ExecutionException, TimeoutException {

    return executeSql(String.format(
      "UPDATE %s.%s as tbl SET jsonb = tbl.jsonb - '%s' WHERE id::text = '%s'",
      getSchemaName(), tableName, propertyName, id.toString()));
  }

  RowSet<Row> executeSelect(String selectQuery, Object... args)
    throws InterruptedException, ExecutionException, TimeoutException {

    final CompletableFuture<RowSet<Row>> result = new CompletableFuture<>();
    getPostgresClient().select(String.format(replaceSchema(selectQuery), args),
      resultSet -> {
        if (resultSet.failed()) {
          result.completeExceptionally(resultSet.cause());
          return;
        }
        result.complete(resultSet.result());
      });

    return get(result);
  }

  private PostgresClient getPostgresClient() {
    return PostgresClient.getInstance(getVertx());
  }
}
