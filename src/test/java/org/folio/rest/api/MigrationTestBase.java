package org.folio.rest.api;


import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.rest.api.StorageTestSuite.TENANT_ID;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.UnaryOperator;

import org.folio.rest.persist.PostgresClient;
import org.folio.util.ResourceUtil;

import io.vertx.core.Vertx;
import io.vertx.ext.sql.UpdateResult;

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
   * @throws InterruptedException
   * @throws ExecutionException
   * @throws TimeoutException
   */
  void executeMultipleSqlStatements(String allStatements)
    throws InterruptedException, ExecutionException, TimeoutException {

    final CompletableFuture<Void> result = new CompletableFuture<>();
    final Vertx vertx = StorageTestSuite.getVertx();

    PostgresClient.getInstance(vertx).runSQLFile(allStatements, true, handler -> {
      if (handler.failed()) {
        result.completeExceptionally(handler.cause());
      } else if (!handler.result().isEmpty()) {
        result.completeExceptionally(new RuntimeException("Failing SQL: " + handler.result().toString()));
      } else {
        result.complete(null);
      }
    });

    result.get(5, SECONDS);
  }

  UpdateResult executeSql(String sql)
    throws InterruptedException, ExecutionException, TimeoutException {

    final CompletableFuture<UpdateResult> result = new CompletableFuture<>();
    final Vertx vertx = StorageTestSuite.getVertx();

    PostgresClient.getInstance(vertx).execute(sql, updateResult -> {
      if (updateResult.failed()) {
        result.completeExceptionally(updateResult.cause());
      } else {
        result.complete(updateResult.result());
      }
    });

    return result.get(5, SECONDS);
  }

  void updateJsonbProperty(
    String tableName, UUID id, String postgresPropertyPath, String postgresPropertyValue)
    throws InterruptedException, ExecutionException, TimeoutException {

    executeSql(String.format(
      "UPDATE %s.%s as tbl SET jsonb = jsonb_set(tbl.jsonb, '{%s}', to_jsonb(%s)) WHERE id::text = '%s'",
      getSchemaName(),
      tableName,
      postgresPropertyPath,
      postgresPropertyValue,
      id.toString()
    ));
  }

  UpdateResult unsetJsonbProperty(String tableName, UUID id, String propertyName)
    throws InterruptedException, ExecutionException, TimeoutException {

    return executeSql(String.format(
      "UPDATE %s.%s as tbl SET jsonb = tbl.jsonb - '%s' WHERE id::text = '%s'",
      getSchemaName(), tableName, propertyName, id.toString()));
  }
}
