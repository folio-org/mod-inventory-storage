package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.Items;
import org.folio.rest.jaxrs.model.MaterialType;
import org.folio.rest.jaxrs.model.Mtype;
import org.folio.rest.jaxrs.resource.ItemStorageResource;
import org.folio.rest.jaxrs.resource.MaterialTypeResource.DeleteMaterialTypeByMaterialtypeIdResponse;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.tools.utils.TenantTool;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;

public class ItemStorageAPI implements ItemStorageResource {

  // Has to be lowercase because raml-module-builder uses case sensitive
  // lower case headers
  public static final String ITEM_TABLE = "item";
  private static final String TENANT_HEADER = "x-okapi-tenant";
  private static final String BLANK_TENANT_MESSAGE = "Tenant Must Be Provided";
  private static final Logger log = LoggerFactory.getLogger(ItemStorageAPI.class);
  private final Messages messages = Messages.getInstance();

  @Validate
  @Override
  public void getItemStorageItems(
    @DefaultValue("0") @Min(0L) @Max(1000L) int offset,
    @DefaultValue("10") @Min(1L) @Max(100L) int limit,
    String query,
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext)
    throws Exception {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    if (blankTenantId(tenantId)) {
      badRequestResult(asyncResultHandler, BLANK_TENANT_MESSAGE);

      return;
    }

    try {
      vertxContext.runOnContext(v -> {
        try {
          PostgresClient postgresClient = PostgresClient.getInstance(
            vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

          String[] fieldList = {"*"};

          CQL2PgJSON cql2pgJson = new CQL2PgJSON("item.jsonb");
          CQLWrapper cql = new CQLWrapper(cql2pgJson, query)
            .setLimit(new Limit(limit))
            .setOffset(new Offset(offset));

          postgresClient.get("item", Item.class, fieldList, cql, true, false,
            reply -> {
              try {

                if(reply.succeeded()) {
                  List<Item> items = (List<Item>) reply.result()[0];

                  Items itemList = new Items();
                  itemList.setItems(items);
                  itemList.setTotalRecords((Integer) reply.result()[1]);

                  asyncResultHandler.handle(Future.succeededFuture(
                    ItemStorageResource.GetItemStorageItemsResponse.
                      withJsonOK(itemList)));
                }
                else {
                  asyncResultHandler.handle(Future.succeededFuture(
                    ItemStorageResource.GetItemStorageItemsResponse.
                      withPlainInternalServerError(reply.cause().getMessage())));
                }
              } catch (Exception e) {
                if(e.getCause() != null && e.getCause().getClass().getSimpleName().contains("CQLParseException")) {
                  asyncResultHandler.handle(Future.succeededFuture(
                    GetItemStorageItemsResponse.withPlainBadRequest(
                      "CQL Parsing Error for '" + query + "': " + e.getLocalizedMessage())));
                }
                else {
                  asyncResultHandler.handle(Future.succeededFuture(
                    ItemStorageResource.GetItemStorageItemsResponse.
                      withPlainInternalServerError("Error")));
                }
              }
            });
        }
        catch (IllegalStateException e) {
          asyncResultHandler.handle(Future.succeededFuture(
            GetItemStorageItemsResponse.withPlainInternalServerError(
              "CQL State Error for '" + query + "': " + e.getLocalizedMessage())));
        }
        catch (Exception e) {
          if(e.getCause() != null && e.getCause().getClass().getSimpleName().contains("CQLParseException")) {
            asyncResultHandler.handle(Future.succeededFuture(
              GetItemStorageItemsResponse.withPlainBadRequest(
              "CQL Parsing Error for '" + query + "': " + e.getLocalizedMessage())));
          } else {
            asyncResultHandler.handle(Future.succeededFuture(
              ItemStorageResource.GetItemStorageItemsResponse.
                withPlainInternalServerError("Error")));
          }
        }
      });
    } catch (Exception e) {
      if(e.getCause() != null && e.getCause().getClass().getSimpleName().contains("CQLParseException")) {
        asyncResultHandler.handle(Future.succeededFuture(
          GetItemStorageItemsResponse.withPlainBadRequest(
            "CQL Parsing Error for '" + query + "': " + e.getLocalizedMessage())));
      } else {
        asyncResultHandler.handle(Future.succeededFuture(
          ItemStorageResource.GetItemStorageItemsResponse.
            withPlainInternalServerError("Error")));
      }
    }
  }

  /**
   *
   * @param vertx
   * @param tenantId
   * @param item
   * @param handler
   * @throws Exception
   */
  private void getMT(Vertx vertx, String tenantId, Item item, Handler<AsyncResult<Integer>> handler) throws Exception{
    Mtype mtype = new Mtype();
    MaterialType mt = item.getMaterialType();
    if(mt == null){
      //allow null material types so that they can be added after a record is created
      handler.handle(io.vertx.core.Future.succeededFuture(1));
    }else{
      mtype.setName(item.getMaterialType().getName());
      /** check if the material type name exists, if not, can not add it to the item **/
      PostgresClient.getInstance(vertx, tenantId).get(
        MaterialTypeAPI.MATERIAL_TYPE_TABLE, mtype, new String[]{"_id"}, true, false, 0, 1, check -> {
          if(check.succeeded()){
            List<Mtype> mtypeList0 = (List<Mtype>) check.result()[0];
            if(mtypeList0.size() == 0){
              handler.handle(io.vertx.core.Future.succeededFuture(0));
            }
            else{
              handler.handle(io.vertx.core.Future.succeededFuture(1));
            }
          }
          else{
            log.error(check.cause().getLocalizedMessage(), check.cause());
            handler.handle(io.vertx.core.Future.succeededFuture(-1));
          }
      });
    }
  }
  @Validate
  @Override
  public void postItemStorageItems(
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Item entity,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext)
    throws Exception {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    if (blankTenantId(tenantId)) {
      badRequestResult(asyncResultHandler, BLANK_TENANT_MESSAGE);

      return;
    }

    try {
      PostgresClient postgresClient =
        PostgresClient.getInstance(
          vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

      if(entity.getId() == null) {
        entity.setId(UUID.randomUUID().toString());
      }

      vertxContext.runOnContext(v -> {
        try {
          getMT(vertxContext.owner(), tenantId, entity, replyHandler -> {
              int res = replyHandler.result();
              if(res == 0){
                String message = "Can not add " + entity.getMaterialType().getName() + ". Material type not found";
                log.error(message);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteMaterialTypeByMaterialtypeIdResponse
                  .withPlainNotFound(message)));
              }
              else if(res == -1){
                asyncResultHandler.handle(Future.succeededFuture(
                  ItemStorageResource.PostItemStorageItemsResponse
                    .withPlainInternalServerError("")));
              }
              else{
                try {
                  postgresClient.save("item", entity.getId(), entity,
                    reply -> {
                      try {
                        if(reply.succeeded()) {
                          OutStream stream = new OutStream();
                          stream.setData(entity);

                          asyncResultHandler.handle(
                            Future.succeededFuture(
                              ItemStorageResource.PostItemStorageItemsResponse
                                .withJsonCreated(reply.result(), stream)));
                        }
                        else {
                          String message = reply.cause().getMessage();

                          if(message.contains("invalid input syntax for uuid")) {
                            asyncResultHandler.handle(
                              Future.succeededFuture(
                                ItemStorageResource.PostItemStorageItemsResponse
                                  .withPlainBadRequest(
                                    "ID and instance ID must both be a UUID")));
                          }
                          else {
                            asyncResultHandler.handle(
                              Future.succeededFuture(
                                ItemStorageResource.PostItemStorageItemsResponse
                                  .withPlainInternalServerError(
                                    reply.cause().getMessage())));
                          }
                        }
                      } catch (Exception e) {
                        asyncResultHandler.handle(
                          Future.succeededFuture(
                            ItemStorageResource.PostItemStorageItemsResponse
                              .withPlainInternalServerError(e.getMessage())));
                      }
                    });
                } catch (Exception e) {
                  asyncResultHandler.handle(Future.succeededFuture(
                    ItemStorageResource.PostItemStorageItemsResponse
                      .withPlainInternalServerError(e.getMessage())));
                }
              }
          });
        } catch (Exception e) {
          asyncResultHandler.handle(Future.succeededFuture(
            ItemStorageResource.PostItemStorageItemsResponse
              .withPlainInternalServerError(e.getMessage())));
        }
      });
      }
      catch (Exception e) {
        asyncResultHandler.handle(Future.succeededFuture(
          ItemStorageResource.PostItemStorageItemsResponse
            .withPlainInternalServerError(e.getMessage())));
      }
  }
  @Validate
  @Override
  public void getItemStorageItemsByItemId(
    @PathParam("itemId") @NotNull String itemId,
    @QueryParam("lang") @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    java.util.Map<String, String> okapiHeaders,
    io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>> asyncResultHandler,
    Context vertxContext)
    throws Exception {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    if (blankTenantId(tenantId)) {
      badRequestResult(asyncResultHandler, BLANK_TENANT_MESSAGE);

      return;
    }

    Criteria a = new Criteria();

    a.addField("'id'");
    a.setOperation("=");
    a.setValue(itemId);

    Criterion criterion = new Criterion(a);

    try {
      PostgresClient postgresClient = PostgresClient.getInstance(
        vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

      vertxContext.runOnContext(v -> {
        try {
          postgresClient.get("item", Item.class, criterion, true, false,
            reply -> {
              try {
                if(reply.succeeded()) {
                  List<Item> itemList = (List<Item>) reply.result()[0];
                  if (itemList.size() == 1) {
                    Item item = itemList.get(0);

                    asyncResultHandler.handle(
                      Future.succeededFuture(
                        ItemStorageResource.GetItemStorageItemsByItemIdResponse.
                          withJsonOK(item)));
                  } else {
                    asyncResultHandler.handle(
                      Future.succeededFuture(
                        ItemStorageResource.GetItemStorageItemsByItemIdResponse.
                          withPlainNotFound("Not Found")));
                  }
                }
                else {
                  Future.succeededFuture(
                    ItemStorageResource.GetItemStorageItemsByItemIdResponse
                      .withPlainInternalServerError(
                        reply.cause().getMessage()));
                }
              } catch (Exception e) {
                asyncResultHandler.handle(Future.succeededFuture(
                  ItemStorageResource.GetItemStorageItemsByItemIdResponse.
                    withPlainInternalServerError(e.getMessage())));
              }
            });
        } catch (Exception e) {
          asyncResultHandler.handle(Future.succeededFuture(
            ItemStorageResource.GetItemStorageItemsByItemIdResponse.
              withPlainInternalServerError(e.getMessage())));
        }
      });
    } catch (Exception e) {
      asyncResultHandler.handle(Future.succeededFuture(
        ItemStorageResource.GetItemStorageItemsByItemIdResponse.
          withPlainInternalServerError(e.getMessage())));
    }
  }
  @Validate
  @Override
  public void deleteItemStorageItems(
    @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext)
    throws Exception {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    if (blankTenantId(tenantId)) {
      badRequestResult(asyncResultHandler, BLANK_TENANT_MESSAGE);

      return;
    }

    try {
      vertxContext.runOnContext(v -> {
        PostgresClient postgresClient = PostgresClient.getInstance(
          vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

        postgresClient.mutate(String.format("TRUNCATE TABLE %s_%s.item",
          tenantId, "inventory_storage"),
          reply -> {
            if (reply.succeeded()) {
              asyncResultHandler.handle(Future.succeededFuture(
                ItemStorageResource.DeleteItemStorageItemsResponse.noContent()
                  .build()));
            } else {
              asyncResultHandler.handle(Future.succeededFuture(
                ItemStorageResource.DeleteItemStorageItemsResponse.
                  withPlainInternalServerError(reply.cause().getMessage())));
            }
          });
      });
    }
    catch(Exception e) {
      asyncResultHandler.handle(Future.succeededFuture(
        ItemStorageResource.DeleteItemStorageItemsResponse.
          withPlainInternalServerError(e.getMessage())));
    }
  }

  @Validate
  @Override
  public void putItemStorageItemsByItemId(
    @PathParam("itemId") @NotNull String itemId,
    @QueryParam("lang") @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    Item entity,
    java.util.Map<String, String> okapiHeaders,
    io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>> asyncResultHandler,
    Context vertxContext)
    throws Exception {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    if (blankTenantId(tenantId)) {
      badRequestResult(asyncResultHandler, BLANK_TENANT_MESSAGE);

      return;
    }

    try {
      PostgresClient postgresClient =
        PostgresClient.getInstance(
          vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

      Criteria a = new Criteria();

      a.addField("'id'");
      a.setOperation("=");
      a.setValue(itemId);

      Criterion criterion = new Criterion(a);

      vertxContext.runOnContext(v -> {
        try {
          getMT(vertxContext.owner(), tenantId, entity, replyHandler -> {
              int res = replyHandler.result();
              if(res == 0){
                String message = "Can not add " + entity.getMaterialType().getName() + ". Material type not found";
                log.error(message);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DeleteMaterialTypeByMaterialtypeIdResponse
                  .withPlainNotFound(message)));
              }
              else if(res == -1){
                asyncResultHandler.handle(Future.succeededFuture(
                  ItemStorageResource.PostItemStorageItemsResponse
                    .withPlainInternalServerError("")));
              }
              else{
                try {
                  postgresClient.get("item", Item.class, criterion, true, false,
                    reply -> {
                      if(reply.succeeded()) {
                        List<Item> itemList = (List<Item>) reply.result()[0];
                        if (itemList.size() == 1) {
                          try {
                            postgresClient.update("item", entity, criterion, true,
                              update -> {
                                try {
                                  if (update.succeeded()) {
                                    OutStream stream = new OutStream();
                                    stream.setData(entity);

                                    asyncResultHandler.handle(
                                      Future.succeededFuture(
                                        PutItemStorageItemsByItemIdResponse
                                          .withNoContent()));
                                  } else {
                                    asyncResultHandler.handle(
                                      Future.succeededFuture(
                                        PutItemStorageItemsByItemIdResponse
                                          .withPlainInternalServerError(
                                            update.cause().getMessage())));
                                  }
                                } catch (Exception e) {
                                  asyncResultHandler.handle(
                                    Future.succeededFuture(
                                      PostItemStorageItemsResponse
                                        .withPlainInternalServerError(e.getMessage())));
                                }
                              });
                          } catch (Exception e) {
                            asyncResultHandler.handle(Future.succeededFuture(
                              PutItemStorageItemsByItemIdResponse
                                .withPlainInternalServerError(e.getMessage())));
                          }
                        } else {
                          try {
                            postgresClient.save("item", entity.getId(), entity,
                              save -> {
                                try {
                                  if (save.succeeded()) {
                                    OutStream stream = new OutStream();
                                    stream.setData(entity);

                                    asyncResultHandler.handle(
                                      Future.succeededFuture(
                                        PutItemStorageItemsByItemIdResponse
                                          .withNoContent()));
                                  } else {
                                    asyncResultHandler.handle(
                                      Future.succeededFuture(
                                        PutItemStorageItemsByItemIdResponse
                                          .withPlainInternalServerError(
                                            save.cause().getMessage())));
                                  }

                                } catch (Exception e) {
                                  asyncResultHandler.handle(
                                    Future.succeededFuture(
                                      PostItemStorageItemsResponse
                                        .withPlainInternalServerError(e.getMessage())));
                                }
                              });
                          } catch (Exception e) {
                            asyncResultHandler.handle(Future.succeededFuture(
                              PutItemStorageItemsByItemIdResponse
                                .withPlainInternalServerError(e.getMessage())));
                          }
                        }
                      } else {
                        asyncResultHandler.handle(Future.succeededFuture(
                          PutItemStorageItemsByItemIdResponse
                            .withPlainInternalServerError(reply.cause().getMessage())));
                      }
                    });
                } catch (Exception e) {
                  asyncResultHandler.handle(Future.succeededFuture(
                    ItemStorageResource.PostItemStorageItemsResponse
                      .withPlainInternalServerError(e.getMessage())));
                }
              }
          });
        } catch (Exception e) {
          asyncResultHandler.handle(Future.succeededFuture(
            ItemStorageResource.PostItemStorageItemsResponse
              .withPlainInternalServerError(e.getMessage())));
        }
      });
    } catch (Exception e) {
      asyncResultHandler.handle(Future.succeededFuture(
        ItemStorageResource.PostItemStorageItemsResponse
          .withPlainInternalServerError(e.getMessage())));
    }
  }
  @Validate
  @Override
  public void deleteItemStorageItemsByItemId(
    @PathParam("itemId") @NotNull String itemId,
    @QueryParam("lang") @DefaultValue("en") @Pattern(regexp = "[a-zA-Z]{2}") String lang,
    java.util.Map<String, String> okapiHeaders,
    io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>> asyncResultHandler,
    Context vertxContext)
    throws Exception {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    if (blankTenantId(tenantId)) {
      badRequestResult(asyncResultHandler, BLANK_TENANT_MESSAGE);

      return;
    }

    try {
      PostgresClient postgresClient =
        PostgresClient.getInstance(
          vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

      Criteria a = new Criteria();

      a.addField("'id'");
      a.setOperation("=");
      a.setValue(itemId);

      Criterion criterion = new Criterion(a);

      vertxContext.runOnContext(v -> {
        try {
          postgresClient.delete("item", criterion,
            reply -> {
              if(reply.succeeded()) {
                asyncResultHandler.handle(
                  Future.succeededFuture(
                    DeleteItemStorageItemsByItemIdResponse
                      .withNoContent()));
              }
              else {
                asyncResultHandler.handle(Future.succeededFuture(
                  ItemStorageResource.DeleteItemStorageItemsByItemIdResponse
                    .withPlainInternalServerError("Error")));
              }
            });
        } catch (Exception e) {
          asyncResultHandler.handle(Future.succeededFuture(
            ItemStorageResource.DeleteItemStorageItemsByItemIdResponse
              .withPlainInternalServerError("Error")));
        }
      });
    } catch (Exception e) {
      asyncResultHandler.handle(Future.succeededFuture(
        ItemStorageResource.DeleteItemStorageItemsByItemIdResponse
          .withPlainInternalServerError("Error")));
    }
  }

  private void badRequestResult(
    Handler<AsyncResult<Response>> asyncResultHandler, String message) {
    asyncResultHandler.handle(Future.succeededFuture(
      GetItemStorageItemsResponse.withPlainBadRequest(message)));
  }

  private boolean blankTenantId(String tenantId) {
    return tenantId == null || tenantId == "" || tenantId == "folio_shared";
  }

}
