package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.jaxrs.resource.InventoryStorageBoundWiths.PutInventoryStorageBoundWithsResponse.respond204;
import static org.folio.rest.support.ResponseHandlerUtil.handleFailure;
import static org.folio.rest.tools.utils.TenantTool.tenantId;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.ws.rs.core.Response;
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
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.services.instance.BoundWithPartService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings("java:S6813")
public class BoundWithApi implements org.folio.rest.jaxrs.resource.InventoryStorageBoundWiths {

  @Autowired
  private BoundWithPartService service;
  @Autowired
  private ItemRepository itemRepository;
  @Autowired
  private HoldingsRepository holdingsRepository;
  @Autowired
  private BoundWithRepository boundWithRepository;

  public BoundWithApi() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Validate
  @Override
  public void putInventoryStorageBoundWiths(BoundWith entity,
                                            Map<String, String> headers,
                                            Handler<AsyncResult<Response>> asyncResultHandler,
                                            Context vertxContext) {

    Map<String, BoundWithContent> incomingParts = getIncomingParts(entity);
    AtomicReference<Map<String, BoundWithPart>> existingParts = new AtomicReference<>();
    AtomicReference<Item> boundWithItem = new AtomicReference<>();

    validate(entity, headers)
      .compose(x -> getExistingParts(entity, headers))
      .onSuccess(existingParts::set)
      .compose(x -> getMainPart(entity, headers))
      .onSuccess(boundWithItem::set)
      .compose(x -> {
        var futures = getCreateBoundWithPartFutures(
          entity.getItemId(), boundWithItem.get().getHoldingsRecordId(),
          incomingParts, existingParts.get(), headers);
        return Future.all(futures);
      })
      .compose(x -> {
        var futures = getDeleteBoundWithPartFutures(
          boundWithItem.get().getHoldingsRecordId(),
          existingParts.get(), incomingParts, headers);
        return Future.all(futures);
      })
      .onSuccess(x -> asyncResultHandler.handle(succeededFuture(respond204())))
      .onFailure(handleFailure(asyncResultHandler));
  }

  /**
   * Checks referential integrity for all involved records.
   */
  Future<Void> validate(BoundWith requestEntity, Map<String, String> headers) {
    Errors errors = new Errors();
    return itemRepository.getById(requestEntity.getItemId(), tenantId(headers))
      .compose(item -> {
        if (item == null) {
          addError(errors,
            "item.not-found", "Item not found.", "itemId", requestEntity.getItemId());
        }
        return validateHoldings(requestEntity, headers);
      })
      .compose(holdingsFound -> validateHoldingsResults(holdingsFound, requestEntity, errors));
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
   * Bound-with parts not in incoming: Future deletes.
   */
  private List<Future<?>> getDeleteBoundWithPartFutures(String mainHoldingsRecordId,
                                                               Map<String, BoundWithPart> existingParts,
                                                               Map<String, BoundWithContent> incomingParts,
                                                               Map<String, String> headers) {

    List<Future<?>> deleteFutures = existingParts.entrySet().stream()
      .filter(part -> isForDeletion(mainHoldingsRecordId, incomingParts, part))
      .<Future<?>>map(part -> service.delete(part.getValue().getId(), headers))
      .collect(Collectors.toList());
    if (existingParts.containsKey(mainHoldingsRecordId)
        && (incomingParts.size() == 1 && incomingParts.containsKey(mainHoldingsRecordId) || incomingParts.isEmpty())) {
      deleteFutures.add(service.delete(existingParts.get(mainHoldingsRecordId).getId(), headers));
    }
    return deleteFutures;
  }

  private boolean isForDeletion(String mainHoldingsRecordId,
                                Map<String, BoundWithContent> incomingParts,
                                Map.Entry<String, BoundWithPart> part) {
    return !incomingParts.containsKey(part.getKey())
           && !part.getKey().equals(mainHoldingsRecordId);
  }

  /**
   * Bound-with parts not existing: future creates.
   */
  private List<Future<?>> getCreateBoundWithPartFutures(String itemId,
                                                               String mainHoldingsId,
                                                               Map<String, BoundWithContent> incomingParts,
                                                               Map<String, BoundWithPart> existingParts,
                                                               Map<String, String> headers) {

    List<Future<?>> createFutures = new ArrayList<>();

    createIncomingParts(itemId, mainHoldingsId, incomingParts, existingParts, headers, createFutures);
    createMainHoldingsPart(itemId, mainHoldingsId, incomingParts, existingParts, headers, createFutures);

    return createFutures;
  }

  private void createIncomingParts(String itemId, String mainHoldingsId,
                                   Map<String, BoundWithContent> incomingParts,
                                   Map<String, BoundWithPart> existingParts,
                                   Map<String, String> headers,
                                   List<Future<?>> createFutures) {
    if (!incomingParts.containsKey(mainHoldingsId) || incomingParts.size() > 1) {
      for (String holdingsId : incomingParts.keySet()) {
        if (!existingParts.containsKey(holdingsId)) {
          BoundWithPart part = new BoundWithPart()
            .withItemId(itemId)
            .withHoldingsRecordId(holdingsId)
            .withMetadata(new Metadata().withCreatedDate(new Date()).withUpdatedDate(new Date()));
          createFutures.add(service.create(part, headers));
        }
      }
    }
  }

  private void createMainHoldingsPart(String itemId, String mainHoldingsId,
                                      Map<String, BoundWithContent> incomingParts,
                                      Map<String, BoundWithPart> existingParts,
                                      Map<String, String> headers,
                                      List<Future<?>> createFutures) {
    // Add main holdings ID, but only if it doesn't exist already,
    // wasn't provided in the request (and thus added above),
    // and would not become the only part.
    if (!existingParts.containsKey(mainHoldingsId)
        && !incomingParts.containsKey(mainHoldingsId)
        && !incomingParts.isEmpty()) {
      BoundWithPart part = new BoundWithPart()
        .withItemId(itemId)
        .withHoldingsRecordId(mainHoldingsId)
        .withMetadata(new Metadata().withCreatedDate(new Date()).withUpdatedDate(new Date()));
      createFutures.add(service.create(part, headers));
    }
  }

  private Future<CompositeFuture> validateHoldings(BoundWith requestEntity,
                                                   Map<String, String> headers) {
    var holdingsFutures = requestEntity.getBoundWithContents().stream()
      .map(entry -> holdingsRepository.getById(entry.getHoldingsRecordId(), tenantId(headers)))
      .toList();
    return Future.all(holdingsFutures);
  }

  private Future<Void> validateHoldingsResults(CompositeFuture holdingsFound, BoundWith requestEntity, Errors errors) {
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
  private Future<Item> getMainPart(BoundWith entity, Map<String, String> headers) {
    Promise<Item> promise = Promise.promise();
    return itemRepository.getById(entity.getItemId(), tenantId(headers))
      .onComplete(item -> promise.complete(item.result()));
  }

  /**
   * Existing BoundWithPart entities mapped by holdings record ID.
   */
  private Future<Map<String, BoundWithPart>> getExistingParts(BoundWith entity, Map<String, String> headers) {
    Promise<Map<String, BoundWithPart>> promise = Promise.promise();
    Map<String, BoundWithPart> existingParts = new HashMap<>();
    Criterion criterion = new Criterion(new Criteria()
      .setJSONB(false).addField("itemId").setOperation("=")
      .setVal(entity.getItemId()));

    boundWithRepository.get(criterion, tenantId(headers))
      .onComplete(boundWithParts -> {
        for (BoundWithPart boundWith : boundWithParts.result()) {
          existingParts.put(boundWith.getHoldingsRecordId(), boundWith);
        }
        promise.complete(existingParts);
      });
    return promise.future();
  }
}

