package org.folio.services.holding;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static io.vertx.core.Promise.promise;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.logging.log4j.LogManager.getLogger;
import static org.folio.rest.impl.HoldingsStorageAPI.HOLDINGS_RECORD_TABLE;
import static org.folio.rest.persist.PgUtil.postgresClient;

import java.util.Map;
import java.util.Objects;

import org.apache.logging.log4j.Logger;
import org.folio.rest.exceptions.BadRequestException;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.SQLConnection;
import org.folio.rest.support.HridManager;
import org.folio.services.item.ItemService;
import org.folio.services.persist.PostgresClientFuturized;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

public class HoldingsService {
  private static final String WHERE_CLAUSE = "WHERE id = '%s'";
  private static final Logger log = getLogger(HoldingsService.class);

  private final PostgresClient postgresClient;
  private final HridManager hridManager;
  private final ItemService itemService;
  private final PostgresClientFuturized postgresClientFuturized;

  public HoldingsService(Context context, Map<String, String> okapiHeaders) {
    itemService = new ItemService(context, okapiHeaders);
    postgresClient = postgresClient(context, okapiHeaders);
    hridManager = new HridManager(context, postgresClient);
    postgresClientFuturized = new PostgresClientFuturized(postgresClient);
  }

  public Future<Void> updateHoldingRecord(String holdingId, HoldingsRecord holdingsRecord) {
    return postgresClientFuturized.getById(HOLDINGS_RECORD_TABLE, holdingId, HoldingsRecord.class)
      .compose(existingHoldingsRecord -> {
        if (holdingsRecordFound(existingHoldingsRecord)) {
          return updateHolding(existingHoldingsRecord, holdingsRecord);
        } else {
          return saveHolding(holdingsRecord);
        }
      });
  }

  private Future<Void> saveHolding(HoldingsRecord entity) {
    final Future<String> hridFuture = isBlank(entity.getHrid())
      ? hridManager.getNextHoldingsHrid() : succeededFuture(entity.getHrid());

    return hridFuture
      .map(entity::withHrid)
      .compose(holdingsRecord -> postgresClientFuturized
        .save(HOLDINGS_RECORD_TABLE, entity.getId(), entity))
      .map(notUsed -> null);
  }

  private Future<Void> updateHolding(HoldingsRecord oldHoldings, HoldingsRecord newHoldings) {
    return refuseIfHridChanged(oldHoldings, newHoldings)
      .compose(notUsed -> {
        final Promise<Void> overallResult = promise();

        postgresClient.startTx(
          connection -> updateHoldingInTransaction(connection, newHoldings)
            .compose(updateRes -> itemService.updateItemsOnHoldingChanged(connection, newHoldings))
            .onComplete(handleTransaction(connection, overallResult)));

        return overallResult.future();
      });
  }

  private Future<Void> updateHoldingInTransaction(
    AsyncResult<SQLConnection> connection, HoldingsRecord entity) {

    final Promise<RowSet<Row>> holdingsUpdateResult = promise();

    postgresClient.update(connection, HOLDINGS_RECORD_TABLE, entity, "jsonb",
      format(WHERE_CLAUSE, entity.getId()), false, holdingsUpdateResult);

    return holdingsUpdateResult.future().map(notUsed -> null);
  }

  private Handler<AsyncResult<Void>> handleTransaction(
    AsyncResult<SQLConnection> connection, Promise<Void> overallResult) {

    return transactionResult -> {
      if (transactionResult.succeeded()) {
        postgresClient.endTx(connection, overallResult);
      } else {
        log.warn("Reverting transaction");
        postgresClient.rollbackTx(connection, revertResult -> {
          if (revertResult.failed()) {
            log.warn("Unable to revert transaction", revertResult.cause());
          }
          overallResult.fail(transactionResult.cause());
        });
      }
    };
  }

  private Future<HoldingsRecord> refuseIfHridChanged(
    HoldingsRecord oldHolding, HoldingsRecord newHolding) {

    if (Objects.equals(oldHolding.getHrid(), newHolding.getHrid())) {
      return succeededFuture(oldHolding);
    } else {
      return failedFuture(new BadRequestException(format(
        "The hrid field cannot be changed: new=%s, old=%s", newHolding.getHrid(),
        oldHolding.getHrid())));
    }
  }

  private boolean holdingsRecordFound(HoldingsRecord holdingsRecord) {
    return holdingsRecord != null;
  }
}
