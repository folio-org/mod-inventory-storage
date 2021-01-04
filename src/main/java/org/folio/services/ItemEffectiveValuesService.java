package org.folio.services;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.tools.utils.ValidationHelper.createValidationErrorMessage;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.folio.persist.HoldingsRepository;
import org.folio.rest.exceptions.ValidationException;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.EffectiveCallNumberComponentsUtil;
import org.folio.rest.support.ItemEffectiveLocationUtil;
import org.folio.services.item.effectivevalues.ItemWithHolding;

import io.vertx.core.Future;

public class ItemEffectiveValuesService {
  private final HoldingsRepository holdingsRepository;

  public ItemEffectiveValuesService(PostgresClient postgresClient) {
    this.holdingsRepository = new HoldingsRepository(postgresClient);
  }

  public Future<List<ItemWithHolding>> populateEffectiveValues(List<Item> items) {
    return getHoldingsRecordsForItems(items)
      .map(holdingsRecordMap -> items.stream()
        .map(item -> new ItemWithHolding(item,
          holdingsRecordMap.get(item.getHoldingsRecordId())))
        .map(ItemEffectiveLocationUtil::updateItemEffectiveLocation)
        .map(EffectiveCallNumberComponentsUtil::setCallNumberComponents)
        .collect(Collectors.toList()));
  }

  public Future<ItemWithHolding> populateEffectiveValues(Item item) {
    return getHoldingsRecordForItem(item)
      .map(holdingsRecord -> new ItemWithHolding(item, holdingsRecord))
      .map(ItemEffectiveLocationUtil::updateItemEffectiveLocation)
      .map(EffectiveCallNumberComponentsUtil::setCallNumberComponents);
  }

  private Future<HoldingsRecord> getHoldingsRecordForItem(Item item) {
    final String holdingsRecordId = item.getHoldingsRecordId();

    return holdingsRepository.getById(holdingsRecordId)
      .compose(holdingsRecord -> {
        if (holdingsRecord != null) {
          return succeededFuture(holdingsRecord);
        }

        return failedFuture(holdingsRecordNotFoundException(holdingsRecordId));
      });
  }

  private Future<Map<String, HoldingsRecord>> getHoldingsRecordsForItems(List<Item> items) {
    final Set<String> holdingsIds = items.stream()
      .map(Item::getHoldingsRecordId)
      .collect(Collectors.toSet());

    return holdingsRepository.getById(holdingsIds)
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

  private ValidationException holdingsRecordNotFoundException(String id) {
    return new ValidationException(createValidationErrorMessage("holdingsRecordId",
      id, "Holdings record does not exist"));
  }
}
