package org.folio.services;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.apache.commons.lang3.StringUtils.isNoneBlank;
import static org.folio.rest.impl.HoldingsStorageAPI.HOLDINGS_RECORD_TABLE;
import static org.folio.rest.support.EffectiveCallNumberComponentsUtil.buildComponents;
import static org.folio.rest.tools.utils.ValidationHelper.createValidationErrorMessage;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.folio.rest.exceptions.ValidationException;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;

public class ItemEffectiveCallNumberComponentsService {
  private final PostgresClient postgresClient;

  public ItemEffectiveCallNumberComponentsService(Context context, Map<String, String> headers) {
    this.postgresClient = PgUtil.postgresClient(context, headers);
  }

  public ItemEffectiveCallNumberComponentsService(PostgresClient postgresClient) {
    this.postgresClient = postgresClient;
  }

  public Future<List<Item>> populateEffectiveCallNumberComponents(List<Item> items) {
    return getHoldingsRecordsForItems(items)
      .map(holdingsRecordMap -> {
        items.forEach(item -> {
          final HoldingsRecord holdingsRecord = holdingsRecordMap.get(item.getHoldingsRecordId());
          item.setEffectiveCallNumberComponents(buildComponents(holdingsRecord, item));
        });
        return items;
      });
  }

  public Future<Item> populateEffectiveCallNumberComponents(Item item) {
    return getHoldingsRecordForItem(item)
      .map(holdingsRecord -> buildComponents(holdingsRecord, item))
      .map(item::withEffectiveCallNumberComponents);
  }

  private Future<HoldingsRecord> getHoldingsRecordForItem(Item item) {
    if (shouldNotRetrieveHoldingsRecord(item)) {
      return succeededFuture(null);
    }

    final Promise<HoldingsRecord> promise = Promise.promise();
    postgresClient.getById(HOLDINGS_RECORD_TABLE, item.getHoldingsRecordId(),
      HoldingsRecord.class, promise);

    return promise.future()
      .compose(holdingsRecord -> {
        if (holdingsRecord != null) {
          return succeededFuture(holdingsRecord);
        }

        return failedFuture(
          holdingsRecordNotFoundException(item.getHoldingsRecordId()));
      });
  }

  private Future<Map<String, HoldingsRecord>> getHoldingsRecordsForItems(List<Item> items) {
    final Promise<Map<String, HoldingsRecord>> promise = Promise.promise();
    final List<String> holdingsIds = items.stream()
      .filter(this::shouldRetrieveHoldingsRecord)
      .map(Item::getHoldingsRecordId)
      .distinct()
      .collect(Collectors.toList());

    postgresClient.getById(HOLDINGS_RECORD_TABLE, new JsonArray(holdingsIds),
      HoldingsRecord.class, promise);

    return promise.future()
      .compose(holdingsRecordMap -> {
        if (holdingsRecordMap.keySet().containsAll(holdingsIds)) {
          return succeededFuture(holdingsRecordMap);
        }

        final String notFoundId = holdingsIds.stream()
          .filter(id -> !holdingsRecordMap.containsKey(id))
          .findFirst().orElse(null);

        return failedFuture(holdingsRecordNotFoundException(notFoundId));
      });
  }

  private boolean shouldNotRetrieveHoldingsRecord(Item item) {
    return isNoneBlank(item.getItemLevelCallNumber(),
      item.getItemLevelCallNumberPrefix(),
      item.getItemLevelCallNumberSuffix(),
      item.getItemLevelCallNumberTypeId());
  }

  private boolean shouldRetrieveHoldingsRecord(Item item) {
    return !shouldNotRetrieveHoldingsRecord(item);
  }

  private ValidationException holdingsRecordNotFoundException(String id) {
    return new ValidationException(createValidationErrorMessage("holdingsRecordId",
      id, "Holdings record does not exist"));
  }
}
