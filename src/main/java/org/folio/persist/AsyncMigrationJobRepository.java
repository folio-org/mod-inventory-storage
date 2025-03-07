package org.folio.persist;

import static java.lang.String.format;
import static org.folio.rest.persist.PgUtil.postgresClient;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.folio.rest.jaxrs.model.AsyncMigrationJob;
import org.folio.rest.persist.SQLConnection;

public class AsyncMigrationJobRepository extends AbstractRepository<AsyncMigrationJob> {
  public static final String TABLE_NAME = "async_migration_job";

  public AsyncMigrationJobRepository(Context context, Map<String, String> okapiHeaders) {
    super(postgresClient(context, okapiHeaders), TABLE_NAME, AsyncMigrationJob.class);
  }

  public Future<AsyncMigrationJob> fetchAndUpdate(String id, UnaryOperator<AsyncMigrationJob> builder) {

    Promise<AsyncMigrationJob> result = Promise.promise();
    String selectForUpdate = format("SELECT jsonb FROM %s WHERE id = $1 LIMIT 1 FOR UPDATE",
      postgresClientFuturized.getFullTableName(TABLE_NAME));
    Promise<SQLConnection> txPromise = Promise.promise();

    Future.succeededFuture()
      .compose(notUsed -> {
        postgresClient.startTx(txPromise);
        return txPromise.future();
      }).compose(notUsed -> {
        Promise<Row> selectResult = Promise.promise();
        postgresClient.selectSingle(txPromise.future(), selectForUpdate, Tuple.of(id), selectResult);
        return selectResult.future().map(row -> row.getJsonObject("jsonb")
          .mapTo(AsyncMigrationJob.class));
      }).map(builder)
      .compose(response -> update(txPromise.future(), id, response)
        .map(response))
      .compose(asyncMigrationJob -> {
        Promise<Void> endTxFuture = Promise.promise();
        postgresClient.endTx(txPromise.future(), endTxFuture);
        return endTxFuture.future().map(asyncMigrationJob);
      })
      .onSuccess(result::complete)
      .onFailure(throwable ->
        postgresClient.rollbackTx(txPromise.future(), rollback -> result.fail(throwable)));
    return result.future();
  }
}
