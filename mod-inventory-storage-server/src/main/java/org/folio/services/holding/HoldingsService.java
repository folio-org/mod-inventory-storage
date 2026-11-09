package org.folio.services.holding;

import static java.util.stream.Collectors.toMap;
import static org.apache.logging.log4j.LogManager.getLogger;
import static org.folio.rest.impl.StorageHelper.MAX_ENTITIES;
import static org.folio.rest.tools.utils.TenantTool.tenantId;
import static org.folio.services.batch.BatchOperationContextFactory.buildBatchOperationContext;
import static org.folio.utils.CollectionUtil.deepCopy;
import static org.folio.utils.ComparisonUtils.equalsIgnoringMetadata;
import static org.folio.validator.CommonValidators.validateUuidFormat;
import static org.folio.validator.CommonValidators.validateUuidFormatForList;
import static org.folio.validator.HoldingsValidators.refuseNullValueInRequiredFields;
import static org.folio.validator.HridValidators.refuseWhenHridChanged;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Logger;
import org.folio.dbschema.ObjectMapperTool;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.persist.HoldingsRepository;
import org.folio.persist.InstanceRepository;
import org.folio.persist.ItemRepository;
import org.folio.rest.exceptions.BadRequestException;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.HoldingsRecordView;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.PatchRequest;
import org.folio.rest.jaxrs.model.ReindexRecordsRequest;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PostgresClientStreamResult;
import org.folio.rest.tools.utils.MetadataUtil;
import org.folio.services.CqlQuery;
import org.folio.services.HridManager;
import org.folio.services.ItemEffectiveValuesService;
import org.folio.services.batch.BatchPersistence;
import org.folio.services.caches.ConsortiumData;
import org.folio.services.consortium.ConsortiumService;
import org.folio.services.consortium.entities.SharingInstance;
import org.folio.services.item.ItemService;
import org.folio.services.reindex.ReindexExportOrchestrator;
import org.folio.services.sanitizer.Sanitizer;
import org.folio.services.setting.SettingsService;
import org.folio.validator.CommonValidators;
import org.folio.validator.NotesValidators;
import org.springframework.stereotype.Service;

@Service
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
  private final ItemService itemService;
  private final ConsortiumService consortiumService;
  private final ItemEffectiveValuesService effectiveValuesService;
  private final SettingsService settingsService;
  private final Sanitizer<HoldingsRecord> sanitizer;
  private final HoldingsEventPublisher eventPublisher;
  private final ReindexExportOrchestrator reindexExportOrchestrator;
  private final HoldingsRepository holdingsRepository;
  private final InstanceRepository instanceRepository;
  private final ItemRepository itemRepository;
  private final HridManager hridManager;

  public HoldingsService(Context vertxContext, ItemService itemService, ConsortiumService consortiumService,
                         ItemEffectiveValuesService effectiveValuesService, SettingsService settingsService,
                         HoldingsEventPublisher eventPublisher,
                         ReindexExportOrchestrator reindexExportOrchestrator,
                         HoldingsRepository holdingsRepository,
                         InstanceRepository instanceRepository,
                         ItemRepository itemRepository,
                         HridManager hridManager,
                         Sanitizer<HoldingsRecord> sanitizer) {
    this.hridManager = hridManager;
    this.itemRepository = itemRepository;
    this.instanceRepository = instanceRepository;
    this.holdingsRepository = holdingsRepository;
    this.reindexExportOrchestrator = reindexExportOrchestrator;
    this.eventPublisher = eventPublisher;
    this.vertxContext = vertxContext;
    this.itemService = itemService;
    this.consortiumService = consortiumService;
    this.effectiveValuesService = effectiveValuesService;
    this.settingsService = settingsService;
    this.sanitizer = sanitizer;
  }

  /**
   * Returns the JSON of the holdings records if the query is supported by instanceId query, null otherwise.
   *
   * <p>
   */
  public Future<HoldingsRecord> getHoldings(String holdingsRecordId, Map<String, String> headers) {
    return holdingsRepository.findById(holdingsRecordId, tenantId(headers));
  }

  public Future<PostgresClientStreamResult<HoldingsRecordView>> streamHoldings(String query, int offset, int limit,
                                                                               Map<String, String> headers) {
    return holdingsRepository.stream(HoldingsRecordView.class, query, offset, limit, tenantId(headers));
  }

  public Future<String> getByInstanceId(int offset, int limit, String query, Map<String, String> headers) {
    if (query == null) {
      return Future.succeededFuture();
    }
    var matcher = INSTANCEID_PATTERN.matcher(query);
    if (!matcher.find()) {
      return Future.succeededFuture();
    }
    var instanceId = matcher.group(1);
    var sortBy = SPACE_REGEX.split(matcher.group(2));
    return holdingsRepository.getByInstanceId(instanceId, sortBy, offset, limit, tenantId(headers))
      .map(row -> {
        return "{ \"holdingsRecords\": " + row.getString("holdings") + ",\n"
               + "  \"totalRecords\": " + row.getLong("total_records") + ",\n"
               + "  \"resultInfo\": { \n"
               + "    \"totalRecords\": " + row.getLong("total_records") + ",\n"
               + "    \"totalRecordsEstimated\": false\n"
               + "  }\n"
               + "}";
      })
      .onFailure(e -> log.error("getByInstanceId:: {}", e.getMessage(), e));
  }

  /**
   * Deletes all holdings but sends only a single domain event (Kafka) message "all records removed",
   * this is much faster than sending one message for each deleted holding.
   */
  public Future<Void> deleteAllHoldings(Map<String, String> headers) {
    return holdingsRepository.deleteAll(tenantId(headers))
      .onSuccess(notUsed -> eventPublisher.publishAllRemoved(headers))
      .mapEmpty();
  }

  public Future<Void> updateHoldingRecord(String holdingId, HoldingsRecord holdingsRecord,
                                          Map<String, String> headers) {
    sanitizer.sanitize(holdingsRecord);
    return holdingsRepository.getById(holdingId, tenantId(headers))
      .compose(existingHoldingsRecord -> {
        if (holdingsRecordFound(existingHoldingsRecord)) {
          return updateHolding(existingHoldingsRecord, holdingsRecord, headers);
        } else {
          return createHolding(holdingsRecord, headers).mapEmpty();
        }
      });
  }

  public Future<Void> patchHoldingRecord(String holdingId, PatchRequest patchRequest,
                                         Map<String, String> headers) {
    String userId = headers.get(XOkapiHeaders.USER_ID);
    var patchJson = JsonObject.mapFrom(patchRequest);
    return holdingsRepository.getById(holdingId, tenantId(headers))
      .compose(CommonValidators::refuseIfNotFound)
      .compose(existingHoldingsRecord ->
        refuseNullValueInRequiredFields(patchJson)
          .compose(json -> applyPatch(existingHoldingsRecord, json, userId)
            .compose(newHoldingsRecord -> updateHolding(existingHoldingsRecord, newHoldingsRecord, headers))));
  }

  public Future<HoldingsRecord> createHolding(HoldingsRecord entity, Map<String, String> headers) {
    entity.setEffectiveLocationId(calculateEffectiveLocation(entity));
    sanitizer.sanitize(entity);

    return validateUuidFormat(entity.getStatisticalCodeIds())
      .compose(v -> createShadowInstancesIfNeeded(List.of(entity), headers))
      .compose(v -> hridManager.populateHrid(entity, tenantId(headers)))
      .compose(NotesValidators::refuseLongNotes)
      .compose(hr -> holdingsRepository.create(hr, tenantId(headers)))
      .onSuccess(created -> eventPublisher.publishRecordCreated(created, headers));
  }

  public Future<Void> deleteHolding(String hrId, Map<String, String> headers) {
    return holdingsRepository.getById(hrId, tenantId(headers))
      .compose(CommonValidators::refuseIfNotFound)
      .compose(hr -> holdingsRepository.remove(hrId, tenantId(headers))
        .onSuccess(notUsed -> eventPublisher.publishRecordRemoved(hr, headers)));
  }

  public Future<Void> deleteHoldings(String cql, Map<String, String> headers) {
    if (StringUtils.isBlank(cql)) {
      return Future.failedFuture(new BadRequestException("Expected CQL but query parameter is empty"));
    }
    if (new CqlQuery(cql).isMatchingAll()) {
      return deleteAllHoldings(headers);  // faster: sends only one domain event (Kafka) message
    }
    // do not add curly braces for readability, this is to comply with
    // https://sonarcloud.io/organizations/folio-org/rules?open=java%3AS1602&rule_key=java%3AS1602
    return holdingsRepository.delete(cql, tenantId(headers))
      .onSuccess(deleted -> vertxContext.runOnContext(runLater ->
        deleted.forEach(deletedRecord -> {
            try {
              var holdingId = OBJECT_MAPPER.readTree(deletedRecord.json()).get("id").textValue();
              eventPublisher.publishRemoved(holdingId, deletedRecord.json(), headers);
            } catch (JsonProcessingException ex) {
              log.error("deleteHoldings:: Failed to parse json : {}", ex.getMessage(), ex);
              throw new IllegalArgumentException(ex.getCause());
            }
          }
        )
      ))
      .mapEmpty();
  }

  public Future<Void> createHoldings(List<HoldingsRecord> holdings, boolean upsert, boolean optimisticLocking,
                                     Map<String, String> headers) {
    for (var holdingsRecord : holdings) {
      holdingsRecord.setEffectiveLocationId(calculateEffectiveLocation(holdingsRecord));
      sanitizer.sanitize(holdingsRecord);
    }

    return validateUuidFormatForList(holdings, HoldingsRecord::getStatisticalCodeIds)
      .compose(v -> createShadowInstancesIfNeeded(holdings, headers))
      .compose(ar -> hridManager.populateHridForHoldings(holdings, tenantId(headers))
        .compose(NotesValidators::refuseHoldingLongNotes))
      .compose(validatedHoldings -> {
        if (!upsert) {
          // For create-only operations, use existing logic
          return buildBatchOperationContext(false, validatedHoldings,
            holdingsRepository, HoldingsRecord::getId, true, headers)
            .compose(batchOperation -> BatchPersistence.save(holdingsRepository, validatedHoldings,
                MAX_ENTITIES, false, optimisticLocking, headers)
              .onSuccess(notUsed -> eventPublisher.publishCreatedOrUpdated(batchOperation, headers)));
        }

        // For upsert operations, use the new transaction-based approach
        return performUpsertWithItemUpdates(validatedHoldings, optimisticLocking, headers);
      });
  }

  public Future<Void> publishReindexHoldingsRecords(String rangeId, String fromId, String toId,
                                                    Map<String, String> headers) {
    return holdingsRepository.getReindexHoldingsRecords(fromId, toId, tenantId(headers))
      .compose(holdings -> eventPublisher
        .publishReindexHoldings(rangeId, holdings, headers));
  }

  public Future<Void> exportReindexHoldingsRecords(ReindexRecordsRequest request, Map<String, String> headers) {
    var rangeFrom = request.getRecordIdsRange().getFrom();
    var rangeTo = request.getRecordIdsRange().getTo();
    return reindexExportOrchestrator
      .export(request, headers, holdingsRepository,
        conn -> holdingsRepository
          .streamReindexHoldingsRecords(conn, rangeFrom, rangeTo, tenantId(headers)));
  }

  private Future<HoldingsRecord> applyPatch(HoldingsRecord holdingsRecord, JsonObject patchJson, String userId) {
    var metadata = holdingsRecord.getMetadata();
    if (metadata != null) {
      metadata.setUpdatedDate(new Date());
      metadata.setUpdatedByUserId(userId);
    }
    var holdingsRecordJson = JsonObject.mapFrom(holdingsRecord);
    var patchedJson = holdingsRecordJson.mergeIn(patchJson);
    try {
      return Future.succeededFuture(patchedJson.mapTo(HoldingsRecord.class));
    } catch (Exception e) {
      return Future.failedFuture(e);
    }
  }

  private Future<Void> updateHolding(HoldingsRecord oldHoldings, HoldingsRecord newHoldings,
                                     Map<String, String> headers) {
    newHoldings.setEffectiveLocationId(calculateEffectiveLocation(newHoldings));

    return createShadowInstancesIfNeeded(List.of(newHoldings), headers)
      .compose(v -> checkAndPerformUpdate(oldHoldings, newHoldings, headers));
  }

  private Future<Void> checkAndPerformUpdate(HoldingsRecord oldHoldings, HoldingsRecord newHoldings,
                                             Map<String, String> headers) {
    return settingsService.isOptimizeUpdatesEnabled(headers)
      .compose(isOptimizeUpdatesEnabled -> {
        try {
          if (Boolean.TRUE.equals(isOptimizeUpdatesEnabled) && equalsIgnoringMetadata(oldHoldings, newHoldings)) {
            return Future.succeededFuture();
          }
        } catch (Exception e) {
          return Future.failedFuture(e);
        }

        if (Integer.valueOf(-1).equals(newHoldings.getVersion())) {
          newHoldings.setVersion(null);  // enforce optimistic locking
        }

        return refuseWhenHridChanged(oldHoldings, newHoldings)
          .compose(notUsed -> NotesValidators.refuseLongNotes(newHoldings))
          .compose(notUsed -> performHoldingsUpdate(oldHoldings, newHoldings, headers));
      });
  }

  private Future<Void> performHoldingsUpdate(HoldingsRecord oldHoldings, HoldingsRecord newHoldings,
                                             Map<String, String> headers) {
    if (shouldUpdateItems(oldHoldings, newHoldings)) {
      return holdingsRepository.inTransaction(tenantId(headers), conn -> holdingsRepository
          .update(oldHoldings.getId(), newHoldings, conn)
          .compose(updateRes -> itemService.updateItemsOnHoldingChanged(conn, newHoldings, headers)))
        .onSuccess(itemsBeforeUpdate ->
          eventPublisher
            .publishUpdatedItems(oldHoldings, newHoldings, itemsBeforeUpdate, headers))
        .onSuccess(notUsed -> eventPublisher.publishRecordUpdated(oldHoldings, headers))
        .mapEmpty();
    }
    return holdingsRepository.inTransaction(tenantId(headers), conn -> holdingsRepository
        .update(oldHoldings.getId(), newHoldings, conn))
      .onSuccess(notUsed -> eventPublisher.publishRecordUpdated(oldHoldings, headers))
      .mapEmpty();
  }

  private boolean shouldUpdateItems(HoldingsRecord oldHoldings, HoldingsRecord newHoldings) {
    return oldHoldings == null
           || !Objects.equals(oldHoldings.getInstanceId(), newHoldings.getInstanceId())
           || !Objects.equals(oldHoldings.getPermanentLocationId(), newHoldings.getPermanentLocationId())
           || !Objects.equals(oldHoldings.getTemporaryLocationId(), newHoldings.getTemporaryLocationId())
           || !Objects.equals(oldHoldings.getCallNumber(), newHoldings.getCallNumber())
           || !Objects.equals(oldHoldings.getCallNumberPrefix(), newHoldings.getCallNumberPrefix())
           || !Objects.equals(oldHoldings.getCallNumberSuffix(), newHoldings.getCallNumberSuffix())
           || !Objects.equals(oldHoldings.getCallNumberTypeId(), newHoldings.getCallNumberTypeId());
  }

  private Future<Void> performUpsertWithItemUpdates(List<HoldingsRecord> holdings, boolean optimisticLocking,
                                                    Map<String, String> headers) {
    if (holdings == null || holdings.isEmpty()) {
      return Future.succeededFuture();
    }
    try {
      holdingsRepository.applyOptimisticLocking(holdings, optimisticLocking);
    } catch (RuntimeException e) {
      return Future.failedFuture(e);
    }

    try {
      MetadataUtil.populateMetadata(holdings, headers);
    } catch (ReflectiveOperationException e) {
      log.warn("performUpsertWithItemUpdates:: Error during metadata population", e);
      return Future.failedFuture(e.getMessage());
    }

    ensureHoldingsHaveIds(holdings);

    return holdingsRepository.inTransaction(tenantId(headers), conn ->
        upsertHoldingsAndGetOldContent(conn, holdings, headers)
          .compose(upsertResult -> updateItemsForHoldingsChange(conn, holdings, upsertResult, headers)
            .map(itemsBeforeUpdate -> Pair.of(upsertResult.getLeft(), itemsBeforeUpdate))))
      .onSuccess(oldData ->
        eventPublisher
          .publishHoldingsAndItemEvents(holdings, oldData.getLeft(), oldData.getRight(), headers))
      .mapEmpty();
  }

  private void ensureHoldingsHaveIds(List<HoldingsRecord> holdings) {
    holdings.forEach(holding -> holding.setId(holding.getId() == null
                                              ? UUID.randomUUID().toString()
                                              : holding.getId()));
  }

  private Future<Pair<Map<String, HoldingsRecord>, Map<String, List<Item>>>> upsertHoldingsAndGetOldContent(
    Conn conn, List<HoldingsRecord> holdings, Map<String, String> headers) {
    if (holdings.isEmpty()) {
      return Future.succeededFuture(Pair.of(Map.of(), Map.of()));
    }

    var sqlAndParamsResult = new HoldingsUpsertSqlBuilder(holdingsRepository, itemRepository)
      .buildUpsertSqlWithParams(holdings, headers);
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
    Pair<Map<String, HoldingsRecord>, Map<String, List<Item>>> upsertResult,
    Map<String, String> headers) {

    var oldHoldingsMap = upsertResult.getLeft();
    var oldItemsMap = upsertResult.getRight();

    var holdingsMap = newHoldings.stream()
      .collect(toMap(HoldingsRecord::getId, h -> h));

    var itemsBeforeUpdate = new HashMap<String, List<Item>>();
    var allItemsToUpdate = new ArrayList<Item>();

    try {
      processItemsForHoldingsUpdate(oldItemsMap, holdingsMap, oldHoldingsMap,
        itemsBeforeUpdate, allItemsToUpdate, headers);
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
                                             List<Item> allItemsToUpdate,
                                             Map<String, String> headers) throws JsonProcessingException {
    for (var entry : oldItemsMap.entrySet()) {
      var holdingsId = entry.getKey();
      var items = entry.getValue();
      var newHolding = holdingsMap.get(holdingsId);
      var oldHolding = oldHoldingsMap.get(holdingsId);

      if (newHolding != null) {
        var holdingsChanged = oldHolding == null || !equalsIgnoringMetadata(oldHolding, newHolding);
        if (holdingsChanged && shouldUpdateItems(oldHolding, newHolding)) {
          var itemsCopy = (List<Item>) deepCopy(items, Item.class);
          itemsBeforeUpdate.put(holdingsId, itemsCopy);
          for (var item : items) {
            itemService.populateItemFromHoldings(item, newHolding, effectiveValuesService, headers);
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

  private Future<Void> createShadowInstancesIfNeeded(List<HoldingsRecord> holdingsRecords,
                                                     Map<String, String> headers) {
    return consortiumService.getConsortiumData(headers)
      .compose(consortiumDataOptional -> {
        if (consortiumDataOptional.isPresent()) {
          return createShadowInstancesIfNeeded(holdingsRecords, consortiumDataOptional.get(), headers);
        }
        return Future.succeededFuture();
      });
  }

  private Future<Void> createShadowInstancesIfNeeded(List<HoldingsRecord> holdingsRecords,
                                                     ConsortiumData consortiumData, Map<String, String> headers) {
    Map<String, Future<SharingInstance>> instanceFuturesMap = new HashMap<>();

    for (HoldingsRecord holdingRecord : holdingsRecords) {
      String instanceId = holdingRecord.getInstanceId();
      instanceFuturesMap.computeIfAbsent(instanceId,
        v -> createShadowInstanceIfNeeded(instanceId, consortiumData, headers));
    }
    return Future.all(new ArrayList<>(instanceFuturesMap.values()))
      .mapEmpty();
  }

  private Future<SharingInstance> createShadowInstanceIfNeeded(String instanceId, ConsortiumData consortiumData,
                                                               Map<String, String> headers) {
    return instanceRepository.exists(instanceId, tenantId(headers))
      .compose(exists -> Boolean.TRUE.equals(exists) ? Future.succeededFuture() :
                         consortiumService.createShadowInstance(instanceId, consortiumData, headers)
      );
  }

  private boolean holdingsRecordFound(HoldingsRecord holdingsRecord) {
    return holdingsRecord != null;
  }
}
