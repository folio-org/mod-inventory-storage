package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.persist.BoundWithRepository;
import org.folio.persist.HoldingsRepository;
import org.folio.persist.ItemRepository;
import org.folio.rest.jaxrs.model.BoundWith;
import org.folio.rest.jaxrs.model.BoundWithContent;
import org.folio.rest.jaxrs.model.BoundWithPart;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.services.instance.BoundWithPartService;

import static io.vertx.core.Future.succeededFuture;


public class BoundWithAPI implements org.folio.rest.jaxrs.resource.InventoryStorageBoundWiths {

  @Override
  public void putInventoryStorageBoundWiths(BoundWith entity,
                                            Map<String, String> okapiHeaders,
                                            Handler<AsyncResult<Response>> asyncResultHandler,
                                            Context vertxContext) {

    Validation check = new Validation(vertxContext, okapiHeaders);
    check.isValid(entity).onComplete(
      validation -> {
        if (validation.result()) {
          Map<String, BoundWithContent> incomingParts = getIncomingParts(entity);
          getExistingParts(entity, vertxContext, okapiHeaders).onComplete(
            existing -> {
              Map<String,BoundWithPart> existingParts = existing.result();
              CompositeFuture.all(
                getCreateBoundWithPartFutures(
                  entity.getItemId(), incomingParts, existingParts,
                  okapiHeaders,  vertxContext)).onComplete(
                    createResults -> CompositeFuture.all(
                      getDeleteBoundWithPartFutures(
                        existingParts, incomingParts,
                        vertxContext, okapiHeaders)).onComplete(
                          deleteResults -> asyncResultHandler.handle(
                            succeededFuture(
                              PostInventoryStorageBoundWithsResponse.noContent().build()))));
            });
        } else {
          asyncResultHandler.handle(succeededFuture(
            PostInventoryStorageBoundWithsResponse.respond400WithTextPlain(check.message)));
        }
      });
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
  private List<Future> getDeleteBoundWithPartFutures(
    Map<String, BoundWithPart> existingParts,
    Map<String, BoundWithContent> incomingParts,
    Context vertxContext,
    Map<String,String> okapiHeaders) {
    List<Future> deleteFutures = new ArrayList<>();
    BoundWithPartService service = new BoundWithPartService(vertxContext, okapiHeaders);
    for (String holdingsId : existingParts.keySet()) {
      if (!incomingParts.containsKey(holdingsId)) {
        deleteFutures.add(service.delete(existingParts.get(holdingsId).getId()));
      }
    }
    return deleteFutures;
  }

  /**
   * Bound-with parts not existing: future creates.
   */
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
    private String message = "";
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
          message = "Item " + requestEntity.getItemId() + " not found." + System.lineSeparator();
        }
        HoldingsRepository holdings = new HoldingsRepository(vertxContext, okapiHeaders);
        List<Future> holdingsFutures = new ArrayList<>();
        for (BoundWithContent entry : requestEntity.getBoundWithContents()) {
          holdingsFutures.add(holdings.getById(entry.getHoldingsRecordId()));
        }
        CompositeFuture.all(holdingsFutures).onComplete( holdingsFound -> {
          for (int i = 0; i < holdingsFound.result().size(); i++ ) {
            if (holdingsFound.result().resultAt(i) == null) {
              valid = false;
              message = message.concat("Holdings record "
                + requestEntity.getBoundWithContents().get(i).getHoldingsRecordId()
                + " not found." + System.lineSeparator());
            }
          }
          promise.complete(valid);
        });
      });
      return promise.future();
    }
  }


  @Override
  public void getInventoryStorageBoundWiths(String lang, Map<String, String> okapiHeaders,
                                            Handler<AsyncResult<Response>> asyncResultHandler,
                                            Context vertxContext) {}

  @Override
  public void postInventoryStorageBoundWiths(String lang, BoundWith entity,
                                             Map<String, String> okapiHeaders,
                                             Handler<AsyncResult<Response>> asyncResultHandler,
                                             Context vertxContext) {}

}

