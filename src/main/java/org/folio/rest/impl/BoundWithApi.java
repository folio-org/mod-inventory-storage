package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.jaxrs.resource.InventoryStorageBoundWiths.PutInventoryStorageBoundWithsResponse.respond204;
import static org.folio.rest.support.EndpointFailureHandler.handleFailure;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.ws.rs.core.Response;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.persist.BoundWithRepository;
import org.folio.persist.HoldingsRepository;
import org.folio.persist.ItemRepository;
import org.folio.rest.annotations.Validate;
import org.folio.rest.exceptions.ValidationException;
import org.folio.rest.jaxrs.model.BoundWith;
import org.folio.rest.jaxrs.model.BoundWithContent;
import org.folio.rest.jaxrs.model.BoundWithPart;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.HoldingsRecord;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.services.instance.BoundWithPartService;

public class BoundWithApi implements org.folio.rest.jaxrs.resource.InventoryStorageBoundWiths {

  /**
   * Bound-with parts not in incoming: Future deletes.
   */
  private static List<Future<Response>> getDeleteBoundWithPartFutures(
    String mainHoldingsRecordId,
    Map<String, BoundWithPart> existingParts,
    Map<String, BoundWithContent> incomingParts,
    Context vertxContext,
    Map<String, String> okapiHeaders) {

    List<Future<Response>> deleteFutures = new ArrayList<>();
    BoundWithPartService service = new BoundWithPartService(vertxContext, okapiHeaders);
    for (var part : existingParts.entrySet()) {
      if (!incomingParts.containsKey(part.getKey())
        && !part.getKey().equals(mainHoldingsRecordId)) {
        deleteFutures.add(service.delete(part.getValue().getId()));
      }
    }
    if (existingParts.containsKey(mainHoldingsRecordId)
      && (incomingParts.size() == 1 && incomingParts.containsKey(mainHoldingsRecordId)
      || incomingParts.isEmpty())) {
      deleteFutures.add(service.delete(existingParts.get(mainHoldingsRecordId).getId()));
    }
    return deleteFutures;
  }

  /**
   * Bound-with parts not existing: future creates.
   */
  private static List<Future<Response>> getCreateBoundWithPartFutures(
    String itemId,
    String mainHoldingsId,
    Map<String, BoundWithContent> incomingParts,
    Map<String, BoundWithPart> existingParts,
    Map<String, String> okapiHeaders,
    Context vertxContext) {

    List<Future<Response>> createFutures = new ArrayList<>();
    BoundWithPartService service = new BoundWithPartService(vertxContext, okapiHeaders);
    if (!incomingParts.containsKey(mainHoldingsId) || incomingParts.size() > 1) {
      for (String holdingsId : incomingParts.keySet()) {
        if (!existingParts.containsKey(holdingsId)) {
          BoundWithPart part =
            new BoundWithPart()
              .withItemId(itemId)
              .withHoldingsRecordId(holdingsId)
              .withMetadata(new Metadata().withCreatedDate(new Date())
                .withUpdatedDate(new Date()));
          createFutures.add(service.create(part));
        }
      }
    }
    // Add main holdings ID, but only if it doesn't exist already,
    // wasn't provided in the request (and thus added above),
    // and would not become the only part.
    if (!existingParts.containsKey(mainHoldingsId)
      && !incomingParts.containsKey(mainHoldingsId)
      && !incomingParts.isEmpty()) {
      BoundWithPart part =
        new BoundWithPart()
          .withItemId(itemId)
          .withHoldingsRecordId(mainHoldingsId)
          .withMetadata(new Metadata().withCreatedDate(new Date()).withUpdatedDate(new Date()));
      createFutures.add(service.create(part));
    }
    return createFutures;
  }

  @Validate
  @Override
  public void putInventoryStorageBoundWiths(BoundWith entity,
                                            Map<String, String> okapiHeaders,
                                            Handler<AsyncResult<Response>> asyncResultHandler,
                                            Context vertxContext) {

    Map<String, BoundWithContent> incomingParts = getIncomingParts(entity);
    AtomicReference<Map<String, BoundWithPart>> existingParts = new AtomicReference<>();
    AtomicReference<Item> boundWithItem = new AtomicReference<>();

    validate(entity, vertxContext, okapiHeaders)
      .compose(x -> getExistingParts(entity, vertxContext, okapiHeaders))
      .onSuccess(existingParts::set)
      .compose(x -> getMainPart(entity, vertxContext, okapiHeaders))
      .onSuccess(boundWithItem::set)
      .compose(x -> {
        var futures = getCreateBoundWithPartFutures(
          entity.getItemId(), boundWithItem.get().getHoldingsRecordId(),
          incomingParts, existingParts.get(), okapiHeaders, vertxContext);
        return GenericCompositeFuture.all(futures);
      })
      .compose(x -> {
        var futures = getDeleteBoundWithPartFutures(
          boundWithItem.get().getHoldingsRecordId(),
          existingParts.get(), incomingParts, vertxContext, okapiHeaders);
        return GenericCompositeFuture.all(futures);
      })
      .onSuccess(x -> asyncResultHandler.handle(succeededFuture(respond204())))
      .onFailure(handleFailure(asyncResultHandler));
  }

  /**
   * Checks referential integrity for all involved records.
   */
  Future<Void> validate(BoundWith requestEntity, Context vertxContext, Map<String, String> okapiHeaders) {
    Errors errors = new Errors();
    ItemRepository items = new ItemRepository(vertxContext, okapiHeaders);
    return items.getById(requestEntity.getItemId())
      .compose(item -> {
        if (item == null) {
          addError(errors,
            "item.not-found", "Item not found.", "itemId", requestEntity.getItemId());
        }
        HoldingsRepository holdings = new HoldingsRepository(vertxContext, okapiHeaders);
        List<Future<HoldingsRecord>> holdingsFutures = new ArrayList<>();
        for (BoundWithContent entry : requestEntity.getBoundWithContents()) {
          holdingsFutures.add(holdings.getById(entry.getHoldingsRecordId()));
        }
        return GenericCompositeFuture.all(holdingsFutures);
      })
      .compose(holdingsFound -> {
        for (int i = 0; i < holdingsFound.size(); i++) {
          if (holdingsFound.resultAt(i) == null) {
            var id = requestEntity.getBoundWithContents().get(i).getHoldingsRecordId();
            addError(errors,
              "holding.not-found", "Holdings record not found.", "holdingsRecordId", id);
          }
        }
        if (errors.getErrors().isEmpty()) {
          return Future.succeededFuture();
        } else {
          return Future.failedFuture(new ValidationException(errors));
        }
      });
  }

  void addError(Errors errors, String code, String message, String key, String value) {
    errors.getErrors().add(new Error()
      .withCode(code)
      .withMessage(message)
      .withParameters(Collections.singletonList(new Parameter()
        .withKey(key)
        .withValue(value))));
  }

  /**
   * Incoming BoundWithContent objects mapped by holdings record ID.
   */
  private Map<String, BoundWithContent> getIncomingParts(BoundWith entity) {
    Map<String, BoundWithContent> incomingParts = new HashMap<>();
    for (BoundWithContent content : entity.getBoundWithContents()) {
      incomingParts.put(content.getHoldingsRecordId(), content);
    }
    return incomingParts;
  }

  /**
   * Get mandatory, main holdings record; the one directly linked to by the item.
   */
  private Future<Item> getMainPart(
    BoundWith entity, Context vertxContext, Map<String, String> okapiHeaders) {
    Promise<Item> promise = Promise.promise();
    return new ItemRepository(vertxContext, okapiHeaders)
      .getById(entity.getItemId())
      .onComplete(item -> promise.complete(item.result()));
  }

  /**
   * Existing BoundWithPart entities mapped by holdings record ID.
   */
  private Future<Map<String, BoundWithPart>> getExistingParts(
    BoundWith entity, Context vertxContext, Map<String, String> okapiHeaders) {

    Promise<Map<String, BoundWithPart>> promise = Promise.promise();
    Map<String, BoundWithPart> existingParts = new HashMap<>();
    Criterion criterion = new Criterion(new Criteria()
      .setJSONB(false).addField("itemId").setOperation("=")
      .setVal(entity.getItemId()));

    new BoundWithRepository(vertxContext, okapiHeaders)
      .get(criterion).onComplete(boundWithParts -> {
        for (BoundWithPart boundWith : boundWithParts.result()) {
          existingParts.put(boundWith.getHoldingsRecordId(), boundWith);
        }
        promise.complete(existingParts);
      });
    return promise.future();
  }
}

