package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.Items;
import org.folio.rest.jaxrs.resource.ItemStorageResource;

import javax.validation.constraints.Pattern;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ItemStorageAPI implements ItemStorageResource {
  @Override
  public void getItemStorageItem(@DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    List<Item> items = new ArrayList<Item>();
    Items itemList = new Items();
    itemList.setItems(items);
    itemList.setTotalRecords(items.size());
    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
      ItemStorageResource.GetItemStorageItemResponse.withJsonOK(itemList)));
  }

  @Override
  public void postItemStorageItem(@DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang, Item entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(ItemStorageResource.GetItemStorageItemResponse
      .withPlainInternalServerError("Not implemented")));
  }
}
