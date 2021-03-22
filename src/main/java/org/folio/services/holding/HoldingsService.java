package org.folio.services.holding;

import static io.vertx.core.CompositeFuture.all;
import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static io.vertx.core.Promise.promise;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.logging.log4j.LogManager.getLogger;
import static org.folio.rest.impl.HoldingsStorageAPI.HOLDINGS_RECORD_TABLE;
import static org.folio.rest.impl.StorageHelper.MAX_ENTITIES;
import static org.folio.rest.jaxrs.resource.HoldingsStorage.DeleteHoldingsStorageHoldingsByHoldingsRecordIdResponse;
import static org.folio.rest.jaxrs.resource.HoldingsStorage.PostHoldingsStorageHoldingsResponse;
import static org.folio.rest.jaxrs.resource.HoldingsStorage.PutHoldingsStorageHoldingsByHoldingsRecordIdResponse;
import static org.folio.rest.jaxrs.resource.HoldingsStorageBatchSynchronous.PostHoldingsStorageBatchSynchronousResponse;
import static org.folio.rest.persist.PgUtil.deleteById;
import static org.folio.rest.persist.PgUtil.post;
import static org.folio.rest.persist.PgUtil.postSync;
import static org.folio.rest.persist.PgUtil.postgresClient;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.Logger;
import org.folio.persist.HoldingsRepository;
import org.folio.rest.exceptions.BadRequestException;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.SQLConnection;
import org.folio.rest.support.HridManager;
import org.folio.services.batch.BatchOperationContext;
import org.folio.services.domainevent.HoldingDomainEventPublisher;
import org.folio.services.domainevent.ItemDomainEventPublisher;
import org.folio.services.item.ItemService;
import org.folio.validator.CommonValidators;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;

public class HoldingsService {
  private static final Logger log = getLogger(HoldingsService.class);

  private final Context vertxContext;
  private final Map<String, String> okapiHeaders;
  private final PostgresClient postgresClient;
  private final HridManager hridManager;
  private final ItemService itemService;
  private final HoldingsRepository holdingsRepository;
  private final ItemDomainEventPublisher itemEventService;
  private final HoldingDomainEventPublisher domainEventPublisher;

  public HoldingsService(Context context, Map<String, String> okapiHeaders) {
    this.vertxContext = context;
    this.okapiHeaders = okapiHeaders;

    itemService = new ItemService(context, okapiHeaders);
    postgresClient = postgresClient(context, okapiHeaders);
    hridManager = new HridManager(context, postgresClient);
    holdingsRepository = new HoldingsRepository(context, okapiHeaders);
    itemEventService = new ItemDomainEventPublisher(context, okapiHeaders);
    domainEventPublisher = new HoldingDomainEventPublisher(context, okapiHeaders);
  }

  public Future<Void> deleteAllHoldings() {
    return holdingsRepository.deleteAll()
      .compose(notUsed -> domainEventPublisher.publishAllRemoved());
  }

  public Future<Response> updateHoldingRecord(String holdingId, HoldingsRecord holdingsRecord) {
    return holdingsRepository.getById(holdingId)
      .compose(existingHoldingsRecord -> {
        if (holdingsRecordFound(existingHoldingsRecord)) {
          return updateHolding(existingHoldingsRecord, holdingsRecord);
        } else {
          return createHolding(holdingsRecord);
        }
      });
  }

  public Future<Response> createHolding(HoldingsRecord entity) {
    //entity.setEffectiveLocationId(calculateEffectiveLocation(entity));
    return setHrid(entity)
      .compose(hr -> {
        final Promise<Response> postResponse = promise();

        post(HOLDINGS_RECORD_TABLE, hr, okapiHeaders, vertxContext,
          PostHoldingsStorageHoldingsResponse.class, postResponse);

        return postResponse.future()
          .compose(domainEventPublisher.publishCreated());
      });
  }

  private Future<Response> updateHolding(HoldingsRecord oldHoldings, HoldingsRecord newHoldings) {
    //newHoldings.setEffectiveLocationId(calculateEffectiveLocation(newHoldings));
    return refuseIfHridChanged(oldHoldings, newHoldings)
      .compose(notUsed -> {
        final Promise<List<Item>> overallResult = promise();

        postgresClient.startTx(
          connection -> holdingsRepository.update(connection, oldHoldings.getId(), newHoldings)
            .compose(updateRes -> itemService.updateItemsOnHoldingChanged(connection, newHoldings))
            .onComplete(handleTransaction(connection, overallResult)));

        return overallResult.future()
          .compose(itemsBeforeUpdate -> itemEventService.publishUpdated(newHoldings, itemsBeforeUpdate))
          .<Response>map(res -> PutHoldingsStorageHoldingsByHoldingsRecordIdResponse.respond204())
          .compose(domainEventPublisher.publishUpdated(oldHoldings));
      });
  }

  public Future<Response> deleteHolding(String hrId) {
    return holdingsRepository.getById(hrId)
      .compose(CommonValidators::refuseIfNotFound)
      .compose(hr -> {
        final Promise<Response> deleteResult = promise();

        deleteById(HOLDINGS_RECORD_TABLE, hrId, okapiHeaders, vertxContext,
          DeleteHoldingsStorageHoldingsByHoldingsRecordIdResponse.class, deleteResult);

        return deleteResult.future()
          .compose(domainEventPublisher.publishRemoved(hr));
      });
  }

  public Future<Response> createHoldings(List<HoldingsRecord> holdings, boolean upsert) {
    
    //for (HoldingsRecord record : holdings) {
    //  record.setEffectiveLocationId(calculateEffectiveLocation(record));
    //}

    @SuppressWarnings("all")
    final List<Future> setHridFutures = holdings.stream()
      .map(this::setHrid)
      .collect(toList());

    return all(setHridFutures)
      .compose(result -> buildBatchOperationContext(upsert, holdings))
      .compose(batchOperation -> {
        final Promise<Response> postSyncResult = promise();

        postSync(HOLDINGS_RECORD_TABLE, holdings, MAX_ENTITIES, upsert,
          okapiHeaders, vertxContext, PostHoldingsStorageBatchSynchronousResponse.class,
          postSyncResult);

        return postSyncResult.future()
          .compose(domainEventPublisher.publishCreatedOrUpdated(batchOperation));
      });
  }

  private String calculateEffectiveLocation(HoldingsRecord record) {
    String permanentLocationId = record.getPermanentLocationId();
    String temporaryLocationId = record.getTemporaryLocationId();

    if (temporaryLocationId.isEmpty() || temporaryLocationId == null) {
      return permanentLocationId;
    } else {
      return temporaryLocationId;
    }
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

  private Future<HoldingsRecord> setHrid(HoldingsRecord entity) {
    return isBlank(entity.getHrid())
      ? hridManager.getNextHoldingsHrid().map(entity::withHrid)
      : succeededFuture(entity);
  }

  private Future<BatchOperationContext<HoldingsRecord>> buildBatchOperationContext(
    boolean upsert, List<HoldingsRecord> allHrs) {

    if (!upsert) {
      return succeededFuture(new BatchOperationContext<>(allHrs, emptyList()));
    }

    return holdingsRepository.getById(allHrs, HoldingsRecord::getId)
      .map(foundHrs -> {
        final var hrsToBeCreated = allHrs.stream()
          .filter(hr -> !foundHrs.containsKey(hr.getId()))
          .collect(toList());

        return new BatchOperationContext<>(hrsToBeCreated, foundHrs.values());
      });
  }
}
