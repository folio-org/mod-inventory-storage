package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.persist.BoundWithRepository;
import org.folio.persist.HoldingsRepository;
import org.folio.persist.ItemRepository;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.BoundWith;
import org.folio.rest.jaxrs.model.BoundWithContent;
import org.folio.rest.jaxrs.model.BoundWithPart;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.services.instance.BoundWithPartService;

import static io.vertx.core.Future.succeededFuture;


public class BoundWithAPI implements org.folio.rest.jaxrs.resource.InventoryStorageBoundWiths {

  @Validate
  @Override
  public void putInventoryStorageBoundWiths(BoundWith entity,
                                            Map<String, String> okapiHeaders,
                                            Handler<AsyncResult<Response>> asyncResultHandler,
                                            Context vertxContext) {

    Validation check = new Validation(vertxContext, okapiHeaders);
    check.isValid(entity).onComplete(
      validation -> {
        if (Boolean.TRUE.equals(validation.result())) {
          Map<String, BoundWithContent> incomingParts = getIncomingParts(entity);
          getExistingParts(entity, vertxContext, okapiHeaders).onComplete(
            existing -> {
              Map<String,BoundWithPart> existingParts = existing.result();
              CompositeFuture.all(
                getCreateBoundWithPartFutures(
                  entity.getItemId(), incomingParts, existingParts,
                  okapiHeaders,  vertxContext)).onComplete(
                    createResults -> {
                      if (createResults.succeeded()) {
                        CompositeFuture.all(
                          getDeleteBoundWithPartFutures(
                            existingParts, incomingParts,
                            vertxContext, okapiHeaders)).onComplete(
                              deleteResults -> {
                                if (deleteResults.succeeded()) {
                                  asyncResultHandler.handle(
                                    succeededFuture(
                                      PutInventoryStorageBoundWithsResponse.respond204()));
                                } else {
                                  respond422(asyncResultHandler, deleteResults);
                                }
                              });
                      } else {
                        respond422(asyncResultHandler, createResults);
                      }
                    });
            });
        } else {
          respond422(asyncResultHandler,check.message);
        }
      });
  }

  private void respond422(Handler<AsyncResult<Response>> handler,
                           AsyncResult<CompositeFuture> failed) {
    JsonObject message = new JsonObject();
    message.put("errors", (failed.cause() != null ? failed.cause().getMessage() : ""));
    respond422(handler, message);
  }

  private void respond422(Handler<AsyncResult<Response>> handler, JsonObject json) {
    handler.handle(
      succeededFuture(PutInventoryStorageBoundWithsResponse.respond422WithApplicationJson(json))
    );
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
  @SuppressWarnings("rawtypes")
  private List<Future> getDeleteBoundWithPartFutures(
    Map<String, BoundWithPart> existingParts,
    Map<String, BoundWithContent> incomingParts,
    Context vertxContext,
    Map<String,String> okapiHeaders) {
    List<Future> deleteFutures = new ArrayList<>();
    BoundWithPartService service = new BoundWithPartService(vertxContext, okapiHeaders);
    for (Map.Entry part : existingParts.entrySet()) {
      if (!incomingParts.containsKey(part.getKey().toString())) {
        deleteFutures.add(service.delete(((BoundWithPart) part.getValue()).getId()));
      }
    }
    return deleteFutures;
  }

  /**
   * Bound-with parts not existing: future creates.
   */
  @SuppressWarnings("rawtypes")
  private static List<Future> getCreateBoundWithPartFutures(
    String itemId,
    Map<String, BoundWithContent> incomingParts,
    Map<String, BoundWithPart> existingParts,
    Map<String, String> okapiHeaders,
    Context vertxContext) {
    List<Future> createFutures = new ArrayList<>();
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
  static class Validation {

    private boolean valid = true;
    private final JsonObject message = new JsonObject();
    private final Context vertxContext;
    private final Map<String,String> okapiHeaders;

    public Validation (Context vertxContext, Map<String,String> okapiHeaders) {
      this.vertxContext = vertxContext;
      this.okapiHeaders = okapiHeaders;
    }

    Future<Boolean> isValid(BoundWith requestEntity) {
      Promise<Boolean> promise = Promise.promise();
      ItemRepository items = new ItemRepository(vertxContext, okapiHeaders);
      items.getById(requestEntity.getItemId()).onComplete(found -> {
        if (found.result() == null) {
          valid = false;
          message.put("item", "Item " + requestEntity.getItemId() + " not found.");
        }
        HoldingsRepository holdings = new HoldingsRepository(vertxContext, okapiHeaders);
        @SuppressWarnings("rawtypes")
        List<Future> holdingsFutures = new ArrayList<>();
        for (BoundWithContent entry : requestEntity.getBoundWithContents()) {
          holdingsFutures.add(holdings.getById(entry.getHoldingsRecordId()));
        }
        CompositeFuture.all(holdingsFutures).onComplete( holdingsFound -> {
          JsonArray holdingsNotFound = new JsonArray();
          for (int i = 0; i < holdingsFound.result().size(); i++ ) {
            if (holdingsFound.result().resultAt(i) == null) {
              valid = false;
              holdingsNotFound.add("Holdings record "
                + requestEntity.getBoundWithContents().get(i).getHoldingsRecordId()
                + " not found.");
            }
          }
          if (holdingsNotFound.size()>0) {
            message.put("holdings", holdingsNotFound);
          }
          promise.complete(valid);
        });
      });
      return promise.future();
    }
  }
}

