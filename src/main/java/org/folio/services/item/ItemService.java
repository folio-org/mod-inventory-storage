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
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.SQLConnection;
import org.folio.rest.support.HridManager;
import org.folio.services.ItemEffectiveValuesService;
import org.folio.services.batch.BatchOperationContext;
import org.folio.services.domainevent.ItemDomainEventPublisher;
import org.folio.validator.CommonValidators;

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
  private final ItemDomainEventPublisher domainEventService;
  private final ItemRepository itemRepository;

  public ItemService(Context vertxContext, Map<String, String> okapiHeaders) {
    this.vertxContext = vertxContext;
    this.okapiHeaders = okapiHeaders;

    final PostgresClient postgresClient = postgresClient(vertxContext, okapiHeaders);
    hridManager = new HridManager(vertxContext, postgresClient);
    effectiveValuesService = new ItemEffectiveValuesService(vertxContext, okapiHeaders);
    domainEventService = new ItemDomainEventPublisher(vertxContext, okapiHeaders);
    itemRepository = new ItemRepository(vertxContext, okapiHeaders);
  }

  public Future<Response> createItem(Item entity) {
    entity.getStatus().setDate(new Date());

    return setHrid(entity)
      .compose(effectiveValuesService::populateEffectiveValues)
      .compose(item -> {
        final Promise<Response> postResponse = promise();

        post(ITEM_TABLE, item, okapiHeaders, vertxContext,
          PostItemStorageItemsResponse.class, postResponse);

        return postResponse.future()
          .compose(domainEventService.publishCreated(item));
      });
  }

  public Future<Response> createItems(List<Item> items, boolean upsert) {
    final Date itemStatusDate = new java.util.Date();

    @SuppressWarnings("all")
    final List<Future> setHridFutures = items.stream()
      .map(item -> {
        item.getStatus().setDate(itemStatusDate);
        return setHrid(item);
      }).collect(toList());

    return all(setHridFutures)
      .compose(result -> effectiveValuesService.populateEffectiveValues(items))
      .compose(result -> buildBatchOperationContext(upsert, items))
      .compose(batchOperation -> {
        final Promise<Response> postSyncResult = promise();

        postSync(ITEM_TABLE, items, MAX_ENTITIES, upsert,
          okapiHeaders, vertxContext, PostItemStorageBatchSynchronousResponse.class, postSyncResult);

        return postSyncResult.future()
          .compose(domainEventService.publishCreatedOrUpdated(batchOperation));
      });
  }

  public Future<Response> updateItem(String itemId, Item newItem) {
    return itemRepository.getById(itemId)
      .compose(CommonValidators::refuseIfNotFound)
      .compose(oldItem -> refuseWhenHridChanged(oldItem, newItem))
      .compose(oldItem -> effectiveValuesService.populateEffectiveValues(newItem)
        .compose(notUsed -> {
          final Promise<Response> putResult = promise();

          put(ITEM_TABLE, newItem, itemId, okapiHeaders, vertxContext,
            PutItemStorageItemsByItemIdResponse.class, putResult);

          return putResult.future()
            .compose(domainEventService.publishUpdated(oldItem));
        }));
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
    return itemRepository.getAll()
      .compose(allItems -> itemRepository.deleteAll()
        .compose(notUsed -> domainEventService.publishRemoved(allItems)));
  }

  /**
   * @return items before update.
   */
  public Future<List<Item>> updateItemsOnHoldingChanged(AsyncResult<SQLConnection> connection,
    HoldingsRecord holdingsRecord) {

    return itemRepository.getItemsForHoldingRecord(holdingsRecord.getId())
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

  private Future<Item> setHrid(Item entity) {
    return isBlank(entity.getHrid())
      ? hridManager.getNextItemHrid().map(entity::withHrid)
      : succeededFuture(entity);
  }

  private Future<BatchOperationContext<Item>> buildBatchOperationContext(
    boolean upsert, List<Item> allItems) {

    if (!upsert) {
      return succeededFuture(new BatchOperationContext<>(allItems, emptyList()));
    }

    return itemRepository.getById(allItems, Item::getId)
      .map(foundItems -> {
        final var itemsToBeCreated = allItems.stream()
          .filter(item -> !foundItems.containsKey(item.getId()))
          .collect(toList());

        return new BatchOperationContext<>(itemsToBeCreated, foundItems.values());
      });
  }
}
