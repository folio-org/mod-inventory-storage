package org.folio.services.holding;

import static io.vertx.core.Promise.promise;
import static org.apache.logging.log4j.LogManager.getLogger;
import static org.folio.rest.impl.HoldingsStorageApi.HOLDINGS_RECORD_TABLE;
import static org.folio.rest.impl.StorageHelper.MAX_ENTITIES;
import static org.folio.rest.jaxrs.resource.HoldingsStorage.DeleteHoldingsStorageHoldingsByHoldingsRecordIdResponse;
import static org.folio.rest.jaxrs.resource.HoldingsStorage.DeleteHoldingsStorageHoldingsResponse;
import static org.folio.rest.jaxrs.resource.HoldingsStorage.PostHoldingsStorageHoldingsResponse;
import static org.folio.rest.jaxrs.resource.HoldingsStorage.PutHoldingsStorageHoldingsByHoldingsRecordIdResponse;
import static org.folio.rest.jaxrs.resource.HoldingsStorageBatchSynchronous.PostHoldingsStorageBatchSynchronousResponse;
import static org.folio.rest.persist.PgUtil.deleteById;
import static org.folio.rest.persist.PgUtil.postSync;
import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.rest.persist.PgUtil.respond422method;
import static org.folio.rest.tools.messages.Messages.DEFAULT_LANGUAGE;
import static org.folio.services.batch.BatchOperationContextFactory.buildBatchOperationContext;
import static org.folio.utils.ComparisonUtils.equalsIgnoringMetadata;
import static org.folio.validator.HridValidators.refuseWhenHridChanged;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.folio.dbschema.ObjectMapperTool;
import org.folio.persist.HoldingsRepository;
import org.folio.persist.InstanceRepository;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.resource.support.ResponseDelegate;
import org.folio.rest.persist.PgExceptionFacade;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.SQLConnection;
import org.folio.rest.support.CqlQuery;
import org.folio.rest.support.HridManager;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.services.ResponseHandlerUtil;
import org.folio.services.caches.ConsortiumData;
import org.folio.services.caches.ConsortiumDataCache;
import org.folio.services.consortium.ConsortiumService;
import org.folio.services.consortium.ConsortiumServiceImpl;
import org.folio.services.consortium.entities.SharingInstance;
import org.folio.services.domainevent.HoldingDomainEventPublisher;
import org.folio.services.domainevent.ItemDomainEventPublisher;
import org.folio.services.item.ItemService;
import org.folio.validator.CommonValidators;
import org.folio.validator.NotesValidators;

public class HoldingsService {
  private static final Logger log = getLogger(HoldingsService.class);
  private static final ObjectMapper OBJECT_MAPPER = ObjectMapperTool.getMapper();
  private static final String LOCATION_PREFIX = "/holdings-storage/holdings/";
  private static final String RESPOND_400_WITH_TEXT_PLAIN = "respond400WithTextPlain";
  private static final String RESPOND_500_WITH_TEXT_PLAIN = "respond500WithTextPlain";
  private static final Pattern INSTANCEID_PATTERN = Pattern.compile(
      "^ *instanceId *== *\"?("
      // UUID
      + "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
      + ")\"?"
      + " +sortBy"
      // allow any sub-set of the fields and allow the fields in any order
      + "(( +(effectiveLocation\\.name|callNumberPrefix|callNumber|callNumberSuffix))+) *$");

  private final Messages messages = Messages.getInstance();
  private final Context vertxContext;
  private final Map<String, String> okapiHeaders;
  private final PostgresClient postgresClient;
  private final HridManager hridManager;
  private final ItemService itemService;
  private final HoldingsRepository holdingsRepository;
  private final ItemDomainEventPublisher itemEventService;
  private final HoldingDomainEventPublisher domainEventPublisher;
  private final InstanceRepository instanceRepository;
  private final ConsortiumService consortiumService;

  public HoldingsService(Context context, Map<String, String> okapiHeaders) {
    this.vertxContext = context;
    this.okapiHeaders = okapiHeaders;

    itemService = new ItemService(context, okapiHeaders);
    postgresClient = postgresClient(context, okapiHeaders);
    hridManager = new HridManager(postgresClient);
    holdingsRepository = new HoldingsRepository(context, okapiHeaders);
    itemEventService = new ItemDomainEventPublisher(context, okapiHeaders);
    domainEventPublisher = new HoldingDomainEventPublisher(context, okapiHeaders);
    instanceRepository = new InstanceRepository(context, okapiHeaders);
    consortiumService = new ConsortiumServiceImpl(context.owner().createHttpClient(),
      context.get(ConsortiumDataCache.class.getName()));
  }

  /**
   * Returns Response if the query is supported by instanceId query, null otherwise.
   *
   * <p>
   */
  public Future<Response> getByInstanceId(int offset, int limit, String query) {
    if (query == null) {
      return Future.succeededFuture();
    }
    var matcher = INSTANCEID_PATTERN.matcher(query);
    if (!matcher.find()) {
      return Future.succeededFuture();
    }
    var instanceId = matcher.group(1);
    var sortBy = matcher.group(2).split(" +");
    return holdingsRepository.getByInstanceId(instanceId, sortBy, offset, limit)
        .map(row -> {
          var json = "{ \"holdingsRecords\": " + row.getString("holdings") + ",\n"
              + "  \"totalRecords\": " + row.getLong("total_records") + ",\n"
              + "  \"resultInfo\": { \n"
              + "    \"totalRecords\": " + row.getLong("total_records") + ",\n"
              + "    \"totalRecordsEstimated\": false\n"
              + "  }\n"
              + "}";
          return Response.ok(json, MediaType.APPLICATION_JSON).build();
        })
        .onFailure(e -> log.error("getByInstanceId:: {}", e.getMessage(), e));
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
          return updateHolding(existingHoldingsRecord, holdingsRecord);
        } else {
          return createHolding(holdingsRecord);
        }
      });
  }

  public Future<Response> createHolding(HoldingsRecord entity) {
    entity.setEffectiveLocationId(calculateEffectiveLocation(entity));

    return createShadowInstancesIfNeeded(List.of(entity))
      .compose(v -> hridManager.populateHrid(entity))
      .compose(NotesValidators::refuseLongNotes)
      .compose(hr -> {
        if (StringUtils.isEmpty(hr.getId())) {
          hr.setId(UUID.randomUUID().toString());
        }
        final Promise<Response> postResponse = promise();

        holdingsRepository.save(hr.getId(), hr, reply -> {
          try {
            if (reply.succeeded()) {
              postResponse.handle(Future.succeededFuture(PostHoldingsStorageHoldingsResponse
                .respond201WithApplicationJson(reply.result(),
                  PostHoldingsStorageHoldingsResponse.headersFor201().withLocation(LOCATION_PREFIX))));
            } else {
              handleFailedResponse(hr.getId(), reply.cause(), postResponse);
            }
          } catch (Exception e) {
            internalServerErrorDuringPost(e, postResponse);
          }
        });
        return postResponse.future()
          .onSuccess(domainEventPublisher.publishCreated());
      })
      .map(ResponseHandlerUtil::handleHridError);
  }

  private void internalServerErrorDuringPost(Throwable e, Handler<AsyncResult<Response>> handler) {
    log.error(e.getMessage(), e);
    handler.handle(Future.succeededFuture(PostHoldingsStorageHoldingsResponse
      .respond500WithTextPlain(messages.getMessage(DEFAULT_LANGUAGE, MessageConsts.InternalServerError))));
  }

  private void handleFailedResponse(String id, Throwable cause, Handler<AsyncResult<Response>> handler) {
    log.error(cause.getMessage(), cause);
    try {
      Class<? extends ResponseDelegate> responseClass = PostHoldingsStorageHoldingsResponse.class;
      Method respond400 = responseClass.getMethod(RESPOND_400_WITH_TEXT_PLAIN, Object.class);
      Method respond500 = responseClass.getMethod(RESPOND_500_WITH_TEXT_PLAIN, Object.class);
      PgExceptionFacade pgException = new PgExceptionFacade(cause);
      if (pgException.isForeignKeyViolation()) {
        handler.handle(PgUtil.responseForeignKeyViolation(HOLDINGS_RECORD_TABLE, id, pgException,
          respond422method(responseClass), respond400, respond500));
      }
      if (pgException.isUniqueViolation()) {
        handler.handle(PgUtil.responseUniqueViolation(HOLDINGS_RECORD_TABLE, id, pgException,
          respond422method(responseClass), respond400, respond500));
      }
      if (pgException.isInvalidTextRepresentation()) {
        handler.handle(PgUtil.response(pgException.getMessage(), respond400, respond500));
      }
      handler.handle(PgUtil.response(pgException.getMessage(), respond500, respond500));
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      handler.handle(Future.failedFuture(e));
    }
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
        rowSet.iterator().forEachRemaining(row -> {
            try {
              var holdingId = OBJECT_MAPPER.readTree(row.getString(1)).get("id").textValue();
              domainEventPublisher.publishRemoved(holdingId, row.getString(1));
            } catch (JsonProcessingException ex) {
              log.error("deleteHoldings:: Failed to parse json : {}", ex.getMessage(), ex);
              throw new IllegalArgumentException(ex.getCause());
            }
          }
        )
      ))
      .map(Response.noContent().build());
  }

  public Future<Response> createHoldings(List<HoldingsRecord> holdings, boolean upsert, boolean optimisticLocking) {
    for (HoldingsRecord holdingsRecord : holdings) {
      holdingsRecord.setEffectiveLocationId(calculateEffectiveLocation(holdingsRecord));
    }

    return createShadowInstancesIfNeeded(holdings)
      .compose(ar -> hridManager.populateHridForHoldings(holdings)
        .compose(NotesValidators::refuseHoldingLongNotes)
        .compose(result -> buildBatchOperationContext(upsert, holdings,
          holdingsRepository, HoldingsRecord::getId, true))
        .compose(batchOperation -> postSync(HOLDINGS_RECORD_TABLE, holdings, MAX_ENTITIES,
          upsert, optimisticLocking, okapiHeaders, vertxContext, PostHoldingsStorageBatchSynchronousResponse.class)
          .onSuccess(domainEventPublisher.publishCreatedOrUpdated(batchOperation))))
      .map(ResponseHandlerUtil::handleHridError);
  }

  public Future<Void> publishReindexHoldingsRecords(String rangeId, String fromId, String toId) {
    return holdingsRepository.getReindexHoldingsRecords(fromId, toId)
      .compose(holdings -> domainEventPublisher.publishReindexHoldings(rangeId, holdings));
  }

  private Future<Response> updateHolding(HoldingsRecord oldHoldings, HoldingsRecord newHoldings) {
    newHoldings.setEffectiveLocationId(calculateEffectiveLocation(newHoldings));

    return createShadowInstancesIfNeeded(List.of(newHoldings))
      .compose(v -> {
        try {
          var noChanges = equalsIgnoringMetadata(oldHoldings, newHoldings);
          if (noChanges) {
            return Future.succeededFuture()
              .map(res -> PutHoldingsStorageHoldingsByHoldingsRecordIdResponse.respond204());
          }
        } catch (Exception e) {
          return Future.failedFuture(e);
        }

        if (Integer.valueOf(-1).equals(newHoldings.getVersion())) {
          newHoldings.setVersion(null);  // enforce optimistic locking
        }

        return refuseWhenHridChanged(oldHoldings, newHoldings)
          .compose(notUsed -> NotesValidators.refuseLongNotes(newHoldings))
          .compose(notUsed -> {
            final Promise<List<Item>> overallResult = promise();

            postgresClient.startTx(
              connection -> holdingsRepository.update(connection, oldHoldings.getId(), newHoldings)
                .compose(updateRes -> itemService.updateItemsOnHoldingChanged(connection, newHoldings))
                .onComplete(handleTransaction(connection, overallResult))
            );

            return overallResult.future()
              .compose(itemsBeforeUpdate -> itemEventService
                .publishUpdated(oldHoldings, newHoldings, itemsBeforeUpdate))
              .<Response>map(res -> PutHoldingsStorageHoldingsByHoldingsRecordIdResponse.respond204())
              .onSuccess(domainEventPublisher.publishUpdated(oldHoldings));
          });
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

  private Future<Void> createShadowInstancesIfNeeded(List<HoldingsRecord> holdingsRecords) {
    return consortiumService.getConsortiumData(okapiHeaders)
        .compose(consortiumDataOptional -> {
          if (consortiumDataOptional.isPresent()) {
            return createShadowInstancesIfNeeded(holdingsRecords, consortiumDataOptional.get());
          }
          return Future.succeededFuture();
        });
  }

  private Future<Void> createShadowInstancesIfNeeded(List<HoldingsRecord> holdingsRecords,
                                                        ConsortiumData consortiumData) {
    Map<String, Future<SharingInstance>> instanceFuturesMap = new HashMap<>();

    for (HoldingsRecord holdingRecord : holdingsRecords) {
      String instanceId = holdingRecord.getInstanceId();
      instanceFuturesMap.computeIfAbsent(instanceId, v -> createShadowInstanceIfNeeded(instanceId, consortiumData));
    }
    return Future.all(new ArrayList<>(instanceFuturesMap.values()))
        .mapEmpty();
  }

  private Future<SharingInstance> createShadowInstanceIfNeeded(String instanceId, ConsortiumData consortiumData) {
    return instanceRepository.exists(instanceId)
      .compose(exists -> Boolean.TRUE.equals(exists) ? Future.succeededFuture() :
        consortiumService.createShadowInstance(instanceId, consortiumData, okapiHeaders)
      );
  }

  private boolean holdingsRecordFound(HoldingsRecord holdingsRecord) {
    return holdingsRecord != null;
  }
}
