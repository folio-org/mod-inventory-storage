package org.folio.services.holding;

import static io.vertx.core.Promise.promise;
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
import static org.folio.services.batch.BatchOperationContextFactory.buildBatchOperationContext;
import static org.folio.validator.HridValidators.refuseWhenHridChanged;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.Logger;
import org.folio.persist.HoldingsRepository;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.SQLConnection;
import org.folio.rest.support.HridManager;
import org.folio.services.domainevent.HoldingDomainEventPublisher;
import org.folio.services.domainevent.ItemDomainEventPublisher;
import org.folio.services.item.ItemService;
import org.folio.validator.CommonValidators;

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
    entity.setEffectiveLocationId(calculateEffectiveLocation(entity));
    return hridManager.populateHrid(entity)
      .compose(hr -> {
        final Promise<Response> postResponse = promise();

        post(HOLDINGS_RECORD_TABLE, hr, okapiHeaders, vertxContext,
          PostHoldingsStorageHoldingsResponse.class, postResponse);

        return postResponse.future()
          .compose(domainEventPublisher.publishCreated());
      });
  }

  private Future<Response> updateHolding(HoldingsRecord oldHoldings, HoldingsRecord newHoldings) {
    newHoldings.setEffectiveLocationId(calculateEffectiveLocation(newHoldings));

    return refuseWhenHridChanged(oldHoldings, newHoldings)
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
    for (HoldingsRecord record : holdings) {
      record.setEffectiveLocationId(calculateEffectiveLocation(record));
    }

    return hridManager.populateHridForHoldings(holdings)
      .compose(result -> buildBatchOperationContext(upsert, holdings,
        holdingsRepository, HoldingsRecord::getId))
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

    if (temporaryLocationId != null) {
      return temporaryLocationId;
    } else {
      return permanentLocationId;
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

  private boolean holdingsRecordFound(HoldingsRecord holdingsRecord) {
    return holdingsRecord != null;
  }
}
