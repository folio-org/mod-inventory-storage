package org.folio.services.item;

import static io.vertx.core.Future.succeededFuture;
import static io.vertx.core.Promise.promise;
import static java.util.stream.Collectors.toList;
import static org.apache.logging.log4j.LogManager.getLogger;
import static org.folio.rest.impl.ItemStorageAPI.ITEM_TABLE;
import static org.folio.rest.impl.StorageHelper.MAX_ENTITIES;
import static org.folio.rest.jaxrs.resource.ItemStorage.DeleteItemStorageItemsByItemIdResponse;
import static org.folio.rest.jaxrs.resource.ItemStorage.PostItemStorageItemsResponse;
import static org.folio.rest.jaxrs.resource.ItemStorage.PutItemStorageItemsByItemIdResponse;
import static org.folio.rest.jaxrs.resource.ItemStorageBatchSynchronous.PostItemStorageBatchSynchronousResponse;
import static org.folio.rest.persist.PgUtil.deleteById;
import static org.folio.rest.persist.PgUtil.post;
import static org.folio.rest.persist.PgUtil.postSync;
import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.rest.support.CollectionUtil.deepCopy;
import static org.folio.services.batch.BatchOperationContextFactory.buildBatchOperationContext;
import static org.folio.validator.HridValidators.refuseWhenHridChanged;
import static org.folio.dbschema.ObjectMapperTool.readValue;

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
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.ws.rs.core.Response;

import org.apache.logging.log4j.Logger;
import org.folio.persist.HoldingsRepository;
import org.folio.persist.ItemRepository;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.PostgresClientFuturized;

import static org.folio.rest.persist.PostgresClient.pojo2JsonObject;
import org.folio.rest.persist.SQLConnection;
import org.folio.rest.support.HridManager;
import org.folio.services.ItemEffectiveValuesService;
import org.folio.services.domainevent.ItemDomainEventPublisher;
import org.folio.util.UuidUtil;
import org.folio.validator.CommonValidators;

public class ItemService {
  private static final Logger log = getLogger(ItemService.class);
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
  private final HoldingsRepository holdingsRepository;
  private final PostgresClient postgresClient;


  public ItemService(Context vertxContext, Map<String, String> okapiHeaders) {
    this.vertxContext = vertxContext;
    this.okapiHeaders = okapiHeaders;

    postgresClient = postgresClient(vertxContext, okapiHeaders);
    hridManager = new HridManager(vertxContext, postgresClient);
    effectiveValuesService = new ItemEffectiveValuesService(vertxContext, okapiHeaders);
    domainEventService = new ItemDomainEventPublisher(vertxContext, okapiHeaders);
    itemRepository = new ItemRepository(vertxContext, okapiHeaders);
    holdingsRepository = new HoldingsRepository(vertxContext, okapiHeaders);
  }

  public Future<Response> createItem(Item entity) {
    entity.getStatus().setDate(new Date());

    return hridManager.populateHrid(entity)
      .compose(effectiveValuesService::populateEffectiveValues)
      .compose(item -> {
        final Promise<Response> postResponse = promise();

        post(ITEM_TABLE, item, okapiHeaders, vertxContext,
          PostItemStorageItemsResponse.class, postResponse);

        return postResponse.future()
          .compose(domainEventService.publishCreated());
      });
  }

  public Future<Response> createItems(List<Item> items, boolean upsert) {
    final Date itemStatusDate = new java.util.Date();
    items.stream()
      .filter(item -> item.getStatus().getDate() == null)
      .forEach(item -> item.getStatus().setDate(itemStatusDate));

    return hridManager.populateHridForItems(items)
      .compose(result -> effectiveValuesService.populateEffectiveValues(items))
      .compose(result -> buildBatchOperationContext(upsert, items, itemRepository, Item::getId))
      .compose(batchOperation -> {
        final Promise<Response> postSyncResult = promise();

        postSync(ITEM_TABLE, items, MAX_ENTITIES, upsert,
          okapiHeaders, vertxContext, PostItemStorageBatchSynchronousResponse.class, postSyncResult);

        return postSyncResult.future()
          .compose(domainEventService.publishCreatedOrUpdated(batchOperation));
      });
  }

  public Future<Response> updateItems(List<Item> items) {
    return createItems(items, true);
  }

  public Future<Response> updateItem(String itemId, Item newItem) {
    return itemRepository.getById(itemId)
      .compose(CommonValidators::refuseIfNotFound)
      .compose(oldItem -> refuseWhenHridChanged(oldItem, newItem))
      .compose(oldItem -> handleItemPut(oldItem, newItem));
  }

  public Future<Response> deleteItem(String itemId) {
    return itemRepository.getById(itemId)
      .compose(CommonValidators::refuseIfNotFound)
      .compose(item -> {
        final Promise<Response> deleteResult = promise();

        deleteById(ITEM_TABLE, itemId, okapiHeaders, vertxContext,
          DeleteItemStorageItemsByItemIdResponse.class, deleteResult);

        return deleteResult.future()
          .compose(domainEventService.publishRemoved(item));
      });
  }

  public Future<Void> deleteAllItems() {
    return itemRepository.deleteAll()
      .compose(notUsed -> domainEventService.publishAllRemoved());
  }

  /**
   * @return items before update.
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

  private Future<RowSet<Row>> updateEffectiveCallNumbersAndLocation(
    AsyncResult<SQLConnection> connectionResult, Collection<Item> items, HoldingsRecord holdingsRecord) {

    final Promise<RowSet<Row>> allItemsUpdated = promise();
    final var batchFactories = items.stream()
      .map(item -> effectiveValuesService.populateEffectiveValues(item, holdingsRecord))
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

  private Future<Response> handleItemPut(Item oldItem, Item newItem) {
    if (! UuidUtil.isUuid(newItem.getId())) {
      return Future.succeededFuture(PutItemStorageItemsByItemIdResponse.respond400WithTextPlain("Invalid UUid"));
    }
    return holdingsRepository.getById(newItem.getHoldingsRecordId())
      .compose(holdingsRecord -> {
        return putItemAndPublishResults(
          effectiveValuesService.populateEffectiveValues(newItem, holdingsRecord), oldItem, holdingsRecord);
      }
    );
  }

  private Future<Response> putItemAndPublishResults(Item newItem, Item oldItem, HoldingsRecord holdingsRecord) {

    JsonObject record;
    try{
      record = pojo2JsonObject(newItem);
    } catch(Exception e) {
      String msg = "Cannot map item record to json: " + e.getMessage();
      log.error(msg);
      return Future.succeededFuture(
        PutItemStorageItemsByItemIdResponse.respond400WithTextPlain(msg));
    }
    String tableName = new PostgresClientFuturized(postgresClient).getFullTableName(ITEM_TABLE);
    Tuple tuple = Tuple.of(record, newItem.getId());

    return postgresClient.execute("UPDATE " + tableName + " SET jsonb=$1 WHERE id=$2 RETURNING jsonb", tuple)
      .compose(rowSet -> {
        if (rowSet.size() != 1) {
          return Future.succeededFuture(
              PutItemStorageItemsByItemIdResponse.respond404WithTextPlain("Record not Found"));
        }
        Item finalItem = readValue(rowSet.iterator().next().getJson("jsonb").toString(), Item.class);

        return domainEventService.publishUpdated(finalItem, oldItem, holdingsRecord)
            .<Response>map(x -> PutItemStorageItemsByItemIdResponse.respond204())
            .otherwise(e -> PutItemStorageItemsByItemIdResponse.respond500WithTextPlain(e.getMessage()));
      })
      .otherwise(e -> putFailure(e));
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
}
