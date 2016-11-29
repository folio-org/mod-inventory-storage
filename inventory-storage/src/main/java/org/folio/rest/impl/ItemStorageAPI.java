package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.Items;
import org.folio.rest.jaxrs.resource.ItemStorageResource;
import org.folio.rest.tools.utils.OutStream;

import javax.validation.constraints.Pattern;
import javax.validation.constraints.NotNull;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.core.Response;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Criteria;

public class ItemStorageAPI implements ItemStorageResource {

  @Override
  public void getItemStorageItem(@DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
      Criteria a = new Criteria();
      Criterion criterion = new Criterion(a);
	try {
	    System.out.println("getting... Items");
	    PostgresClient postgresClient = PostgresClient.getInstance(vertxContext.owner());

	    vertxContext.runOnContext(v -> {

		    try {
			postgresClient.get("test.item", Item.class, criterion , false,
					   reply -> {
					       try {
						   List<Item> items = (List<Item>)reply.result()[0];
						   Items itemList = new Items();
						   itemList.setItems(items);
						   itemList.setTotalRecords(items.size());
						   asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
														      ItemStorageResource.GetItemStorageItemResponse.
														      withJsonOK(itemList)));
						   	  
					       } catch (Exception e) {
						   e.printStackTrace();
						   asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
														  ItemStorageResource.GetItemStorageItemResponse.
														  withPlainInternalServerError("Error")));
					       }
					   });
		    } catch (Exception e) {
			e.printStackTrace();
			asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
										       ItemStorageResource.GetItemStorageItemResponse.
										       withPlainInternalServerError("Error")));
		    }
		});
	} catch (Exception e) {
	    e.printStackTrace();
	    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
									   ItemStorageResource.GetItemStorageItemResponse.
									   withPlainInternalServerError("Error")));
	}
  }

  @Override
  public void postItemStorageItem(@DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang, Item entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
      try {
	  System.out.println("sending... Item");
	  PostgresClient postgresClient = PostgresClient.getInstance(vertxContext.owner());

	  vertxContext.runOnContext(v -> {

		  try {
		      postgresClient.save("test.item", entity,
					  reply -> {
					      try {
						  Item p = entity;
						  OutStream stream = new OutStream();
						  stream.setData(p);
						  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
														 ItemStorageResource.PostItemStorageItemResponse
														 .withJsonCreated(reply.result(),stream)));
					      } catch (Exception e) {
						  e.printStackTrace();
						  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
														 ItemStorageResource.PostItemStorageItemResponse
														 .withPlainInternalServerError("Error")));
					      }
					  });
		  } catch (Exception e) {
		      e.printStackTrace();
		      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
										     ItemStorageResource.PostItemStorageItemResponse
										     .withPlainInternalServerError("Error")));
		  }
	      });
      } catch (Exception e) {
	  e.printStackTrace();
	  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
									 ItemStorageResource.PostItemStorageItemResponse
									 .withPlainInternalServerError("Error")));
      }
  }

  @Override
   public void postItemStorageItemByItemId(
        @PathParam("itemId")
        @NotNull
        String itemId,
        @QueryParam("lang")
        @DefaultValue("en")
        @Pattern(regexp = "[a-zA-Z]{2}")
        String lang, java.util.Map<String, String>okapiHeaders, io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>>asyncResultHandler, Context vertxContext)
        throws Exception
    {

    }

    @Override
    public void getItemStorageItemByItemId(
        @PathParam("itemId")
        @NotNull
        String itemId,
        @QueryParam("lang")
        @DefaultValue("en")
        @Pattern(regexp = "[a-zA-Z]{2}")
        String lang, java.util.Map<String, String>okapiHeaders, io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>>asyncResultHandler, Context vertxContext)
        throws Exception
    {	
	Criteria a = new Criteria();
	a.addField("'id'");
	a.setOperation("=");
	a.setValue(itemId);
	Criterion criterion = new Criterion(a);
	try {
	    System.out.println("getting... Item");
	    PostgresClient postgresClient = PostgresClient.getInstance(vertxContext.owner());

	    vertxContext.runOnContext(v -> {

		    try {
			postgresClient.get("test.item", Item.class, criterion , false,
					   reply -> {
					       try {
						   List<Item> itemList = (List<Item>)reply.result()[0];
						   if(itemList.size() == 1) {
						       Item item = itemList.get(0);
						       item.setId(itemId);
						       asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
														      ItemStorageResource.GetItemStorageItemByItemIdResponse.
														      withJsonOK(item)));
						   }
						   else {
						       throw new Exception(itemList.size() + " results returned");
						   }
		  
					       } catch (Exception e) {
						   e.printStackTrace();
						   asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
														  ItemStorageResource.GetItemStorageItemByItemIdResponse.
														  withPlainInternalServerError("Error")));
					       }
					   });
		    } catch (Exception e) {
			e.printStackTrace();
			asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
										       ItemStorageResource.GetItemStorageItemByItemIdResponse.
										       withPlainInternalServerError("Error")));
		    }
		});
	} catch (Exception e) {
	    e.printStackTrace();
	    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
									   ItemStorageResource.GetItemStorageItemByItemIdResponse.
									   withPlainInternalServerError("Error")));
	}
    }

    @Override
    public void putItemStorageItemByItemId(
        @PathParam("itemId")
        @NotNull
        String itemId,
        @QueryParam("lang")
        @DefaultValue("en")
        @Pattern(regexp = "[a-zA-Z]{2}")
        String lang, Item entity, java.util.Map<String, String>okapiHeaders, io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>>asyncResultHandler, Context vertxContext)
        throws Exception
    {

    }

    @Override
    public void deleteItemStorageItemByItemId(
        @PathParam("itemId")
        @NotNull
        String itemId,
        @QueryParam("lang")
        @DefaultValue("en")
        @Pattern(regexp = "[a-zA-Z]{2}")
        String lang, java.util.Map<String, String>okapiHeaders, io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>>asyncResultHandler, Context vertxContext)
        throws Exception
    {

    }
    
}
