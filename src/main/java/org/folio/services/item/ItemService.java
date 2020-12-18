package org.folio.services.item;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static io.vertx.core.Promise.promise;
import static java.lang.String.format;
import static java.util.Collections.emptyMap;
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
import static org.folio.rest.tools.utils.TenantTool.tenantId;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.logging.log4j.Logger;
import org.folio.rest.exceptions.BadRequestException;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.SQLConnection;
import org.folio.rest.support.EffectiveCallNumberComponentsUtil;
import org.folio.rest.support.HridManager;
import org.folio.rest.support.ItemEffectiveLocationUtil;
import org.folio.services.ItemEffectiveValuesService;
import org.folio.services.item.effectivevalues.ItemWithHolding;
import org.folio.services.persist.PostgresClientFuturized;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

public class ItemService {
  private static final Logger log = getLogger(ItemService.class);
  private static final String WHERE_CLAUSE = "WHERE id = '%s'";

  private final PostgresClient postgresClient;
  private final HridManager hridManager;
  private final ItemEffectiveValuesService effectiveValuesService;
  private final Context vertxContext;
  private final Map<String, String> okapiHeaders;
  private final PostgresClientFuturized postgresClientFuturized;

  public ItemService(Context vertxContext, Map<String, String> okapiHeaders) {
    this.vertxContext = vertxContext;
    this.okapiHeaders = okapiHeaders;
    postgresClient = postgresClient(vertxContext, okapiHeaders);
    hridManager = new HridManager(vertxContext, postgresClient);
    effectiveValuesService = new ItemEffectiveValuesService(postgresClient);
    postgresClientFuturized = new PostgresClientFuturized(postgresClient);
  }

  public Future<Response> createItem(Item entity) {
    entity.getStatus().setDate(new Date());

    return getHrid(entity).map(entity::withHrid)
      .compose(effectiveValuesService::populateEffectiveValues)
      .compose(itemWithHolding -> {
        final Promise<Response> postResponse = promise();

        post(ITEM_TABLE, itemWithHolding.getItem(), okapiHeaders, vertxContext,
          PostItemStorageItemsResponse.class, postResponse);

        return postResponse.future();
      });
  }

  public Future<Response> createItems(List<Item> items, boolean upsert) {
    final Date itemStatusDate = new java.util.Date();

    @SuppressWarnings("all")
    final List<Future> setHridFutures = items.stream()
      .map(item -> {
        item.getStatus().setDate(itemStatusDate);
        return getHrid(item).map(item::withHrid);
      }).collect(Collectors.toList());

    final Set<String> itemIds = items.stream()
      .map(Item::getId)
      .collect(Collectors.toSet());

    return CompositeFuture.all(setHridFutures)
      .compose(result -> effectiveValuesService.populateEffectiveValues(items))
      .compose(itemWithHoldings -> {
        if (!upsert) {
          return succeededFuture(new ImmutablePair<>(itemWithHoldings, emptyMap()));
        }

        return postgresClientFuturized.getById(ITEM_TABLE, itemIds, Item.class)
          .map(existingItems -> new ImmutablePair<>(itemWithHoldings, existingItems));
      }).compose(pair -> {
        // Used for logging now, will be updated soon to send domain events.
        log.info("Items that will be updated - {} ", pair.getRight().keySet());

        final Promise<Response> postSyncResult = promise();

        postSync(ITEM_TABLE, items, MAX_ENTITIES, upsert, okapiHeaders, vertxContext,
          PostItemStorageBatchSynchronousResponse.class, postSyncResult);

        return postSyncResult.future();
      });
  }

  public Future<Response> updateItem(String itemId, Item newItem) {
    return postgresClientFuturized.getById(ITEM_TABLE, itemId, Item.class)
      .compose(oldItem -> refuseWhenHridChanged(oldItem, newItem))
      .compose(oldItem -> effectiveValuesService.populateEffectiveValues(newItem)
        .compose(itemWithHolding -> {
          final Promise<Response> putResult = promise();

          put(ITEM_TABLE, newItem, itemId, okapiHeaders, vertxContext,
            PutItemStorageItemsByItemIdResponse.class, putResult);

          return putResult.future();
        }));
  }

  public Future<Response> deleteItem(String itemId) {
    return postgresClientFuturized.getById(ITEM_TABLE, itemId, Item.class)
      .compose(item -> {
        final Promise<Response> deleteResult = promise();
        deleteById(ITEM_TABLE, itemId, okapiHeaders, vertxContext,
          DeleteItemStorageItemsByItemIdResponse.class, deleteResult);

        return deleteResult.future();
      });
  }

  public Future<Void> deleteAllItems() {
    final String removeAllQuery = format("DELETE FROM %s_mod_inventory_storage.item",
      tenantId(okapiHeaders));

    return postgresClientFuturized.get(ITEM_TABLE, new Item())
      .compose(allItems -> postgresClientFuturized.execute(removeAllQuery))
      .map(notUsed -> null);
  }

  public Future<Void> updateItemsOnHoldingChanged(AsyncResult<SQLConnection> connection,
    HoldingsRecord holdingsRecord) {

    final Item sample = new Item().withHoldingsRecordId(holdingsRecord.getId());
    return postgresClientFuturized.get(ITEM_TABLE, sample)
      .compose(items -> updateEffectiveCallNumbersAndLocation(connection, items, holdingsRecord))
      .map(notUsed -> null);
  }

  private Future<RowSet<Row>> updateEffectiveCallNumbersAndLocation(
    AsyncResult<SQLConnection> connectionResult, List<Item> items, HoldingsRecord holdingsRecord) {

    final Promise<RowSet<Row>> allItemsUpdated = promise();
    final List<Function<SQLConnection, Future<RowSet<Row>>>> batchFactories = items.stream()
      .map(item -> new ItemWithHolding(item, holdingsRecord))
      .map(EffectiveCallNumberComponentsUtil::setCallNumberComponents)
      .map(ItemEffectiveLocationUtil::updateItemEffectiveLocation)
      .map(itemWithHolding -> updateSingleItemBatchFactory(itemWithHolding.getItemId(),
        itemWithHolding.getItem()))
      .collect(Collectors.toList());

    final SQLConnection connection = connectionResult.result();
    Future<RowSet<Row>> lastUpdate = succeededFuture();
    for (Function<SQLConnection, Future<RowSet<Row>>> factory : batchFactories) {
      lastUpdate = lastUpdate.compose(prev -> factory.apply(connection));
    }

    lastUpdate.onComplete(allItemsUpdated);
    return allItemsUpdated.future();
  }

  private <T> Function<SQLConnection, Future<RowSet<Row>>> updateSingleItemBatchFactory(
    String id, T entity) {

    return connection -> {
      final Promise<RowSet<Row>> updateResultFuture = promise();
      final Future<SQLConnection> connectionResult = succeededFuture(connection);

      postgresClient.update(connectionResult, ITEM_TABLE, entity, "jsonb",
        format(WHERE_CLAUSE, id), false, updateResultFuture);

      return updateResultFuture.future();
    };
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
}
