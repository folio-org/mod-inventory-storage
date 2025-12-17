package org.folio.services.item;

import static io.vertx.core.Future.succeededFuture;
import static io.vertx.core.Promise.promise;
import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.logging.log4j.LogManager.getLogger;
import static org.folio.dbschema.ObjectMapperTool.readValue;
import static org.folio.rest.impl.HoldingsStorageApi.HOLDINGS_RECORD_TABLE;
import static org.folio.rest.impl.ItemStorageApi.ITEM_TABLE;
import static org.folio.rest.impl.StorageHelper.MAX_ENTITIES;
import static org.folio.rest.jaxrs.resource.ItemStorage.DeleteItemStorageItemsByItemIdResponse;
import static org.folio.rest.jaxrs.resource.ItemStorage.DeleteItemStorageItemsResponse;
import static org.folio.rest.jaxrs.resource.ItemStorage.PostItemStorageItemsResponse;
import static org.folio.rest.jaxrs.resource.ItemStorage.PutItemStorageItemsByItemIdResponse;
import static org.folio.rest.jaxrs.resource.ItemStorageBatchSynchronous.PostItemStorageBatchSynchronousResponse;
import static org.folio.rest.persist.PgUtil.deleteById;
import static org.folio.rest.persist.PgUtil.post;
import static org.folio.rest.persist.PgUtil.postSync;
import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.rest.persist.PostgresClient.pojo2JsonObject;
import static org.folio.rest.support.CollectionUtil.deepCopy;
import static org.folio.services.batch.BatchOperationContextFactory.buildBatchOperationContext;
import static org.folio.services.item.ItemUtils.normalizeItemFields;
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
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import javax.ws.rs.core.Response;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Logger;
import org.folio.dbschema.ObjectMapperTool;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.persist.HoldingsRepository;
import org.folio.persist.ItemRepository;
import org.folio.rest.exceptions.NotFoundException;
import org.folio.rest.jaxrs.model.CirculationNote;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.ItemPatchRequest;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.jaxrs.resource.ItemStorage;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.CqlQuery;
import org.folio.rest.support.HridManager;
import org.folio.rest.tools.client.exceptions.ResponseException;
import org.folio.services.ItemEffectiveValuesService;
import org.folio.services.ResponseHandlerUtil;
import org.folio.services.domainevent.ItemDomainEventPublisher;
import org.folio.services.sanitizer.Sanitizer;
import org.folio.services.sanitizer.SanitizerFactory;
import org.folio.validator.CommonValidators;
import org.folio.validator.NotesValidators;

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

  private final HridManager hridManager;
  private final ItemEffectiveValuesService effectiveValuesService;
  private final Context vertxContext;
  private final Map<String, String> okapiHeaders;
  private final ItemDomainEventPublisher domainEventService;
  private final ItemRepository itemRepository;
  private final PostgresClient postgresClient;
  private final HoldingsRepository holdingsRepository;
  private final Sanitizer<Item> sanitizer;

  public ItemService(Context vertxContext, Map<String, String> okapiHeaders) {
    this.vertxContext = vertxContext;
    this.okapiHeaders = okapiHeaders;
    this.postgresClient = postgresClient(vertxContext, okapiHeaders);
    this.hridManager = new HridManager(postgresClient);
    this.effectiveValuesService = new ItemEffectiveValuesService(vertxContext, okapiHeaders);
    this.domainEventService = new ItemDomainEventPublisher(vertxContext, okapiHeaders);
    this.itemRepository = new ItemRepository(vertxContext, okapiHeaders);
    this.holdingsRepository = new HoldingsRepository(vertxContext, okapiHeaders);
    this.sanitizer = SanitizerFactory.getSanitizer(Item.class);
  }

  public Future<Response> createItem(Item entity) {
    entity.getStatus().setDate(new Date());
    sanitizer.sanitize(entity);

    return validateUuidFormat(entity.getStatisticalCodeIds())
      .compose(v -> hridManager.populateHrid(entity))
      .compose(NotesValidators::refuseLongNotes)
      .compose(effectiveValuesService::populateEffectiveValues)
      .compose(this::populateCirculationNoteId)
      .compose(item -> post(ITEM_TABLE, item, okapiHeaders, vertxContext, PostItemStorageItemsResponse.class)
        .onSuccess(domainEventService.publishCreated()))
      .map(ResponseHandlerUtil::handleHridError);
  }

  public Future<Response> createItems(List<Item> items, boolean upsert, boolean optimisticLocking) {
    final var itemStatusDate = new Date();
    for (Item item : items) {
      if (item.getStatus().getDate() == null) {
        item.getStatus().setDate(itemStatusDate);
      }
      sanitizer.sanitize(item);
    }
    ensureItemsHaveIds(items);

    return validateUuidFormatForList(items, Item::getStatisticalCodeIds)
      .compose(v -> hridManager.populateHridForItems(items))
      .compose(NotesValidators::refuseItemLongNotes)
      .compose(result -> effectiveValuesService.populateEffectiveValues(items))
      .compose(this::populateCirculationNoteId)
      .compose(result -> buildBatchOperationContext(upsert, items, itemRepository, Item::getId, true))
      .compose(batchOperation -> postSync(ITEM_TABLE, items, MAX_ENTITIES, upsert, optimisticLocking,
        okapiHeaders, vertxContext, PostItemStorageBatchSynchronousResponse.class)
        .onSuccess(domainEventService.publishCreatedOrUpdated(batchOperation)))
      .map(ResponseHandlerUtil::handleHridError);
  }

  public Future<RowSet<Row>> updateBatch(Conn conn, List<Item> allItemsToUpdate) {
    allItemsToUpdate.forEach(sanitizer::sanitize);
    return itemRepository.updateBatch(allItemsToUpdate, conn);
  }

  public Future<Response> updateItems(List<ItemPatchRequest> items) {
    if (CollectionUtils.isEmpty(items)) {
      return Future.succeededFuture(
        ItemStorage.PatchItemStorageItemsResponse.respond400WithTextPlain("Expected at least one item to update"));
    }

    Pair<List<PatchData>, List<Item>> conversionResult;
    try {
      conversionResult = convertItemPatchesToPatchData(items);
    } catch (IllegalArgumentException e) {
      log.error("updateItems:: Failed to convert items", e);
      return Future.succeededFuture(ItemStorage.PatchItemStorageItemsResponse.respond400WithTextPlain(
        "Invalid item format: " + e.getMessage()));
    }

    var patchDataList = conversionResult.getLeft();
    var itemList = conversionResult.getRight();
    itemList.forEach(sanitizer::sanitize);

    return ItemUtils.validateRequiredFields(items)
      .compose(v -> validateUuidFormatForList(itemList, Item::getStatisticalCodeIds))
      .compose(v -> NotesValidators.refuseItemLongNotes(itemList))
      .compose(v -> populateCirculationNoteId(itemList))
      .compose(v -> validateMultiplePatchItemsAndHoldings(patchDataList))
      .compose(this::filterUnchangedPatchData)
      .compose(patchDataToUpdate -> executeItemsUpdate(items, patchDataToUpdate));
  }

  public Future<Response> updateItem(String itemId, Item newItem) {
    newItem.setId(itemId);
    sanitizer.sanitize(newItem);
    var putData = new PutData();
    return validateUuidFormat(newItem.getStatisticalCodeIds())
      .compose(v -> refuseLongNotes(newItem))
      .compose(this::populateCirculationNoteId)
      .compose(notUsed -> getItemAndHolding(itemId, newItem.getHoldingsRecordId()))
      .onSuccess(putData::set)
      .compose(x -> refuseWhenHridChanged(putData.oldItem, newItem))
      .compose(x -> retrieveOldHoldingsIfNeeded(newItem, putData))
      .compose(oldHoldings -> performItemUpdate(newItem, putData, oldHoldings))
      .map(x -> PutItemStorageItemsByItemIdResponse.respond204());
  }

  public Future<Response> deleteItem(String itemId) {
    return itemRepository.getById(itemId)
      .compose(CommonValidators::refuseIfNotFound)
      .compose(item -> deleteById(ITEM_TABLE, itemId, okapiHeaders, vertxContext,
        DeleteItemStorageItemsByItemIdResponse.class)
        .onSuccess(domainEventService.publishRemoved(item)));
  }

  public Future<Response> deleteItems(String cql) {
    if (isBlank(cql)) {
      return Future.succeededFuture(
        DeleteItemStorageItemsResponse.respond400WithTextPlain(
          "Expected CQL but query parameter is empty"));
    }
    if (new CqlQuery(cql).isMatchingAll()) {
      return deleteAllItems();  // faster: sends only one domain event (Kafka) message
    }
    // do not add curly braces for readability, this is to comply with
    // https://sonarcloud.io/organizations/folio-org/rules?open=java%3AS1602&rule_key=java%3AS1602
    return itemRepository.delete(cql)
      .onSuccess(rowSet -> vertxContext.runOnContext(runLater ->
        rowSet.iterator().forEachRemaining(this::processDeletedItemRow)))
      .map(Response.noContent().build());
  }

  /**
   * Deletes all items but sends only a single domain event (Kafka) message "all records removed",
   * this is much faster than sending one message for each deleted item.
   */
  public Future<Response> deleteAllItems() {
    return itemRepository.deleteAll()
      .onSuccess(notUsed -> domainEventService.publishAllRemoved())
      .map(Response.noContent().build());
  }

  /**
   * Return items before update.
   */
  public Future<List<Item>> updateItemsOnHoldingChanged(Conn connection,
                                                        HoldingsRecord holdingsRecord) {

    return itemRepository.getItemsForHoldingRecord(connection, holdingsRecord.getId())
      .compose(items -> updateEffectiveCallNumbersAndLocation(connection,
        // have to make deep clone of the items because the items are stateful
        // so that domain events will have proper 'old' item state.
        deepCopy(items, Item.class), holdingsRecord)
        .map(items));
  }

  public Future<Void> publishReindexItemRecords(String rangeId, String fromId, String toId) {
    return itemRepository.getReindexItemRecords(fromId, toId)
      .compose(items -> domainEventService.publishReindexItems(rangeId, items));
  }

  public void populateItemFromHoldings(Item item, HoldingsRecord holdingsRecord,
                                       org.folio.services.ItemEffectiveValuesService effectiveValuesService) {
    effectiveValuesService.populateEffectiveValues(item, holdingsRecord);
    if (isItemFieldsAffected(holdingsRecord, item)) {
      populateMetadata(item, holdingsRecord.getMetadata());
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

  private Future<Response> executeItemsUpdate(List<ItemPatchRequest> items, List<PatchData> patchDataToUpdate) {
    if (patchDataToUpdate.isEmpty()) {
      return Future.succeededFuture(ItemStorage.PatchItemStorageItemsResponse.respond204());
    }
    try {
      normalizeItemFields(items);
    } catch (Exception e) {
      log.warn("executeItemsUpdate:: Unable to normalize fields", e);
      return Future.succeededFuture(ItemStorage.PatchItemStorageItemsResponse.respond400WithTextPlain(
        "Unable to normalize fields: " + e.getMessage()));
    }
    return postgresClient.withTransaction(conn ->
        itemRepository.updateItems(conn, items)
          .<Response>compose(updatedItems -> publishItemUpdateEvents(updatedItems, patchDataToUpdate)))
      .recover(ItemUtils::handleUpdateItemsError);
  }

  private Future<Response> publishItemUpdateEvents(List<Item> updatedItems, List<PatchData> patchDataToUpdate) {
    for (var updatedItem : updatedItems) {
      for (var patchData : patchDataToUpdate) {
        if (updatedItem.getId().equals(patchData.getPatchRequest().getId())) {
          domainEventService.publishUpdated(
            updatedItem, patchData.getOldItem(), patchData.getNewHoldings(), patchData.getOldHoldings());
          break;
        }
      }
    }
    return Future.succeededFuture(ItemStorage.PatchItemStorageItemsResponse.respond204());
  }

  private Future<HoldingsRecord> retrieveOldHoldingsIfNeeded(Item newItem, PutData putData) {
    if (newItem.getHoldingsRecordId().equals(putData.oldItem.getHoldingsRecordId())) {
      return Future.succeededFuture(putData.newHoldings);
    }
    return holdingsRepository.getById(putData.oldItem.getHoldingsRecordId());
  }

  private Future<Void> performItemUpdate(Item newItem, PutData putData, HoldingsRecord oldHoldings) {
    putData.oldHoldings = oldHoldings;
    effectiveValuesService.populateEffectiveValues(newItem, putData.newHoldings);
    try {
      var noChanges = equalsIgnoringMetadata(putData.oldItem, newItem);
      if (noChanges) {
        return Future.succeededFuture();
      } else {
        return doUpdateItem(newItem)
          .onSuccess(finalItem -> domainEventService.publishUpdated(
            finalItem, putData.oldItem, putData.newHoldings, putData.oldHoldings))
          .mapEmpty();
      }
    } catch (Exception e) {
      return Future.failedFuture(e);
    }
  }

  private void processDeletedItemRow(Row row) {
    try {
      var instanceIdAndItemRaw = INSTANCE_ID_WITH_ITEM_JSON.formatted(
        row.getString(0), row.getString(1).substring(1));
      var itemId = OBJECT_MAPPER.readTree(row.getString(1)).get("id").textValue();

      domainEventService.publishRemoved(itemId, instanceIdAndItemRaw);
    } catch (JsonProcessingException ex) {
      log.error("processDeletedItemRow:: Failed to parse json : {}", ex.getMessage(), ex);
      throw new IllegalArgumentException(ex.getCause());
    }
  }

  private static Response putFailure(Throwable e) {
    var msg = PgExceptionUtil.badRequestMessage(e);
    if (msg == null) {
      msg = e.getMessage();
    }
    if (PgExceptionUtil.isVersionConflict(e)) {
      return PutItemStorageItemsByItemIdResponse.respond409WithTextPlain(msg);
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
    return PutItemStorageItemsByItemIdResponse.respond400WithTextPlain(msg);
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

  private Future<RowSet<Row>> updateEffectiveCallNumbersAndLocation(
    Conn connection, Collection<Item> items, HoldingsRecord holdingsRecord) {

    final Promise<RowSet<Row>> allItemsUpdated = promise();
    final var batchFactories = items.stream()
      .map(item -> {
        populateItemFromHoldings(item, holdingsRecord, effectiveValuesService);
        return item;
      })
      .map(this::updateSingleItemBatchFactory0)
      .toList();

    Future<RowSet<Row>> lastUpdate = succeededFuture();
    for (var factory : batchFactories) {
      lastUpdate = lastUpdate.compose(prev -> factory.apply(connection));
    }

    lastUpdate.onComplete(allItemsUpdated);
    return allItemsUpdated.future();
  }

  private Function<Conn, Future<RowSet<Row>>> updateSingleItemBatchFactory0(Item item) {
    return connection -> itemRepository.update(connection, item.getId(), item);
  }

  private Future<PutData> getItemAndHolding(String itemId, String holdingsId) {
    var sql = "SELECT item.jsonb::text, holdings_record.jsonb::text "
              + "FROM " + itemRepository.getFullTableName(ITEM_TABLE) + " "
              + "LEFT JOIN " + itemRepository.getFullTableName(HOLDINGS_RECORD_TABLE)
              + "  ON holdings_record.id = $2 "
              + "WHERE item.id = $1";
    return postgresClient.execute(sql, Tuple.of(itemId, holdingsId))
      .compose(rowSet -> {
        if (rowSet.size() == 0) {
          return Future.failedFuture(new ResponseException(
            PutItemStorageItemsByItemIdResponse.respond404WithTextPlain("Not found")));
        }
        var row = rowSet.iterator().next();
        if (row.getString(1) == null) {
          return Future.failedFuture(new ResponseException(
            PutItemStorageItemsByItemIdResponse.respond400WithTextPlain(
              "holdingsRecordId not found: " + holdingsId)));
        }
        var putData = new PutData();
        putData.oldItem = readValue(row.getString(0), Item.class);
        putData.newHoldings = readValue(row.getString(1), HoldingsRecord.class);
        return Future.succeededFuture(putData);
      });
  }

  private Future<Item> doUpdateItem(Item item) {
    if (Integer.valueOf(-1).equals(item.getVersion())) {
      item.setVersion(null);  // enforce optimistic locking
    }

    JsonObject itemJson;
    try {
      itemJson = pojo2JsonObject(item);
    } catch (Exception e) {
      return Future.failedFuture(e);
    }
    var tableName = itemRepository.getFullTableName(ITEM_TABLE);
    var tuple = Tuple.of(itemJson, item.getId());

    return postgresClient.execute("UPDATE " + tableName + " SET jsonb=$1 WHERE id=$2 RETURNING jsonb::text", tuple)
      .compose(rowSet -> {
        if (rowSet.size() != 1) {
          return Future.failedFuture(new ResponseException(
            PutItemStorageItemsByItemIdResponse.respond404WithTextPlain("Record not Found")));
        }
        return Future.succeededFuture(readValue(rowSet.iterator().next().getString(0), Item.class));
      })
      .recover(e -> Future.failedFuture(new ResponseException(putFailure(e))));
  }

  private Future<List<PatchData>> validateMultiplePatchItemsAndHoldings(List<PatchData> patchDataList) {
    if (patchDataList.isEmpty()) {
      return Future.succeededFuture(List.of());
    }

    var itemIds = patchDataList.stream()
      .map(patchData -> patchData.getNewItem().getId())
      .toList();

    return itemRepository.getItemsWithCurrentHoldings(itemIds)
      .compose(itemsRowSet -> {
        populateOldItemsAndCurrentHoldings(itemsRowSet, patchDataList);
        return ensureHoldingsAreLoaded(patchDataList)
          .compose(this::validateAndPopulatePatchData);
      });
  }

  private void populateOldItemsAndCurrentHoldings(RowSet<Row> itemsRowSet, List<PatchData> patchDataList) {
    var holdingsCache = new HashMap<String, HoldingsRecord>();
    var foundItemIds = new ArrayList<String>();
    var patchDataMap = patchDataList.stream()
      .<HashMap<String, PatchData>>collect(HashMap::new, (map, patchData) ->
        map.put(patchData.getNewItem().getId(), patchData), HashMap::putAll);

    // Populate old items and current holdings from database results
    for (var row : itemsRowSet) {
      processItemRow(row, patchDataMap, holdingsCache, foundItemIds);
    }

    validateAllItemsFound(foundItemIds, patchDataList);
  }

  private void processItemRow(Row row, Map<String, PatchData> patchDataMap,
                              Map<String, HoldingsRecord> holdingsCache, List<String> foundItemIds) {
    var itemId = row.getString(0);
    var itemJson = row.getString(1);
    var currentHoldingsJson = row.getString(2);
    var currentHoldingsId = row.getString(3);

    var patchData = patchDataMap.get(itemId);
    if (patchData != null) {
      patchData.setOldItem(readValue(itemJson, Item.class));

      // Cache current holdings if present
      if (currentHoldingsJson != null) {
        holdingsCache.putIfAbsent(currentHoldingsId,
          readValue(currentHoldingsJson, HoldingsRecord.class));
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

  private Future<List<PatchData>> ensureHoldingsAreLoaded(List<PatchData> patchDataList) {
    var missingHoldingsIds = patchDataList.stream()
      .filter(patchData -> patchData.getNewHoldings() == null)
      .map(patchData -> patchData.getNewItem().getHoldingsRecordId())
      .distinct()
      .toList();

    if (missingHoldingsIds.isEmpty()) {
      return Future.succeededFuture(patchDataList);
    }

    return holdingsRepository.getHoldingsByIds(missingHoldingsIds)
      .compose(holdingsRowSet -> assignMissingHoldings(patchDataList, holdingsRowSet));
  }

  private Future<List<PatchData>> assignMissingHoldings(List<PatchData> patchDataList, RowSet<Row> holdingsRowSet) {
    var holdingsMap = new HashMap<String, HoldingsRecord>();
    for (var row : holdingsRowSet) {
      var holdingsId = row.getString(0);
      var holdingsJson = row.getString(1);
      holdingsMap.put(holdingsId, readValue(holdingsJson, HoldingsRecord.class));
    }

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

  private void populateMetadata(Item item, Metadata metadata) {
    var oldMetadata = item.getMetadata();
    var updatedMetadata = new Metadata()
      .withCreatedByUserId(oldMetadata.getCreatedByUserId())
      .withCreatedByUsername(oldMetadata.getCreatedByUsername())
      .withCreatedDate(oldMetadata.getCreatedDate())
      .withUpdatedByUsername(metadata.getUpdatedByUsername())
      .withUpdatedByUserId(okapiHeaders.get(XOkapiHeaders.USER_ID))
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
