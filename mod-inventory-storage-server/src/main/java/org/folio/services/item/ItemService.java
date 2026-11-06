package org.folio.services.item;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.logging.log4j.LogManager.getLogger;
import static org.folio.rest.impl.StorageHelper.MAX_ENTITIES;
import static org.folio.rest.tools.utils.TenantTool.tenantId;
import static org.folio.services.batch.BatchOperationContextFactory.buildBatchOperationContext;
import static org.folio.services.item.ItemUtils.normalizeItemFields;
import static org.folio.utils.CollectionUtil.deepCopy;
import static org.folio.utils.ComparisonUtils.equalsIgnoringMetadata;
import static org.folio.validator.CommonValidators.validateUuidFormat;
import static org.folio.validator.CommonValidators.validateUuidFormatForList;
import static org.folio.validator.HridValidators.refuseWhenHridChanged;
import static org.folio.validator.NotesValidators.refuseLongNotes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Logger;
import org.folio.dbschema.ObjectMapperTool;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.persist.HoldingsRepository;
import org.folio.persist.ItemRepository;
import org.folio.persist.entity.DeletedRecord;
import org.folio.persist.entity.ItemWithHoldings;
import org.folio.rest.exceptions.BadRequestException;
import org.folio.rest.exceptions.NotFoundException;
import org.folio.rest.jaxrs.model.CirculationNote;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.ItemPatchRequest;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.jaxrs.model.ReindexRecordsRequest;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PostgresClientStreamResult;
import org.folio.services.CqlQuery;
import org.folio.services.HridManager;
import org.folio.services.ItemEffectiveValuesService;
import org.folio.services.batch.BatchPersistence;
import org.folio.services.domainevent.ItemDomainEventPublisher;
import org.folio.services.reindex.ReindexExportOrchestrator;
import org.folio.services.sanitizer.Sanitizer;
import org.folio.services.setting.SettingsService;
import org.folio.validator.CommonValidators;
import org.folio.validator.NotesValidators;
import org.springframework.stereotype.Service;

@Service
public class ItemService {

  private static final Logger log = getLogger(ItemService.class);

  private static final ObjectMapper OBJECT_MAPPER = ObjectMapperTool.getMapper();
  private static final Pattern KEY_ALREADY_EXISTS_PATTERN = Pattern.compile(
    ": Key \\(([^=]+)\\)=\\((.*)\\) already exists.$");
  private static final Pattern KEY_NOT_PRESENT_PATTERN = Pattern.compile(
    ": Key \\(([^=]+)\\)=\\((.*)\\) is not present in table \"(.*)\".$");
  private static final String INSTANCE_ID_WITH_ITEM_JSON = """
    {"instanceId": "%s",%s
    """;
  private static final List<String> READ_ONLY_FIELDS = List.of(
    "permanentLocation", "temporaryLocation", "effectiveLocationId", "effectiveShelvingOrder",
    "effectiveCallNumberComponents", "holdingsRecord2", "metadata", "materialType");
  private static final int BATCH_SIZE = 100;

  private final ItemEffectiveValuesService effectiveValuesService;
  private final Context vertxContext;
  private final SettingsService settingsService;
  private final Sanitizer<Item> sanitizer;
  private final ItemDomainEventPublisher itemDomainEventPublisher;
  private final ReindexExportOrchestrator reindexExportOrchestrator;
  private final HoldingsRepository holdingsRepository;
  private final ItemRepository itemRepository;
  private final HridManager hridManager;

  public ItemService(Context vertxContext, ItemEffectiveValuesService effectiveValuesService,
                     SettingsService settingsService,
                     ItemDomainEventPublisher itemDomainEventPublisher,
                     ReindexExportOrchestrator reindexExportOrchestrator,
                     HoldingsRepository holdingsRepository,
                     ItemRepository itemRepository,
                     HridManager hridManager,
                     Sanitizer<Item> sanitizer) {
    this.hridManager = hridManager;
    this.itemRepository = itemRepository;
    this.holdingsRepository = holdingsRepository;
    this.reindexExportOrchestrator = reindexExportOrchestrator;
    this.itemDomainEventPublisher = itemDomainEventPublisher;
    this.vertxContext = vertxContext;
    this.effectiveValuesService = effectiveValuesService;
    this.settingsService = settingsService;
    this.sanitizer = sanitizer;
  }

  public Future<Item> createItem(Item entity, Map<String, String> headers) {
    entity.getStatus().setDate(new Date());
    sanitizer.sanitize(entity);

    return validateUuidFormat(entity.getStatisticalCodeIds())
      .compose(v -> hridManager.populateHrid(entity, tenantId(headers)))
      .compose(NotesValidators::refuseLongNotes)
      .compose(item -> effectiveValuesService.populateEffectiveValues(item, headers))
      .compose(this::populateCirculationNoteId)
      .compose(item -> itemRepository.create(item, tenantId(headers)))
      .onSuccess(created -> itemDomainEventPublisher.publishRecordCreated(created, headers));
  }

  public Future<Void> createItems(List<Item> items, boolean upsert, boolean optimisticLocking,
                                      Map<String, String> headers) {
    final var itemStatusDate = new Date();
    for (Item item : items) {
      if (item.getStatus().getDate() == null) {
        item.getStatus().setDate(itemStatusDate);
      }
      sanitizer.sanitize(item);
    }
    ensureItemsHaveIds(items);

    return validateUuidFormatForList(items, Item::getStatisticalCodeIds)
      .compose(v -> hridManager.populateHridForItems(items, tenantId(headers)))
      .compose(NotesValidators::refuseItemLongNotes)
      .compose(result -> effectiveValuesService.populateEffectiveValues(items, headers))
      .compose(this::populateCirculationNoteId)
      .compose(result -> buildBatchOperationContext(upsert, items,
        itemRepository, Item::getId, true, headers))
      .compose(batchOperation -> BatchPersistence.save(itemRepository, items, MAX_ENTITIES, upsert,
          optimisticLocking, headers)
        .onSuccess(notUsed -> itemDomainEventPublisher.publishRecordsCreatedOrUpdated(batchOperation, headers)));
  }

  public Future<Void> updateBatch(Conn conn, List<Item> allItemsToUpdate) {
    allItemsToUpdate.forEach(sanitizer::sanitize);
    Future<Void> future = Future.succeededFuture();
    for (List<Item> batch : ListUtils.partition(allItemsToUpdate, BATCH_SIZE)) {
      future = future.compose(prev -> itemRepository.updateBatch(batch, conn));
    }
    return future;
  }

  public Future<Void> updateItems(List<ItemPatchRequest> items, Map<String, String> headers) {
    if (CollectionUtils.isEmpty(items)) {
      return Future.failedFuture(new BadRequestException("Expected at least one item to update"));
    }

    Pair<List<PatchData>, List<Item>> conversionResult;
    try {
      conversionResult = convertItemPatchesToPatchData(items);
    } catch (IllegalArgumentException e) {
      log.error("updateItems:: Failed to convert items", e);
      return Future.failedFuture(new BadRequestException("Invalid item format: " + e.getMessage()));
    }

    var patchDataList = conversionResult.getLeft();
    var itemList = conversionResult.getRight();
    itemList.forEach(sanitizer::sanitize);

    return ItemUtils.validateRequiredFields(items)
      .compose(v -> validateUuidFormatForList(itemList, Item::getStatisticalCodeIds))
      .compose(v -> NotesValidators.refuseItemLongNotes(itemList))
      .compose(v -> populateCirculationNoteId(itemList))
      .compose(v -> validateMultiplePatchItemsAndHoldings(patchDataList, headers))
      .compose(this::filterUnchangedPatchData)
      .compose(patchDataToUpdate -> executeItemsUpdate(items, patchDataToUpdate, headers));
  }

  public Future<Void> updateItem(String itemId, Item newItem, Map<String, String> headers) {
    newItem.setId(itemId);
    sanitizer.sanitize(newItem);
    var putData = new PutData();
    return validateUuidFormat(newItem.getStatisticalCodeIds())
      .compose(v -> refuseLongNotes(newItem))
      .compose(this::populateCirculationNoteId)
      .compose(notUsed -> getItemAndHolding(itemId, newItem.getHoldingsRecordId(), headers))
      .onSuccess(putData::set)
      .compose(x -> refuseWhenHridChanged(putData.oldItem, newItem))
      .compose(x -> retrieveOldHoldingsIfNeeded(newItem, putData, headers))
      .compose(oldHoldings -> performItemUpdate(newItem, putData, oldHoldings, headers))
      .mapEmpty();
  }

  public Future<Item> getItem(String itemId, Map<String, String> headers) {
    return itemRepository.findById(itemId, tenantId(headers));
  }

  public Future<PostgresClientStreamResult<Item>> streamItems(String query, int offset, int limit,
                                                              Map<String, String> headers) {
    return itemRepository.stream(query, offset, limit, tenantId(headers));
  }

  public Future<Void> deleteItem(String itemId, Map<String, String> headers) {
    return itemRepository.getById(itemId, tenantId(headers))
      .compose(CommonValidators::refuseIfNotFound)
      .compose(item -> itemRepository.remove(itemId, tenantId(headers))
        .onSuccess(notUsed -> itemDomainEventPublisher.publishRecordRemoved(item, headers)));
  }

  public Future<Void> deleteItems(String cql, Map<String, String> headers) {
    if (isBlank(cql)) {
      return Future.failedFuture(new BadRequestException("Expected CQL but query parameter is empty"));
    }
    if (new CqlQuery(cql).isMatchingAll()) {
      return deleteAllItems(headers);  // faster: sends only one domain event (Kafka) message
    }
    // do not add curly braces for readability, this is to comply with
    // https://sonarcloud.io/organizations/folio-org/rules?open=java%3AS1602&rule_key=java%3AS1602
    return itemRepository.delete(cql, tenantId(headers))
      .onSuccess(deleted -> vertxContext.runOnContext(runLater ->
        deleted.forEach(x -> processDeletedItem(x, headers))))
      .mapEmpty();
  }

  /**
   * Deletes all items but sends only a single domain event (Kafka) message "all records removed",
   * this is much faster than sending one message for each deleted item.
   */
  public Future<Void> deleteAllItems(Map<String, String> headers) {
    return itemRepository.deleteAll(tenantId(headers))
      .onSuccess(notUsed -> itemDomainEventPublisher.publishAllRemoved(headers))
      .mapEmpty();
  }

  /**
   * Return items before update.
   */
  public Future<List<Item>> updateItemsOnHoldingChanged(Conn connection, HoldingsRecord holdingsRecord,
                                                        Map<String, String> headers) {

    return itemRepository.getItemsForHoldingRecord(connection, holdingsRecord.getId())
      .compose(items -> updateEffectiveCallNumbersAndLocation(connection,
        // have to make deep clone of the items because the items are stateful
        // so that domain events will have proper 'old' item state.
        deepCopy(items, Item.class), holdingsRecord, headers)
        .map(items));
  }

  public Future<Void> publishReindexItemRecords(String rangeId, String fromId, String toId,
                                                Map<String, String> headers) {
    return itemRepository.getReindexItemRecords(fromId, toId, tenantId(headers))
      .compose(items -> itemDomainEventPublisher.publishReindexItems(rangeId, items, headers));
  }

  public Future<Void> exportReindexItemRecords(ReindexRecordsRequest request, Map<String, String> headers) {
    var rangeFrom = request.getRecordIdsRange().getFrom();
    var rangeTo = request.getRecordIdsRange().getTo();
    return reindexExportOrchestrator
      .export(request, headers, itemRepository,
        conn -> itemRepository
          .streamReindexItemRecords(conn, rangeFrom, rangeTo, tenantId(headers)));
  }

  public void populateItemFromHoldings(Item item, HoldingsRecord holdingsRecord,
                                       ItemEffectiveValuesService effectiveValuesService,
                                       Map<String, String> headers) {
    effectiveValuesService.populateEffectiveValues(item, holdingsRecord);
    if (isItemFieldsAffected(holdingsRecord, item)) {
      populateMetadata(item, holdingsRecord.getMetadata(), headers);
    }
  }

  private Pair<List<PatchData>, List<Item>> convertItemPatchesToPatchData(List<ItemPatchRequest> items) {
    var itemList = new ArrayList<Item>(items.size());
    var patchDataList = items.stream()
      .map(itemPatch -> {
        removeReadOnlyFields(itemPatch);
        var patchData = new PatchData();
        patchData.setPatchRequest(itemPatch);
        patchData.setNewItem(OBJECT_MAPPER.convertValue(itemPatch, Item.class));
        itemList.add(patchData.getNewItem());
        return patchData;
      })
      .toList();
    return Pair.of(patchDataList, itemList);
  }

  private Future<Void> executeItemsUpdate(List<ItemPatchRequest> items, List<PatchData> patchDataToUpdate,
                                              Map<String, String> headers) {
    if (patchDataToUpdate.isEmpty()) {
      return Future.succeededFuture();
    }
    try {
      normalizeItemFields(items);
    } catch (Exception e) {
      log.warn("executeItemsUpdate:: Unable to normalize fields", e);
      return Future.failedFuture(new BadRequestException("Unable to normalize fields: " + e.getMessage()));
    }
    return itemRepository.inTransaction(tenantId(headers), conn ->
        itemRepository.updateItems(conn, items, tenantId(headers))
          .compose(updatedItems -> publishItemUpdateEvents(updatedItems, patchDataToUpdate, headers)))
      .recover(ItemUtils::handleUpdateItemsError);
  }

  private Future<Void> publishItemUpdateEvents(List<Item> updatedItems, List<PatchData> patchDataToUpdate,
                                                   Map<String, String> headers) {
    for (var updatedItem : updatedItems) {
      for (var patchData : patchDataToUpdate) {
        if (updatedItem.getId().equals(patchData.getPatchRequest().getId())) {
          itemDomainEventPublisher.publishUpdated(
            updatedItem, patchData.getOldItem(), patchData.getNewHoldings(), patchData.getOldHoldings(), headers);
          break;
        }
      }
    }
    return Future.succeededFuture();
  }

  private Future<HoldingsRecord> retrieveOldHoldingsIfNeeded(Item newItem, PutData putData,
                                                             Map<String, String> headers) {
    if (newItem.getHoldingsRecordId().equals(putData.oldItem.getHoldingsRecordId())) {
      return Future.succeededFuture(putData.newHoldings);
    }
    return holdingsRepository.getById(putData.oldItem.getHoldingsRecordId(), tenantId(headers));
  }

  private Future<Void> performItemUpdate(Item newItem, PutData putData, HoldingsRecord oldHoldings,
                                         Map<String, String> headers) {
    putData.oldHoldings = oldHoldings;
    effectiveValuesService.populateEffectiveValues(newItem, putData.newHoldings);
    return settingsService.isOptimizeUpdatesEnabled(headers)
      .compose(isOptimizeUpdatesEnabled -> {
        try {
          if (isOptimizeUpdatesEnabled.booleanValue() && equalsIgnoringMetadata(putData.oldItem, newItem)) {
            return Future.succeededFuture();
          } else {
            return doUpdateItem(newItem, headers)
              .onSuccess(finalItem -> itemDomainEventPublisher.publishUpdated(
                finalItem, putData.oldItem, putData.newHoldings, putData.oldHoldings, headers))
              .mapEmpty();
          }
        } catch (Exception e) {
          return Future.failedFuture(e);
        }
      });
  }

  private void processDeletedItem(DeletedRecord deleted, Map<String, String> headers) {
    try {
      var instanceIdAndItemRaw = INSTANCE_ID_WITH_ITEM_JSON.formatted(
        deleted.instanceId(), deleted.json().substring(1));
      var itemId = OBJECT_MAPPER.readTree(deleted.json()).get("id").textValue();

      itemDomainEventPublisher.publishRemoved(itemId, instanceIdAndItemRaw, headers);
    } catch (JsonProcessingException ex) {
      log.error("processDeletedItem:: Failed to parse json : {}", ex.getMessage(), ex);
      throw new IllegalArgumentException(ex.getCause());
    }
  }

  private static Throwable putFailure(Throwable e) {
    var msg = PgExceptionUtil.badRequestMessage(e);
    if (msg == null) {
      msg = e.getMessage();
    }
    if (PgExceptionUtil.isVersionConflict(e)) {
      return e;
    }
    var matcher = KEY_NOT_PRESENT_PATTERN.matcher(msg);
    if (matcher.find()) {
      var field = matcher.group(1);
      var value = matcher.group(2);
      var refTable = matcher.group(3);
      msg = "Cannot set item " + field + " = " + value
            + " because it does not exist in " + refTable + ".id.";
    } else {
      matcher = KEY_ALREADY_EXISTS_PATTERN.matcher(msg);
      if (matcher.find()) {
        msg = matcher.group(1) + " value already exists in table item: " + matcher.group(2);
      }
    }
    return new BadRequestException(msg);
  }

  private static boolean isItemFieldsAffected(HoldingsRecord holdingsRecord, Item item) {
    return isBlank(item.getItemLevelCallNumber()) && isNotBlank(holdingsRecord.getCallNumber())
           || isBlank(item.getItemLevelCallNumberPrefix()) && isNotBlank(holdingsRecord.getCallNumberPrefix())
           || isBlank(item.getItemLevelCallNumberSuffix()) && isNotBlank(holdingsRecord.getCallNumberSuffix())
           || isBlank(item.getItemLevelCallNumberTypeId()) && isNotBlank(holdingsRecord.getCallNumberTypeId())
           || isNull(item.getPermanentLocationId())
              && isNull(item.getTemporaryLocationId())
              && (!isNull(holdingsRecord.getTemporaryLocationId())
                  || !isNull(holdingsRecord.getPermanentLocationId()));
  }

  private Future<Void> updateEffectiveCallNumbersAndLocation(Conn connection, Collection<Item> items,
                                                             HoldingsRecord holdingsRecord,
                                                             Map<String, String> headers) {
    var preparedItems = items.stream()
      .map(item -> {
        populateItemFromHoldings(item, holdingsRecord, effectiveValuesService, headers);
        return item;
      })
      .toList();
    if (preparedItems.isEmpty()) {
      return Future.succeededFuture();
    }
    return updateBatch(connection, preparedItems);
  }

  private Future<PutData> getItemAndHolding(String itemId, String holdingsId, Map<String, String> headers) {
    return itemRepository.getItemAndHolding(itemId, holdingsId, tenantId(headers))
      .compose(found -> {
        if (found.isEmpty()) {
          return Future.failedFuture(new NotFoundException("Not found"));
        }
        if (found.get().holdings() == null) {
          return Future.failedFuture(new BadRequestException("holdingsRecordId not found: " + holdingsId));
        }
        var putData = new PutData();
        putData.oldItem = found.get().item();
        putData.newHoldings = found.get().holdings();
        return Future.succeededFuture(putData);
      });
  }

  private Future<Item> doUpdateItem(Item item, Map<String, String> headers) {
    if (Integer.valueOf(-1).equals(item.getVersion())) {
      item.setVersion(null);  // enforce optimistic locking
    }

    return itemRepository.updateReturning(item, tenantId(headers))
      .compose(updated -> updated.<Future<Item>>map(Future::succeededFuture)
        .orElseGet(() -> Future.failedFuture(new NotFoundException("Record not Found"))))
      .recover(e -> Future.failedFuture(e instanceof NotFoundException ? e : putFailure(e)));
  }

  private Future<List<PatchData>> validateMultiplePatchItemsAndHoldings(List<PatchData> patchDataList,
                                                                        Map<String, String> headers) {
    if (patchDataList.isEmpty()) {
      return Future.succeededFuture(List.of());
    }

    var itemIds = patchDataList.stream()
      .map(patchData -> patchData.getNewItem().getId())
      .toList();

    return itemRepository.getItemsWithCurrentHoldings(itemIds, tenantId(headers))
      .compose(itemsWithHoldings -> {
        populateOldItemsAndCurrentHoldings(itemsWithHoldings, patchDataList);
        return ensureHoldingsAreLoaded(patchDataList, headers)
          .compose(this::validateAndPopulatePatchData);
      });
  }

  private void populateOldItemsAndCurrentHoldings(List<ItemWithHoldings> itemsWithHoldings,
                                                  List<PatchData> patchDataList) {
    var holdingsCache = new HashMap<String, HoldingsRecord>();
    var foundItemIds = new ArrayList<String>();
    var patchDataMap = patchDataList.stream()
      .<HashMap<String, PatchData>>collect(HashMap::new, (map, patchData) ->
        map.put(patchData.getNewItem().getId(), patchData), HashMap::putAll);

    // Populate old items and current holdings from database results
    for (var itemWithHoldings : itemsWithHoldings) {
      processItemRow(itemWithHoldings, patchDataMap, holdingsCache, foundItemIds);
    }

    validateAllItemsFound(foundItemIds, patchDataList);
  }

  private void processItemRow(ItemWithHoldings itemWithHoldings, Map<String, PatchData> patchDataMap,
                              Map<String, HoldingsRecord> holdingsCache, List<String> foundItemIds) {
    var itemId = itemWithHoldings.item().getId();
    var currentHoldings = itemWithHoldings.holdings();
    var currentHoldingsId = itemWithHoldings.item().getHoldingsRecordId();

    var patchData = patchDataMap.get(itemId);
    if (patchData != null) {
      patchData.setOldItem(itemWithHoldings.item());

      // Cache current holdings if present
      if (currentHoldings != null) {
        holdingsCache.putIfAbsent(currentHoldingsId, currentHoldings);
        patchData.setOldHoldings(holdingsCache.get(currentHoldingsId));

        // Set new holdings if it's the same as current
        var newHoldingsId = patchData.getNewItem().getHoldingsRecordId();
        if (newHoldingsId == null || newHoldingsId.equals(currentHoldingsId)) {
          patchData.setNewHoldings(patchData.getOldHoldings());
        }
      }

      foundItemIds.add(itemId);
    }
  }

  private void validateAllItemsFound(List<String> foundItemIds, List<PatchData> patchDataList) {
    if (foundItemIds.size() != patchDataList.size()) {
      var requestedItemIds = patchDataList.stream()
        .map(patchData -> patchData.getNewItem().getId())
        .toList();
      var missingItemIds = requestedItemIds.stream()
        .filter(id -> !foundItemIds.contains(id))
        .toList();

      var errorMessage = missingItemIds.size() == 1
                         ? "Item not found in database: " + missingItemIds.getFirst()
                         : "Items not found in database: " + String.join(", ", missingItemIds);

      throw new NotFoundException(errorMessage);
    }
  }

  private Future<List<PatchData>> ensureHoldingsAreLoaded(List<PatchData> patchDataList,
                                                          Map<String, String> headers) {
    var missingHoldingsIds = patchDataList.stream()
      .filter(patchData -> patchData.getNewHoldings() == null)
      .map(patchData -> patchData.getNewItem().getHoldingsRecordId())
      .distinct()
      .toList();

    if (missingHoldingsIds.isEmpty()) {
      return Future.succeededFuture(patchDataList);
    }

    return holdingsRepository.getHoldingsByIds(missingHoldingsIds, tenantId(headers))
      .compose(holdings -> assignMissingHoldings(patchDataList, holdings));
  }

  private Future<List<PatchData>> assignMissingHoldings(List<PatchData> patchDataList,
                                                           List<HoldingsRecord> holdings) {
    var holdingsMap = new HashMap<String, HoldingsRecord>();
    holdings.forEach(holdingsRecord -> holdingsMap.put(holdingsRecord.getId(), holdingsRecord));

    // Set missing holdings
    for (var patchData : patchDataList) {
      if (patchData.getNewHoldings() == null) {
        var newHoldingsId = patchData.getNewItem().getHoldingsRecordId();
        patchData.setNewHoldings(holdingsMap.get(newHoldingsId));
        if (patchData.getNewHoldings() == null) {
          return Future.failedFuture(new NotFoundException("Holdings not found: " + newHoldingsId));
        }
      }
    }

    return Future.succeededFuture(patchDataList);
  }

  private Future<List<PatchData>> validateAndPopulatePatchData(List<PatchData> patchDataList) {
    var validationFutures = patchDataList.stream()
      .map(this::validateAndPopulateSinglePatchData)
      .toList();

    return Future.all(validationFutures)
      .map(CompositeFuture::list);
  }

  private Future<PatchData> validateAndPopulateSinglePatchData(PatchData patchData) {
    var newItem = patchData.getNewItem();
    var patchRequest = patchData.getPatchRequest();

    var hridPresentInPatch = patchRequest.getAdditionalProperties() != null
                             && patchRequest.getAdditionalProperties().containsKey("hrid");

    var validationFuture = hridPresentInPatch
                           ? refuseWhenHridChanged(patchData.getOldItem(), newItem).mapEmpty()
                           : Future.succeededFuture();

    return validationFuture.compose(v -> {
      effectiveValuesService.populateEffectiveValues(newItem, patchData);
      ItemUtils.transferEffectiveValuesToPatch(newItem, patchData.getPatchRequest());
      return Future.succeededFuture(patchData);
    });
  }

  private void populateMetadata(Item item, Metadata metadata, Map<String, String> headers) {
    var oldMetadata = item.getMetadata();
    var updatedMetadata = new Metadata()
      .withCreatedByUserId(oldMetadata.getCreatedByUserId())
      .withCreatedByUsername(oldMetadata.getCreatedByUsername())
      .withCreatedDate(oldMetadata.getCreatedDate())
      .withUpdatedByUsername(metadata.getUpdatedByUsername())
      .withUpdatedByUserId(headers.get(XOkapiHeaders.USER_ID))
      .withUpdatedDate(new Date());

    item.setMetadata(updatedMetadata);
  }

  private Future<Item> populateCirculationNoteId(Item item) {
    if (Objects.nonNull(item.getCirculationNotes())) {
      for (CirculationNote circulationNote : item.getCirculationNotes()) {
        if (Objects.isNull(circulationNote.getId())) {
          circulationNote.setId(UUID.randomUUID().toString());
        }
      }
    }
    return Future.succeededFuture(item);
  }

  private Future<List<Item>> populateCirculationNoteId(List<Item> items) {
    items.forEach(this::populateCirculationNoteId);
    return Future.succeededFuture(items);
  }

  private Future<List<PatchData>> filterUnchangedPatchData(List<PatchData> patchDataList) {
    return Future.succeededFuture(patchDataList.stream()
      .filter(patchData -> {
        try {
          return patchData.hasChanges();
        } catch (Exception e) {
          log.warn("Error checking patch changes for item {}, including in update: {}",
            patchData.getNewItem().getId(), e.getMessage());
          return true;
        }
      })
      .toList());
  }

  private void removeReadOnlyFields(ItemPatchRequest itemPatch) {
    var additionalProperties = itemPatch.getAdditionalProperties();
    if (additionalProperties != null) {
      READ_ONLY_FIELDS.forEach(additionalProperties::remove);
    }
  }

  private void ensureItemsHaveIds(List<Item> items) {
    items.forEach(item -> item.setId(item.getId() == null
                                     ? UUID.randomUUID().toString()
                                     : item.getId()));
  }

  private static final class PutData {
    private Item oldItem;
    private HoldingsRecord oldHoldings;
    private HoldingsRecord newHoldings;

    public void set(PutData other) {
      oldItem = other.oldItem;
      newHoldings = other.newHoldings;
    }
  }
}
