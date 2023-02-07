package org.folio.rest.impl;

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
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.services.instance.BoundWithPartService;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.support.EndpointFailureHandler.handleFailure;
import static org.folio.rest.jaxrs.resource.InventoryStorageBoundWiths.PutInventoryStorageBoundWithsResponse.respond204;

public class BoundWithAPI implements org.folio.rest.jaxrs.resource.InventoryStorageBoundWiths {

  @Validate
  @Override
  public void putInventoryStorageBoundWiths(BoundWith entity,
                                            Map<String, String> okapiHeaders,
                                            Handler<AsyncResult<Response>> asyncResultHandler,
                                            Context vertxContext) {

    Map<String, BoundWithContent> incomingParts = getIncomingParts(entity);
    AtomicReference<Map<String,BoundWithPart>> existingParts = new AtomicReference<>();

    validate(entity, vertxContext, okapiHeaders)
    .compose(x -> getExistingParts(entity, vertxContext, okapiHeaders))
    .onSuccess(existingParts::set)
    .compose(x -> {
      var futures = getCreateBoundWithPartFutures(
          entity.getItemId(), incomingParts, existingParts.get(), okapiHeaders, vertxContext);
      return GenericCompositeFuture.all(futures);
    })
    .compose(x -> {
      var futures = getDeleteBoundWithPartFutures(
          existingParts.get(), incomingParts, vertxContext, okapiHeaders);
      return GenericCompositeFuture.all(futures);
    })
    .onSuccess(x -> asyncResultHandler.handle(succeededFuture(respond204())))
    .onFailure(handleFailure(asyncResultHandler));
  }

  /**
   * Incoming BoundWithContent objects mapped by holdings record ID.
   */
  private Map<String, BoundWithContent> getIncomingParts (BoundWith entity) {
    Map<String, BoundWithContent> incomingParts = new HashMap<>();
    for (BoundWithContent content : entity.getBoundWithContents()) {
      incomingParts.put(content.getHoldingsRecordId(), content);
    }
    return incomingParts;
  }

  /**
   * Existing BoundWithPart entities mapped by holdings record ID.
   */
  private Future<Map<String,BoundWithPart>> getExistingParts(
      BoundWith entity, Context vertxContext, Map<String,String> okapiHeaders) {

    Promise<Map<String,BoundWithPart>> promise = Promise.promise();
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

  /**
   * Bound-with parts not in incoming: Future deletes.
   */
  private static List<Future<Response>> getDeleteBoundWithPartFutures(
      Map<String, BoundWithPart> existingParts,
      Map<String, BoundWithContent> incomingParts,
      Context vertxContext,
      Map<String,String> okapiHeaders) {

    List<Future<Response>> deleteFutures = new ArrayList<>();
    BoundWithPartService service = new BoundWithPartService(vertxContext, okapiHeaders);
    for (var part : existingParts.entrySet()) {
      if (!incomingParts.containsKey(part.getKey())) {
        deleteFutures.add(service.delete(part.getValue().getId()));
      }
    }
    return deleteFutures;
  }

  /**
   * Bound-with parts not existing: future creates.
   */
  private static List<Future<Response>> getCreateBoundWithPartFutures(
      String itemId,
      Map<String, BoundWithContent> incomingParts,
      Map<String, BoundWithPart> existingParts,
      Map<String, String> okapiHeaders,
      Context vertxContext) {

    List<Future<Response>> createFutures = new ArrayList<>();
    BoundWithPartService service = new BoundWithPartService(vertxContext, okapiHeaders);
    for (String holdingsId : incomingParts.keySet()) {
      if (!existingParts.containsKey(holdingsId)) {
        BoundWithPart part =
          new BoundWithPart()
            .withItemId(itemId)
            .withHoldingsRecordId(holdingsId)
            .withMetadata(new Metadata().withCreatedDate(new Date()).withUpdatedDate(new Date()));
        createFutures.add(service.create(part));
      }
    }
    return createFutures;
  }

  /**
   * Checks referential integrity for all involved records.
   */
  Future<Void> validate(BoundWith requestEntity, Context vertxContext, Map<String,String> okapiHeaders) {
    Errors errors = new Errors();
    ItemRepository items = new ItemRepository(vertxContext, okapiHeaders);
    return items.getById(requestEntity.getItemId())
        .compose(item -> {
          if (item == null) {
            addError(errors, "item.not-found", "Item not found.", "itemId", requestEntity.getItemId());
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
              addError(errors, "holding.not-found", "Holdings record not found.", "holdingsRecordId", id);
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
}

