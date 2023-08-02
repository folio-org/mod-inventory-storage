package org.folio.services.holding;

import static io.vertx.core.Promise.promise;
import static org.folio.rest.impl.HoldingsStorageApi.HOLDINGS_RECORD_TABLE;
import static org.folio.rest.impl.StorageHelper.MAX_ENTITIES;
import static org.folio.rest.jaxrs.resource.HoldingsStorage.DeleteHoldingsStorageHoldingsByHoldingsRecordIdResponse;
import static org.folio.rest.jaxrs.resource.HoldingsStorage.DeleteHoldingsStorageHoldingsResponse;
import static org.folio.rest.jaxrs.resource.HoldingsStorage.PostHoldingsStorageHoldingsResponse;
import static org.folio.rest.jaxrs.resource.HoldingsStorage.PutHoldingsStorageHoldingsByHoldingsRecordIdResponse;
import static org.folio.rest.jaxrs.resource.HoldingsStorageBatchSynchronous.PostHoldingsStorageBatchSynchronousResponse;
import static org.folio.rest.persist.PgUtil.deleteById;
import static org.folio.rest.persist.PgUtil.post;
import static org.folio.rest.persist.PgUtil.postSync;
import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.services.batch.BatchOperationContextFactory.buildBatchOperationContext;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.folio.persist.HoldingsRepository;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.CqlQuery;
import org.folio.rest.support.EndpointFailureHandler;
import org.folio.rest.support.HridManager;
import org.folio.rest.tools.utils.OptimisticLockingUtil;
import org.folio.services.domainevent.HoldingDomainEventPublisher;
import org.folio.services.domainevent.ItemDomainEventPublisher;
import org.folio.validator.CommonValidators;
import org.folio.validator.NotesValidators;

public class HoldingsService {
  private static final String INSTANCE_ID = "instanceId";
  private final Context vertxContext;
  private final Map<String, String> okapiHeaders;
  private final PostgresClient postgresClient;
  private final HridManager hridManager;
  private final HoldingsRepository holdingsRepository;
  private final ItemDomainEventPublisher itemEventService;
  private final HoldingDomainEventPublisher domainEventPublisher;

  public HoldingsService(Context context, Map<String, String> okapiHeaders) {
    this.vertxContext = context;
    this.okapiHeaders = okapiHeaders;

    postgresClient = postgresClient(context, okapiHeaders);
    hridManager = new HridManager(postgresClient);
    holdingsRepository = new HoldingsRepository(context, okapiHeaders);
    itemEventService = new ItemDomainEventPublisher(context, okapiHeaders);
    domainEventPublisher = new HoldingDomainEventPublisher(context, okapiHeaders);
  }

  /**
   * Deletes all holdings but sends only a single domain event (Kafka) message "all records removed",
   * this is much faster than sending one message for each deleted holding.
   */
  public Future<Response> deleteAllHoldings() {
    return holdingsRepository.deleteAll()
      .onSuccess(notUsed -> domainEventPublisher.publishAllRemoved())
      .map(Response.noContent().build());
  }

  public Future<Response> updateHoldingRecord(String holdingId, HoldingsRecord holdingsRecord) {
    return holdingsRepository.getById(holdingId)
      .compose(existingHoldingsRecord -> {
        if (holdingsRecordFound(existingHoldingsRecord)) {
          return updateHolding(holdingsRecord);
        } else {
          return createHolding(holdingsRecord);
        }
      });
  }

  public Future<Response> createHolding(HoldingsRecord entity) {
    entity.setEffectiveLocationId(calculateEffectiveLocation(entity));
    return hridManager.populateHrid(entity)
      .compose(NotesValidators::refuseLongNotes)
      .compose(hr -> {
        final Promise<Response> postResponse = promise();

        post(HOLDINGS_RECORD_TABLE, hr, okapiHeaders, vertxContext,
          PostHoldingsStorageHoldingsResponse.class, postResponse);

        return postResponse.future()
          .onSuccess(domainEventPublisher.publishCreated());
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
          .onSuccess(domainEventPublisher.publishRemoved(hr));
      });
  }

  public Future<Response> deleteHoldings(String cql) {
    if (StringUtils.isBlank(cql)) {
      return Future.succeededFuture(
        DeleteHoldingsStorageHoldingsResponse.respond400WithTextPlain(
          "Expected CQL but query parameter is empty"));
    }
    if (new CqlQuery(cql).isMatchingAll()) {
      return deleteAllHoldings();  // faster: sends only one domain event (Kafka) message
    }
    // do not add curly braces for readability, this is to comply with
    // https://sonarcloud.io/organizations/folio-org/rules?open=java%3AS1602&rule_key=java%3AS1602
    return holdingsRepository.delete(cql)
      .onSuccess(rowSet -> vertxContext.runOnContext(runLater ->
        rowSet.iterator().forEachRemaining(row ->
          domainEventPublisher.publishRemoved(row.getString(0), row.getString(1))
        )
      ))
      .map(Response.noContent().build());
  }

  public Future<Response> createHoldings(List<HoldingsRecord> holdings, boolean upsert, boolean optimisticLocking) {
    if (upsert) {
      return upsertHoldings(holdings, optimisticLocking);
    }

    for (HoldingsRecord holdingsRecord : holdings) {
      holdingsRecord.setEffectiveLocationId(calculateEffectiveLocation(holdingsRecord));
    }

    return hridManager.populateHridForHoldings(holdings)
      .compose(NotesValidators::refuseHoldingLongNotes)
      .compose(result -> buildBatchOperationContext(upsert, holdings,
        holdingsRepository, HoldingsRecord::getId))
      .compose(batchOperation -> postSync(HOLDINGS_RECORD_TABLE, holdings, MAX_ENTITIES, upsert, optimisticLocking,
        okapiHeaders, vertxContext, PostHoldingsStorageBatchSynchronousResponse.class)
        .onSuccess(domainEventPublisher.publishCreatedOrUpdated(batchOperation)));
  }


  public Future<Response> upsertHoldings(List<HoldingsRecord> holdings, boolean optimisticLocking) {
    try {
      for (HoldingsRecord holdingsRecord : holdings) {
        holdingsRecord.setEffectiveLocationId(calculateEffectiveLocation(holdingsRecord));
      }

      if (optimisticLocking) {
        OptimisticLockingUtil.unsetVersionIfMinusOne(holdings);
      } else {
        if (! OptimisticLockingUtil.isSuppressingOptimisticLockingAllowed()) {
          return Future.succeededFuture(Response.status(413).entity(
              "DB_ALLOW_SUPPRESS_OPTIMISTIC_LOCKING environment variable doesn't allow "
                  + "to disable optimistic locking").build());
        }
        OptimisticLockingUtil.setVersionToMinusOne(holdings);
      }

      return NotesValidators.refuseHoldingLongNotes(holdings)
          .compose(x -> holdingsRepository.upsert(holdings))
          .onSuccess(this::publishEvents)
          .map(Response.status(201).build())
          .otherwise(EndpointFailureHandler::failureResponse);
    } catch (ReflectiveOperationException e) {
      throw new SecurityException(e);
    }
  }

  private Future<Response> updateHolding(HoldingsRecord newHoldings) {
    newHoldings.setEffectiveLocationId(calculateEffectiveLocation(newHoldings));

    if (Integer.valueOf(-1).equals(newHoldings.getVersion())) {
      newHoldings.setVersion(null);  // enforce optimistic locking
    }

    return NotesValidators.refuseLongNotes(newHoldings)
        .compose(x -> holdingsRepository.upsert(List.of(newHoldings)))
        .onSuccess(this::publishEvents)
        .map(x -> PutHoldingsStorageHoldingsByHoldingsRecordIdResponse.respond204());
  }

  private void publishEvents(JsonObject holdingsItems) {
    Map<String, String> instanceIdByHoldingsRecordId = new HashMap<>();
    holdingsItems.getJsonArray("holdingsRecords").forEach(o -> {
      var oldHolding = ((JsonObject) o).getJsonObject("old");
      var newHolding = ((JsonObject) o).getJsonObject("new");
      var instanceId = newHolding.getString(INSTANCE_ID);
      var holdingId = newHolding.getString("id");
      instanceIdByHoldingsRecordId.put(holdingId, instanceId);
      domainEventPublisher.publishUpserted(instanceId, oldHolding, newHolding);
    });
    holdingsItems.getJsonArray("items").forEach(o -> {
      var oldItem = ((JsonObject) o).getJsonObject("old");
      var newItem = ((JsonObject) o).getJsonObject("new");
      var instanceId = instanceIdByHoldingsRecordId.get(newItem.getString("holdingsRecordId"));
      oldItem.put(INSTANCE_ID, instanceId);
      newItem.put(INSTANCE_ID, instanceId);
      itemEventService.publishUpserted(instanceId, oldItem, newItem);
    });
  }

  private String calculateEffectiveLocation(HoldingsRecord holdingsRecord) {
    String permanentLocationId = holdingsRecord.getPermanentLocationId();
    String temporaryLocationId = holdingsRecord.getTemporaryLocationId();

    if (temporaryLocationId != null) {
      return temporaryLocationId;
    } else {
      return permanentLocationId;
    }
  }

  private boolean holdingsRecordFound(HoldingsRecord holdingsRecord) {
    return holdingsRecord != null;
  }
}
