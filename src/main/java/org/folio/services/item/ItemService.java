package org.folio.services.item;

import static io.vertx.core.Future.succeededFuture;
import static io.vertx.core.Promise.promise;
import static java.util.Objects.isNull;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
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
import static org.folio.validator.HridValidators.refuseWhenHridChanged;
import static org.folio.validator.NotesValidators.refuseLongNotes;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.ws.rs.core.Response;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.persist.HoldingsRepository;
import org.folio.persist.ItemRepository;
import org.folio.rest.jaxrs.model.CirculationNote;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.PostgresClientFuturized;
import org.folio.rest.persist.SQLConnection;
import org.folio.rest.support.CqlQuery;
import org.folio.rest.support.HridManager;
import org.folio.rest.tools.client.exceptions.ResponseException;
import org.folio.services.ItemEffectiveValuesService;
import org.folio.services.domainevent.ItemDomainEventPublisher;
import org.folio.validator.CommonValidators;
import org.folio.validator.NotesValidators;

public class ItemService {
  private static final Pattern KEY_ALREADY_EXISTS_PATTERN = Pattern.compile(
    ": Key \\(([^=]+)\\)=\\((.*)\\) already exists.$");
  private static final Pattern KEY_NOT_PRESENT_PATTERN = Pattern.compile(
    ": Key \\(([^=]+)\\)=\\((.*)\\) is not present in table \"(.*)\".$");

  private final HridManager hridManager;
  private final ItemEffectiveValuesService effectiveValuesService;
  private final Context vertxContext;
  private final Map<String, String> okapiHeaders;
  private final ItemDomainEventPublisher domainEventService;
  private final ItemRepository itemRepository;
  private final PostgresClient postgresClient;
  private final PostgresClientFuturized postgresClientFuturized;
  private final HoldingsRepository holdingsRepository;

  public ItemService(Context vertxContext, Map<String, String> okapiHeaders) {
    this.vertxContext = vertxContext;
    this.okapiHeaders = okapiHeaders;

    postgresClient = postgresClient(vertxContext, okapiHeaders);
    postgresClientFuturized = new PostgresClientFuturized(postgresClient);
    hridManager = new HridManager(postgresClient);
    effectiveValuesService = new ItemEffectiveValuesService(vertxContext, okapiHeaders);
    domainEventService = new ItemDomainEventPublisher(vertxContext, okapiHeaders);
    itemRepository = new ItemRepository(vertxContext, okapiHeaders);
    holdingsRepository = new HoldingsRepository(vertxContext, okapiHeaders);
  }

  private static Response putFailure(Throwable e) {
    String msg = PgExceptionUtil.badRequestMessage(e);
    if (msg == null) {
      msg = e.getMessage();
    }
    if (PgExceptionUtil.isVersionConflict(e)) {
      return PutItemStorageItemsByItemIdResponse.respond409WithTextPlain(msg);
    }
    Matcher matcher = KEY_NOT_PRESENT_PATTERN.matcher(msg);
    if (matcher.find()) {
      String field = matcher.group(1);
      String value = matcher.group(2);
      String refTable = matcher.group(3);
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

  public Future<Response> createItem(Item entity) {
    entity.getStatus().setDate(new Date());

    return hridManager.populateHrid(entity)
      .compose(NotesValidators::refuseLongNotes)
      .compose(effectiveValuesService::populateEffectiveValues)
      .compose(this::populateCirculationNoteId)
      .compose(item -> {
        final Promise<Response> postResponse = promise();

        post(ITEM_TABLE, item, okapiHeaders, vertxContext,
          PostItemStorageItemsResponse.class, postResponse);

        return postResponse.future()
          .onSuccess(domainEventService.publishCreated());
      });
  }

  public Future<Response> createItems(List<Item> items, boolean upsert, boolean optimisticLocking) {
    final Date itemStatusDate = new java.util.Date();
    items.stream()
      .filter(item -> item.getStatus().getDate() == null)
      .forEach(item -> item.getStatus().setDate(itemStatusDate));

    return hridManager.populateHridForItems(items)
      .compose(NotesValidators::refuseItemLongNotes)
      .compose(result -> effectiveValuesService.populateEffectiveValues(items))
      .compose(this::populateCirculationNoteId)
      .compose(result -> buildBatchOperationContext(upsert, items, itemRepository, Item::getId))
      .compose(batchOperation -> postSync(ITEM_TABLE, items, MAX_ENTITIES, upsert, optimisticLocking,
        okapiHeaders, vertxContext, PostItemStorageBatchSynchronousResponse.class)
        .compose(this::handlePostSyncResponse)
        .onSuccess(domainEventService.publishCreatedOrUpdated(batchOperation)))
        .otherwise(error -> (Response) handleCreateItemsError(error));
  }

  private Future<Response> handlePostSyncResponse(Response response) {
    if (response.getStatus() == 201) {
      // Successful response from postSync
      return Future.succeededFuture(response);
    } else {
      // Failed response, assuming it's due to an error in saveBatch
      String errorMessage = "Failed to save items to the database. PostSync response: " + response.getStatus();
      return Future.failedFuture(new RuntimeException(errorMessage));
    }
  }

  private Future<Response> handleCreateItemsError(Throwable error) {
    // Handle any error that might occur during the createItems process
    String errorMessage = "Error during createItems operation: " + error.getMessage();
    return Future.failedFuture(new RuntimeException(errorMessage));
  }

  public Future<Response> updateItems(List<Item> items) {
    return createItems(items, true, true);
  }

  public Future<Response> updateItem(String itemId, Item newItem) {
    newItem.setId(itemId);
    PutData putData = new PutData();
    return refuseLongNotes(newItem)
      .compose(this::populateCirculationNoteId)
      .compose(notUsed -> getItemAndHolding(itemId, newItem.getHoldingsRecordId()))
      .onSuccess(putData::set)
      .compose(x -> refuseWhenHridChanged(putData.oldItem, newItem))
      .compose(x -> {
        if (newItem.getHoldingsRecordId().equals(putData.oldItem.getHoldingsRecordId())) {
          return Future.succeededFuture(putData.newHoldings);
        }
        return holdingsRepository.getById(putData.oldItem.getHoldingsRecordId());
      })
      .compose(oldHoldings -> {
        putData.oldHoldings = oldHoldings;
        effectiveValuesService.populateEffectiveValues(newItem, putData.newHoldings);
        return doUpdateItem(newItem);
      })
      .onSuccess(finalItem ->
        domainEventService.publishUpdated(finalItem, putData.oldItem, putData.newHoldings, putData.oldHoldings))
      .<Response>map(x -> PutItemStorageItemsByItemIdResponse.respond204())
      ;
  }

  public Future<Response> deleteItem(String itemId) {
    return itemRepository.getById(itemId)
      .compose(CommonValidators::refuseIfNotFound)
      .compose(item -> {
        final Promise<Response> deleteResult = promise();

        deleteById(ITEM_TABLE, itemId, okapiHeaders, vertxContext,
          DeleteItemStorageItemsByItemIdResponse.class, deleteResult);

        return deleteResult.future()
          .onSuccess(domainEventService.publishRemoved(item));
      });
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
        rowSet.iterator().forEachRemaining(row ->
          domainEventService.publishRemoved(row.getString(0), row.getString(1))
        )
      ))
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
  public Future<List<Item>> updateItemsOnHoldingChanged(AsyncResult<SQLConnection> connection,
                                                        HoldingsRecord holdingsRecord) {

    return itemRepository.getItemsForHoldingRecord(connection, holdingsRecord.getId())
      .compose(items -> updateEffectiveCallNumbersAndLocation(connection,
        // have to make deep clone of the items because the items are stateful
        // so that domain events will have proper 'old' item state.
        deepCopy(items, Item.class), holdingsRecord)
        .map(items));
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
    AsyncResult<SQLConnection> connectionResult, Collection<Item> items, HoldingsRecord holdingsRecord) {

    final Promise<RowSet<Row>> allItemsUpdated = promise();
    final var batchFactories = items.stream()
      .map(item -> {
        effectiveValuesService.populateEffectiveValues(item, holdingsRecord);
        if (isItemFieldsAffected(holdingsRecord, item)) {
          populateMetadata(item, holdingsRecord.getMetadata());
        }
        return item;
      })
      .map(this::updateSingleItemBatchFactory)
      .collect(toList());

    final SQLConnection connection = connectionResult.result();
    Future<RowSet<Row>> lastUpdate = succeededFuture();
    for (var factory : batchFactories) {
      lastUpdate = lastUpdate.compose(prev -> factory.apply(connection));
    }

    lastUpdate.onComplete(allItemsUpdated);
    return allItemsUpdated.future();
  }

  private Function<SQLConnection, Future<RowSet<Row>>> updateSingleItemBatchFactory(Item item) {
    return connection -> itemRepository.update(connection, item.getId(), item);
  }

  private Future<PutData> getItemAndHolding(String itemId, String holdingsId) {
    String sql = "SELECT item.jsonb::text, holdings_record.jsonb::text "
      + "FROM " + postgresClientFuturized.getFullTableName(ITEM_TABLE) + " "
      + "LEFT JOIN " + postgresClientFuturized.getFullTableName(HOLDINGS_RECORD_TABLE)
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
        PutData putData = new PutData();
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
    String tableName = postgresClientFuturized.getFullTableName(ITEM_TABLE);
    Tuple tuple = Tuple.of(itemJson, item.getId());

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

  private Future<List<Item>> populateCirculationNoteId(List<Item> items) {
    items.forEach(this::populateCirculationNoteId);
    return Future.succeededFuture(items);
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

  private static class PutData {
    private Item oldItem;
    private HoldingsRecord oldHoldings;
    private HoldingsRecord newHoldings;

    public void set(PutData other) {
      oldItem = other.oldItem;
      newHoldings = other.newHoldings;
    }
  }
}
