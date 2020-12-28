package org.folio.services.holding;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static io.vertx.core.Promise.promise;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.logging.log4j.LogManager.getLogger;
import static org.folio.rest.persist.PgUtil.postgresClient;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.logging.log4j.Logger;
import org.folio.persist.HoldingsRepository;
import org.folio.rest.exceptions.BadRequestException;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.SQLConnection;
import org.folio.rest.support.HridManager;
import org.folio.services.domainevent.ItemDomainEventService;
import org.folio.services.item.ItemService;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;

public class HoldingsService {
  private static final Logger log = getLogger(HoldingsService.class);

  private final PostgresClient postgresClient;
  private final HridManager hridManager;
  private final ItemService itemService;
  private final HoldingsRepository holdingsRepository;
  private final ItemDomainEventService itemEventService;

  public HoldingsService(Context context, Map<String, String> okapiHeaders) {
    itemService = new ItemService(context, okapiHeaders);
    postgresClient = postgresClient(context, okapiHeaders);
    hridManager = new HridManager(context, postgresClient);
    holdingsRepository = new HoldingsRepository(context, okapiHeaders);
    itemEventService = new ItemDomainEventService(context, okapiHeaders);
  }

  public Future<Void> updateHoldingRecord(String holdingId, HoldingsRecord holdingsRecord) {
    return holdingsRepository.getById(holdingId)
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
      .compose(hr -> holdingsRepository.save(hr.getId(), hr))
      .map(notUsed -> null);
  }

  private Future<Void> updateHolding(HoldingsRecord oldHoldings, HoldingsRecord newHoldings) {
    return refuseIfHridChanged(oldHoldings, newHoldings)
      .compose(notUsed -> {
        final Promise<List<Item>> overallResult = promise();

        postgresClient.startTx(
          connection -> holdingsRepository.update(connection, oldHoldings.getId(), newHoldings)
            .compose(updateRes -> itemService.updateItemsOnHoldingChanged(connection, newHoldings))
            .onComplete(handleTransaction(connection, overallResult)));

        return overallResult.future()
          .compose(itemsBeforeUpdate -> itemEventService.itemsUpdated(newHoldings, itemsBeforeUpdate));
      });
  }

  private <T> Handler<AsyncResult<T>> handleTransaction(
    AsyncResult<SQLConnection> connection, Promise<T> overallResult) {

    return transactionResult -> {
      if (transactionResult.succeeded()) {
        postgresClient.endTx(connection, commitResult -> {
          if (commitResult.succeeded()) {
            overallResult.complete(transactionResult.result());
          } else {
            log.error("Unable to commit transaction", commitResult.cause());
            overallResult.fail(commitResult.cause());
          }
        });
      } else {
        log.error("Reverting transaction");
        postgresClient.rollbackTx(connection, revertResult -> {
          if (revertResult.failed()) {
            log.error("Unable to revert transaction", revertResult.cause());
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
