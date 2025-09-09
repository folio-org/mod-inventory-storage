package org.folio.services;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.apache.commons.lang3.StringUtils.isAllBlank;
import static org.apache.commons.lang3.StringUtils.isAnyBlank;
import static org.folio.rest.support.EffectiveCallNumberComponentsUtil.calculateAndSetEffectiveShelvingOrder;
import static org.folio.rest.support.EffectiveCallNumberComponentsUtil.setCallNumberComponents;
import static org.folio.rest.support.ItemEffectiveLocationUtil.updateItemEffectiveLocation;
import static org.folio.rest.tools.utils.ValidationHelper.createValidationErrorMessage;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.folio.persist.HoldingsRepository;
import org.folio.rest.exceptions.ValidationException;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;
import org.folio.services.item.PatchData;

public class ItemEffectiveValuesService {
  private final HoldingsRepository holdingsRepository;

  public ItemEffectiveValuesService(Context context, Map<String, String> okapiHeaders) {
    this.holdingsRepository = new HoldingsRepository(context, okapiHeaders);
  }

  public Future<List<Item>> populateEffectiveValues(List<Item> items) {
    return getHoldingsRecordsForItems(items)
      .map(holdingsRecordMap -> items.stream()
        .map(item -> populateEffectiveValues(item, holdingsRecordMap.get(item.getHoldingsRecordId())))
        .toList());
  }

  public Future<Item> populateEffectiveValues(Item item) {
    return populateEffectiveValues(Collections.singletonList(item))
      // item is stateful - ok to return the passed object
      .map(items -> item);
  }

  public Item populateEffectiveValues(Item item, HoldingsRecord hr) {
    updateItemEffectiveLocation(item, hr);
    setCallNumberComponents(item, hr);
    calculateAndSetEffectiveShelvingOrder(item);
    return item;
  }

  public void populateEffectiveValues(Item newItem, PatchData patchData) {
    updateItemEffectiveLocation(newItem, patchData.getNewHoldings());
    setCallNumberComponents(newItem, patchData);
    calculateAndSetEffectiveShelvingOrder(newItem);
  }

  private Future<Map<String, HoldingsRecord>> getHoldingsRecordsForItems(List<Item> items) {
    final Set<String> holdingsIds = items.stream()
      .filter(this::shouldRetrieveHoldingsRecord)
      .map(Item::getHoldingsRecordId)
      .collect(Collectors.toSet());

    return holdingsRepository.getByIds(holdingsIds)
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

  private boolean shouldRetrieveHoldingsRecord(Item item) {
    // Should not retrieve holding if all properties needed for the
    // effective values are present on the item level.
    return isAnyBlank(item.getItemLevelCallNumber(),
      item.getItemLevelCallNumberPrefix(),
      item.getItemLevelCallNumberSuffix(),
      item.getItemLevelCallNumberTypeId())
      || isAllBlank(item.getPermanentLocationId(), item.getTemporaryLocationId());
  }

  private ValidationException holdingsRecordNotFoundException(String id) {
    return new ValidationException(createValidationErrorMessage("holdingsRecordId",
      id, "Holdings record does not exist"));
  }
}
