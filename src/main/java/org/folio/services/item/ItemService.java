package org.folio.services.item;

import static io.vertx.core.CompositeFuture.all;
import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static io.vertx.core.Promise.promise;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.logging.log4j.LogManager.getLogger;
import static org.folio.rest.impl.HoldingsStorageAPI.ITEM_TABLE;
import static org.folio.rest.impl.StorageHelper.MAX_ENTITIES;
import static org.folio.rest.jaxrs.resource.ItemStorage.DeleteItemStorageItemsByItemIdResponse;
import static org.folio.rest.jaxrs.resource.ItemStorage.PostItemStorageItemsResponse;
import static org.folio.rest.jaxrs.resource.ItemStorage.PutItemStorageItemsByItemIdResponse;
import static org.folio.rest.jaxrs.resource.ItemStorageBatchSynchronous.PostItemStorageBatchSynchronousResponse;
import static org.folio.rest.persist.PgUtil.deleteById;
import static org.folio.rest.persist.PgUtil.post;
import static org.folio.rest.persist.PgUtil.postSync;
import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.rest.persist.PgUtil.put;
import static org.folio.rest.support.CollectionUtil.deepCopy;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.Logger;
import org.folio.persist.ItemRepository;
import org.folio.rest.exceptions.BadRequestException;
import org.folio.rest.exceptions.NotFoundException;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.SQLConnection;
import org.folio.rest.support.EffectiveCallNumberComponentsUtil;
import org.folio.rest.support.HridManager;
import org.folio.rest.support.ItemEffectiveLocationUtil;
import org.folio.services.ItemEffectiveValuesService;
import org.folio.services.batch.BatchOperation;
import org.folio.services.domainevent.ItemDomainEventService;
import org.folio.services.item.effectivevalues.ItemWithHolding;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

public class ItemService {
  private static final Logger log = getLogger(ItemService.class);

  private final HridManager hridManager;
  private final ItemEffectiveValuesService effectiveValuesService;
  private final Context vertxContext;
  private final Map<String, String> okapiHeaders;
  private final ItemDomainEventService domainEventService;
  private final ItemRepository itemRepository;

  public ItemService(Context vertxContext, Map<String, String> okapiHeaders) {
    this.vertxContext = vertxContext;
    this.okapiHeaders = okapiHeaders;

    final PostgresClient postgresClient = postgresClient(vertxContext, okapiHeaders);
    hridManager = new HridManager(vertxContext, postgresClient);
    effectiveValuesService = new ItemEffectiveValuesService(postgresClient);
    domainEventService = new ItemDomainEventService(vertxContext, okapiHeaders);
    itemRepository = new ItemRepository(vertxContext, okapiHeaders);
  }

  public Future<Response> createItem(Item entity) {
    entity.getStatus().setDate(new Date());

    return getHrid(entity).map(entity::withHrid)
      .compose(effectiveValuesService::populateEffectiveValues)
      .compose(itemWithHolding -> {
        final Promise<Response> postResponse = promise();

        post(ITEM_TABLE, itemWithHolding.getItem(), okapiHeaders, vertxContext,
          PostItemStorageItemsResponse.class, postResponse);

        return postResponse.future()
          .compose(domainEventService.itemCreated(itemWithHolding));
      });
  }

  public Future<Response> createItems(List<Item> items, boolean upsert) {
    final Date itemStatusDate = new java.util.Date();

    @SuppressWarnings("all")
    final List<Future> setHridFutures = items.stream()
      .map(item -> {
        item.getStatus().setDate(itemStatusDate);
        return getHrid(item).map(item::withHrid);
      }).collect(toList());

    return all(setHridFutures)
      .compose(result -> effectiveValuesService.populateEffectiveValues(items))
      .compose(itemsWithHoldings -> buildBatchOperationContext(upsert, itemsWithHoldings))
      .compose(batchOperation -> {
        final Promise<Response> postSyncResult = promise();

        final var itemsWithUpdatedEffectiveValues = batchOperation.allRecordsStream()
          .map(ItemWithHolding::getItem)
          .collect(toList());

        postSync(ITEM_TABLE, itemsWithUpdatedEffectiveValues, MAX_ENTITIES, upsert,
          okapiHeaders, vertxContext, PostItemStorageBatchSynchronousResponse.class, postSyncResult);

        return postSyncResult.future()
          .compose(domainEventService.itemsCreatedOrUpdated(batchOperation));
      });
  }

  public Future<Response> updateItem(String itemId, Item newItem) {
    return itemRepository.getById(itemId)
      .compose(this::refuseIfNotFound)
      .compose(oldItem -> refuseWhenHridChanged(oldItem, newItem))
      .compose(oldItem -> effectiveValuesService.populateEffectiveValues(newItem)
        .compose(itemWithHolding -> {
          final Promise<Response> putResult = promise();

          put(ITEM_TABLE, newItem, itemId, okapiHeaders, vertxContext,
            PutItemStorageItemsByItemIdResponse.class, putResult);

          return putResult.future()
            .compose(domainEventService
              .itemUpdated(itemWithHolding.getInstanceId(), oldItem));
        }));
  }

  public Future<Response> deleteItem(String itemId) {
    return itemRepository.getById(itemId)
      .compose(this::refuseIfNotFound)
      .compose(item -> {
        final Promise<Response> deleteResult = promise();

        deleteById(ITEM_TABLE, itemId, okapiHeaders, vertxContext,
          DeleteItemStorageItemsByItemIdResponse.class, deleteResult);

        return deleteResult.future()
          .compose(domainEventService.itemRemoved(item));
      });
  }

  public Future<Void> deleteAllItems() {
    return itemRepository.getAll()
      .compose(allItems -> itemRepository.deleteAll()
        .compose(notUsed -> domainEventService.itemsRemoved(allItems)));
  }

  /**
   * @return items before update.
   */
  public Future<List<Item>> updateItemsOnHoldingChanged(AsyncResult<SQLConnection> connection,
    HoldingsRecord holdingsRecord) {

    return itemRepository.getItemsForHoldingRecord(holdingsRecord.getId())
      .compose(items -> updateEffectiveCallNumbersAndLocation(connection,
        deepCopy(items, Item.class), holdingsRecord)
        .map(items));
  }

  private Future<RowSet<Row>> updateEffectiveCallNumbersAndLocation(
    AsyncResult<SQLConnection> connectionResult, Collection<Item> items, HoldingsRecord holdingsRecord) {

    final Promise<RowSet<Row>> allItemsUpdated = promise();
    final var batchFactories = items.stream()
      .map(item -> new ItemWithHolding(item, holdingsRecord))
      .map(EffectiveCallNumberComponentsUtil::setCallNumberComponents)
      .map(ItemEffectiveLocationUtil::updateItemEffectiveLocation)
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

  private Function<SQLConnection, Future<RowSet<Row>>> updateSingleItemBatchFactory(
    ItemWithHolding itemWithHolding) {

    return connection -> itemRepository.update(connection, itemWithHolding.getItemId(),
      itemWithHolding.getItem());
  }

  private Future<Item> refuseWhenHridChanged(Item oldItem, Item newItem) {
    if (Objects.equals(newItem.getHrid(), oldItem.getHrid())) {
      return succeededFuture(oldItem);
    } else {
      log.warn("HRID has been changed for item {}", oldItem.getId());
      return failedFuture(new BadRequestException(format(
        "The hrid field cannot be changed: new=%s, old=%s", newItem.getHrid(),
        oldItem.getHrid())));
    }
  }

  private Future<String> getHrid(Item entity) {
    return isBlank(entity.getHrid())
      ? hridManager.getNextItemHrid() : succeededFuture(entity.getHrid());
  }

  private Future<Item> refuseIfNotFound(Item item) {
    return item != null ? succeededFuture(item) : failedFuture(new NotFoundException("Not found"));
  }

  private Future<BatchOperation<ItemWithHolding>> buildBatchOperationContext(
    boolean upsert, List<ItemWithHolding> allItems) {

    if (!upsert) {
      return succeededFuture(new BatchOperation<>(allItems, emptyList(), emptyList()));
    }

    return itemRepository.getById(allItems, ItemWithHolding::getItemId)
      .map(foundItems -> {
        final var itemsToBeCreated = allItems.stream()
          .filter(item -> !foundItems.containsKey(item.getItemId()))
          .collect(toList());

        // new Item representations
        final var itemsToBeUpdated = allItems.stream()
          .filter(item -> foundItems.containsKey(item.getItemId()))
          .collect(toList());

        // old Item representations
        final var existingRecordsBeforeUpdate = itemsToBeUpdated.stream()
          .map(itemWithHolding -> {
            final var itemBeforeUpdate = foundItems.get(itemWithHolding.getItemId());
            return new ItemWithHolding(itemBeforeUpdate, itemWithHolding.getHoldingsRecord());
          }).collect(toList());

        return new BatchOperation<>(itemsToBeCreated, itemsToBeUpdated, existingRecordsBeforeUpdate);
      });
  }
}
