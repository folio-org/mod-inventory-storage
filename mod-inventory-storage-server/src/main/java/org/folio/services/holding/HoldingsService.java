package org.folio.services.holding;

import static java.util.stream.Collectors.toMap;
import static org.apache.logging.log4j.LogManager.getLogger;
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
import static org.folio.rest.support.CollectionUtil.deepCopy;
import static org.folio.services.batch.BatchOperationContextFactory.buildBatchOperationContext;
import static org.folio.utils.ComparisonUtils.equalsIgnoringMetadata;
import static org.folio.validator.CommonValidators.validateUuidFormat;
import static org.folio.validator.CommonValidators.validateUuidFormatForList;
import static org.folio.validator.HridValidators.refuseWhenHridChanged;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Logger;
import org.folio.dbschema.ObjectMapperTool;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.persist.HoldingsRepository;
import org.folio.persist.InstanceRepository;
import org.folio.persist.ItemRepository;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.ReindexRecordsRequest;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.CqlQuery;
import org.folio.rest.support.HridManager;
import org.folio.rest.tools.utils.MetadataUtil;
import org.folio.rest.tools.utils.OptimisticLockingUtil;
import org.folio.services.ItemEffectiveValuesService;
import org.folio.services.ResponseHandlerUtil;
import org.folio.services.caches.ConsortiumData;
import org.folio.services.caches.ConsortiumDataCache;
import org.folio.services.consortium.ConsortiumService;
import org.folio.services.consortium.ConsortiumServiceImpl;
import org.folio.services.consortium.entities.SharingInstance;
import org.folio.services.item.ItemService;
import org.folio.services.reindex.ReindexFileReadyEvent;
import org.folio.services.reindex.ReindexFileReadyEventPublisher;
import org.folio.services.reindex.ReindexS3ExportService;
import org.folio.services.s3storage.FolioS3ClientFactory;
import org.folio.services.sanitizer.Sanitizer;
import org.folio.services.sanitizer.SanitizerFactory;
import org.folio.validator.CommonValidators;
import org.folio.validator.NotesValidators;

public class HoldingsService {
  private static final Logger log = getLogger(HoldingsService.class);
  private static final ObjectMapper OBJECT_MAPPER = ObjectMapperTool.getMapper();
  private static final Pattern INSTANCEID_PATTERN = Pattern.compile(
    "^ *instanceId *== *\"?("
    // UUID
    + "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
    + ")\"?"
    + " +sortBy"
    // allow any sub-set of the fields and allow the fields in any order
    + "(( +(effectiveLocation\\.name|callNumberPrefix|callNumber|callNumberSuffix))+) *$");
  private static final Pattern SPACE_REGEX = Pattern.compile(" +");

  private final Context vertxContext;
  private final Map<String, String> okapiHeaders;
  private final PostgresClient postgresClient;
  private final HridManager hridManager;
  private final ItemService itemService;
  private final HoldingsRepository holdingsRepository;
  private final InstanceRepository instanceRepository;
  private final ConsortiumService consortiumService;
  private final ItemEffectiveValuesService effectiveValuesService;
  private final HoldingsUpsertSqlBuilder upsertSqlBuilder;
  private final HoldingsEventPublisher eventPublisher;
  private final Sanitizer<HoldingsRecord> sanitizer;

  public HoldingsService(Context context, Map<String, String> okapiHeaders) {
    this.vertxContext = context;
    this.okapiHeaders = okapiHeaders;

    this.itemService = new ItemService(context, okapiHeaders);
    this.postgresClient = postgresClient(context, okapiHeaders);
    this.hridManager = new HridManager(postgresClient);
    this.holdingsRepository = new HoldingsRepository(context, okapiHeaders);
    this.instanceRepository = new InstanceRepository(context, okapiHeaders);
    this.consortiumService = new ConsortiumServiceImpl(context.owner().createHttpClient(),
      context.get(ConsortiumDataCache.class.getName()));
    this.effectiveValuesService = new ItemEffectiveValuesService(context, okapiHeaders);
    this.upsertSqlBuilder = new HoldingsUpsertSqlBuilder(holdingsRepository, new ItemRepository(context, okapiHeaders));
    this.eventPublisher = new HoldingsEventPublisher(context, okapiHeaders);
    this.sanitizer = SanitizerFactory.getSanitizer(HoldingsRecord.class);
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
    var sortBy = SPACE_REGEX.split(matcher.group(2));
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
      .onSuccess(notUsed -> eventPublisher.publishAllRemoved())
      .map(Response.noContent().build());
  }

  public Future<Response> updateHoldingRecord(String holdingId, HoldingsRecord holdingsRecord) {
    sanitizer.sanitize(holdingsRecord);
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
    sanitizer.sanitize(entity);

    return validateUuidFormat(entity.getStatisticalCodeIds())
      .compose(v -> createShadowInstancesIfNeeded(List.of(entity)))
      .compose(v -> hridManager.populateHrid(entity))
      .compose(NotesValidators::refuseLongNotes)
      .compose(hr -> post(HOLDINGS_RECORD_TABLE, hr, okapiHeaders, vertxContext,
        PostHoldingsStorageHoldingsResponse.class)
        .onSuccess(eventPublisher.publishCreated()))
      .map(ResponseHandlerUtil::handleHridError);
  }

  public Future<Response> deleteHolding(String hrId) {
    return holdingsRepository.getById(hrId)
      .compose(CommonValidators::refuseIfNotFound)
      .compose(hr -> deleteById(HOLDINGS_RECORD_TABLE, hrId, okapiHeaders, vertxContext,
        DeleteHoldingsStorageHoldingsByHoldingsRecordIdResponse.class)
        .onSuccess(eventPublisher.publishRemoved(hr)));
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
              eventPublisher.publishRemoved(holdingId, row.getString(1));
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
    for (var holdingsRecord : holdings) {
      holdingsRecord.setEffectiveLocationId(calculateEffectiveLocation(holdingsRecord));
      sanitizer.sanitize(holdingsRecord);
    }

    return validateUuidFormatForList(holdings, HoldingsRecord::getStatisticalCodeIds)
      .compose(v -> createShadowInstancesIfNeeded(holdings))
      .compose(ar -> hridManager.populateHridForHoldings(holdings)
        .compose(NotesValidators::refuseHoldingLongNotes))
      .compose(validatedHoldings -> {
        if (!upsert) {
          // For create-only operations, use existing logic
          return buildBatchOperationContext(false, validatedHoldings,
            holdingsRepository, HoldingsRecord::getId, true)
            .compose(batchOperation -> postSync(HOLDINGS_RECORD_TABLE, validatedHoldings, MAX_ENTITIES,
              false, optimisticLocking, okapiHeaders, vertxContext, PostHoldingsStorageBatchSynchronousResponse.class)
              .onSuccess(eventPublisher.publishCreatedOrUpdated(batchOperation)));
        }

        // For upsert operations, use the new transaction-based approach
        return performUpsertWithItemUpdates(validatedHoldings, optimisticLocking);
      })
      .map(ResponseHandlerUtil::handleHridError);
  }

  public Future<Void> publishReindexHoldingsRecords(String rangeId, String fromId, String toId) {
    return holdingsRepository.getReindexHoldingsRecords(fromId, toId)
      .compose(holdings -> eventPublisher.publishReindexHoldings(rangeId, holdings));
  }

  public Future<Void> exportReindexHoldingsRecords(ReindexRecordsRequest request) {
    var s3Client = FolioS3ClientFactory.getFolioS3Client(FolioS3ClientFactory.S3ConfigType.REINDEX);
    var tenantId = okapiHeaders.get(XOkapiHeaders.TENANT);
    var traceId = StringUtils.isBlank(request.getTraceId()) ? UUID.randomUUID().toString() : request.getTraceId();
    var s3Key = tenantId + "/holdings/" + traceId + "/" + request.getId() + ".ndjson";
    var exportService = new ReindexS3ExportService(vertxContext, s3Client);
    var fileReadyPublisher = new ReindexFileReadyEventPublisher(vertxContext, okapiHeaders);
    var rangeFrom = request.getRecordIdsRange().getFrom();
    var rangeTo = request.getRecordIdsRange().getTo();

    return postgresClient.withTrans(conn ->
        holdingsRepository.streamReindexHoldingsRecords(conn, rangeFrom, rangeTo)
          .compose(rowStream -> exportService.exportToS3(rowStream, s3Key)))
      .compose(v -> {
        var bucket = FolioS3ClientFactory.getBucketName(FolioS3ClientFactory.S3ConfigType.REINDEX);
        var event = ReindexFileReadyEvent.builder()
          .tenantId(tenantId)
          .recordType(request.getRecordType().value())
          .range(rangeFrom, rangeTo)
          .jobId(traceId)
          .bucket(bucket)
          .objectKey(s3Key)
          .build();
        return fileReadyPublisher.publish(event);
      });
  }

  private Future<Response> updateHolding(HoldingsRecord oldHoldings, HoldingsRecord newHoldings) {
    newHoldings.setEffectiveLocationId(calculateEffectiveLocation(newHoldings));

    return createShadowInstancesIfNeeded(List.of(newHoldings))
      .compose(v -> checkAndPerformUpdate(oldHoldings, newHoldings));
  }

  private Future<Response> checkAndPerformUpdate(HoldingsRecord oldHoldings, HoldingsRecord newHoldings) {
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
      .compose(notUsed -> performHoldingsUpdate(oldHoldings, newHoldings));
  }

  private Future<Response> performHoldingsUpdate(HoldingsRecord oldHoldings, HoldingsRecord newHoldings) {
    return postgresClient
      .withTrans(conn -> holdingsRepository.update(conn, oldHoldings.getId(), newHoldings)
        .compose(updateRes -> itemService.updateItemsOnHoldingChanged(conn, newHoldings)))
      .onSuccess(itemsBeforeUpdate ->
        eventPublisher.publishUpdatedItems(oldHoldings, newHoldings, itemsBeforeUpdate))
      .<Response>map(res -> PutHoldingsStorageHoldingsByHoldingsRecordIdResponse.respond204())
      .onSuccess(eventPublisher.publishUpdated(oldHoldings));
  }

  private Future<Response> performUpsertWithItemUpdates(List<HoldingsRecord> holdings, boolean optimisticLocking) {
    Future<Response> validationResult = handleOptimisticLocking(holdings, optimisticLocking);
    if (validationResult != null) {
      return validationResult;
    }

    try {
      MetadataUtil.populateMetadata(holdings, okapiHeaders);
    } catch (ReflectiveOperationException e) {
      log.warn("performUpsertWithItemUpdates:: Error during metadata population", e);
      return Future.failedFuture(e.getMessage());
    }

    ensureHoldingsHaveIds(holdings);

    return postgresClient.withTrans(conn -> upsertHoldingsAndGetOldContent(conn, holdings)
        .compose(upsertResult -> updateItemsForHoldingsChange(conn, holdings, upsertResult)
          .map(itemsBeforeUpdate -> Pair.of(upsertResult.getLeft(), itemsBeforeUpdate))))
      .onSuccess(oldData ->
        eventPublisher.publishHoldingsAndItemEvents(holdings, oldData.getLeft(), oldData.getRight()))
      .map(v -> PostHoldingsStorageBatchSynchronousResponse.respond201());
  }

  private Future<Response> handleOptimisticLocking(List<HoldingsRecord> holdings, boolean optimisticLocking) {
    if (optimisticLocking) {
      holdings.stream().filter(holding -> Objects.equals(-1, holding.getVersion()))
        .forEach(holding -> holding.setVersion(null));
      return null;
    } else {
      if (!OptimisticLockingUtil.isSuppressingOptimisticLockingAllowed()) {
        return Future.succeededFuture(PostHoldingsStorageBatchSynchronousResponse.respond413WithTextPlain(
          "DB_ALLOW_SUPPRESS_OPTIMISTIC_LOCKING environment variable doesn't allow to disable optimistic locking"));
      }
      holdings.forEach(holding -> holding.setVersion(-1));
      return null;
    }
  }

  private void ensureHoldingsHaveIds(List<HoldingsRecord> holdings) {
    holdings.forEach(holding -> holding.setId(holding.getId() == null
                                              ? UUID.randomUUID().toString()
                                              : holding.getId()));
  }

  private Future<Pair<Map<String, HoldingsRecord>, Map<String, List<Item>>>> upsertHoldingsAndGetOldContent(
    Conn conn, List<HoldingsRecord> holdings) {
    if (holdings.isEmpty()) {
      return Future.succeededFuture(Pair.of(Map.of(), Map.of()));
    }

    var sqlAndParamsResult = upsertSqlBuilder.buildUpsertSqlWithParams(holdings);
    if (sqlAndParamsResult.getLeft() == null) {
      return Future.failedFuture(sqlAndParamsResult.getRight());
    }

    var sqlAndParams = sqlAndParamsResult.getLeft();
    return conn.execute(sqlAndParams.getLeft(), sqlAndParams.getRight())
      .map(HoldingsUpsertResultProcessor::processUpsertResultSet);
  }

  private Future<Map<String, List<Item>>> updateItemsForHoldingsChange(
    Conn conn,
    List<HoldingsRecord> newHoldings,
    Pair<Map<String, HoldingsRecord>, Map<String, List<Item>>> upsertResult) {

    var oldHoldingsMap = upsertResult.getLeft();
    var oldItemsMap = upsertResult.getRight();

    var holdingsMap = newHoldings.stream()
      .collect(toMap(HoldingsRecord::getId, h -> h));

    var itemsBeforeUpdate = new HashMap<String, List<Item>>();
    var allItemsToUpdate = new ArrayList<Item>();

    try {
      processItemsForHoldingsUpdate(oldItemsMap, holdingsMap, oldHoldingsMap,
                                     itemsBeforeUpdate, allItemsToUpdate);
    } catch (JsonProcessingException ex) {
      return Future.failedFuture(ex);
    }

    if (allItemsToUpdate.isEmpty()) {
      return Future.succeededFuture(itemsBeforeUpdate);
    }

    return itemService.updateBatch(conn, allItemsToUpdate)
      .map(notUsed -> itemsBeforeUpdate);
  }

  private void processItemsForHoldingsUpdate(Map<String, List<Item>> oldItemsMap,
                                              Map<String, HoldingsRecord> holdingsMap,
                                              Map<String, HoldingsRecord> oldHoldingsMap,
                                              Map<String, List<Item>> itemsBeforeUpdate,
                                              List<Item> allItemsToUpdate) throws JsonProcessingException {
    for (var entry : oldItemsMap.entrySet()) {
      var holdingsId = entry.getKey();
      var items = entry.getValue();
      var newHolding = holdingsMap.get(holdingsId);
      var oldHolding = oldHoldingsMap.get(holdingsId);

      if (newHolding != null) {
        var holdingsChanged = oldHolding == null || !equalsIgnoringMetadata(oldHolding, newHolding);
        if (holdingsChanged) {
          var itemsCopy = (List<Item>) deepCopy(items, Item.class);
          itemsBeforeUpdate.put(holdingsId, itemsCopy);
          for (var item : items) {
            itemService.populateItemFromHoldings(item, newHolding, effectiveValuesService);
            allItemsToUpdate.add(item);
          }
        }
      }
    }
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
