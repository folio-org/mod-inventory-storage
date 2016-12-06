
package org.folio.rest.jaxrs.resource;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import io.vertx.core.Context;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.Items;

@Path("item-storage")
public interface ItemStorageResource {


    /**
     * Retrieve a list of item items.
     * 
     * @param vertxContext
     *      The Vertx Context Object <code>io.vertx.core.Context</code> 
     * @param asyncResultHandler
     *     A <code>Handler<AsyncResult<Response>>></code> handler {@link io.vertx.core.Handler} which must be called as follows - Note the 'GetPatronsResponse' should be replaced with '[nameOfYourFunction]Response': (example only) <code>asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetPatronsResponse.withJsonOK( new ObjectMapper().readValue(reply.result().body().toString(), Patron.class))));</code> in the final callback (most internal callback) of the function.
     * @param lang
     *     Requested language. Optional. [lang=en]
     *     
     */
    @GET
    @Path("item")
    @Produces({
        "application/json",
        "text/plain"
    })
    @Validate
    void getItemStorageItem(
        @QueryParam("lang")
        @DefaultValue("en")
        @Pattern(regexp = "[a-zA-Z]{2}")
        String lang, java.util.Map<String, String>okapiHeaders, io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>>asyncResultHandler, Context vertxContext)
        throws Exception
    ;

    /**
     * Create a new item item.
     * 
     * @param vertxContext
     *      The Vertx Context Object <code>io.vertx.core.Context</code> 
     * @param asyncResultHandler
     *     A <code>Handler<AsyncResult<Response>>></code> handler {@link io.vertx.core.Handler} which must be called as follows - Note the 'GetPatronsResponse' should be replaced with '[nameOfYourFunction]Response': (example only) <code>asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetPatronsResponse.withJsonOK( new ObjectMapper().readValue(reply.result().body().toString(), Patron.class))));</code> in the final callback (most internal callback) of the function.
     * @param lang
     *     Requested language. Optional. [lang=en]
     *     
     * @param entity
     *      e.g. {
     *       "id": "cd28da0f-a3e4-465c-82f1-acade4e8e170",
     *       "instance_id": "cd28da0f-a3e4-465c-82f1-acade4e8e170",
     *       "title": "Uprooted",
     *       "barcode": "645398607547"
     *     }
     *     
     */
    @POST
    @Path("item")
    @Consumes("application/json")
    @Produces({
        "application/json",
        "text/plain"
    })
    @Validate
    void postItemStorageItem(
        @QueryParam("lang")
        @DefaultValue("en")
        @Pattern(regexp = "[a-zA-Z]{2}")
        String lang, Item entity, java.util.Map<String, String>okapiHeaders, io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>>asyncResultHandler, Context vertxContext)
        throws Exception
    ;

    /**
     * 
     * @param itemId
     *     
     * @param vertxContext
     *      The Vertx Context Object <code>io.vertx.core.Context</code> 
     * @param asyncResultHandler
     *     A <code>Handler<AsyncResult<Response>>></code> handler {@link io.vertx.core.Handler} which must be called as follows - Note the 'GetPatronsResponse' should be replaced with '[nameOfYourFunction]Response': (example only) <code>asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetPatronsResponse.withJsonOK( new ObjectMapper().readValue(reply.result().body().toString(), Patron.class))));</code> in the final callback (most internal callback) of the function.
     * @param lang
     *     Requested language. Optional. [lang=en]
     *     
     */
    @POST
    @Path("item/{itemId}")
    @Validate
    void postItemStorageItemByItemId(
        @PathParam("itemId")
        @NotNull
        String itemId,
        @QueryParam("lang")
        @DefaultValue("en")
        @Pattern(regexp = "[a-zA-Z]{2}")
        String lang, java.util.Map<String, String>okapiHeaders, io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>>asyncResultHandler, Context vertxContext)
        throws Exception
    ;

    /**
     * Retrieve item item with given {itemId}
     * 
     * 
     * @param itemId
     *     
     * @param vertxContext
     *      The Vertx Context Object <code>io.vertx.core.Context</code> 
     * @param asyncResultHandler
     *     A <code>Handler<AsyncResult<Response>>></code> handler {@link io.vertx.core.Handler} which must be called as follows - Note the 'GetPatronsResponse' should be replaced with '[nameOfYourFunction]Response': (example only) <code>asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetPatronsResponse.withJsonOK( new ObjectMapper().readValue(reply.result().body().toString(), Patron.class))));</code> in the final callback (most internal callback) of the function.
     * @param lang
     *     Requested language. Optional. [lang=en]
     *     
     */
    @GET
    @Path("item/{itemId}")
    @Produces({
        "application/json",
        "text/plain"
    })
    @Validate
    void getItemStorageItemByItemId(
        @PathParam("itemId")
        @NotNull
        String itemId,
        @QueryParam("lang")
        @DefaultValue("en")
        @Pattern(regexp = "[a-zA-Z]{2}")
        String lang, java.util.Map<String, String>okapiHeaders, io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>>asyncResultHandler, Context vertxContext)
        throws Exception
    ;

    /**
     * Delete item item with given {itemId}
     * 
     * 
     * @param itemId
     *     
     * @param vertxContext
     *      The Vertx Context Object <code>io.vertx.core.Context</code> 
     * @param asyncResultHandler
     *     A <code>Handler<AsyncResult<Response>>></code> handler {@link io.vertx.core.Handler} which must be called as follows - Note the 'GetPatronsResponse' should be replaced with '[nameOfYourFunction]Response': (example only) <code>asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetPatronsResponse.withJsonOK( new ObjectMapper().readValue(reply.result().body().toString(), Patron.class))));</code> in the final callback (most internal callback) of the function.
     * @param lang
     *     Requested language. Optional. [lang=en]
     *     
     */
    @DELETE
    @Path("item/{itemId}")
    @Produces({
        "text/plain"
    })
    @Validate
    void deleteItemStorageItemByItemId(
        @PathParam("itemId")
        @NotNull
        String itemId,
        @QueryParam("lang")
        @DefaultValue("en")
        @Pattern(regexp = "[a-zA-Z]{2}")
        String lang, java.util.Map<String, String>okapiHeaders, io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>>asyncResultHandler, Context vertxContext)
        throws Exception
    ;

    /**
     * Update item item with given {itemId}
     * 
     * 
     * @param itemId
     *     
     * @param vertxContext
     *      The Vertx Context Object <code>io.vertx.core.Context</code> 
     * @param asyncResultHandler
     *     A <code>Handler<AsyncResult<Response>>></code> handler {@link io.vertx.core.Handler} which must be called as follows - Note the 'GetPatronsResponse' should be replaced with '[nameOfYourFunction]Response': (example only) <code>asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetPatronsResponse.withJsonOK( new ObjectMapper().readValue(reply.result().body().toString(), Patron.class))));</code> in the final callback (most internal callback) of the function.
     * @param lang
     *     Requested language. Optional. [lang=en]
     *     
     * @param entity
     *      e.g. {
     *       "id": "cd28da0f-a3e4-465c-82f1-acade4e8e170",
     *       "instance_id": "cd28da0f-a3e4-465c-82f1-acade4e8e170",
     *       "title": "Uprooted",
     *       "barcode": "645398607547"
     *     }
     *     
     */
    @PUT
    @Path("item/{itemId}")
    @Consumes("application/json")
    @Produces({
        "text/plain"
    })
    @Validate
    void putItemStorageItemByItemId(
        @PathParam("itemId")
        @NotNull
        String itemId,
        @QueryParam("lang")
        @DefaultValue("en")
        @Pattern(regexp = "[a-zA-Z]{2}")
        String lang, Item entity, java.util.Map<String, String>okapiHeaders, io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>>asyncResultHandler, Context vertxContext)
        throws Exception
    ;

    public class DeleteItemStorageItemByItemIdResponse
        extends org.folio.rest.jaxrs.resource.support.ResponseWrapper
    {


        private DeleteItemStorageItemByItemIdResponse(Response delegate) {
            super(delegate);
        }

        /**
         * Item deleted successfully
         * 
         */
        public static ItemStorageResource.DeleteItemStorageItemByItemIdResponse withNoContent() {
            Response.ResponseBuilder responseBuilder = Response.status(204);
            return new ItemStorageResource.DeleteItemStorageItemByItemIdResponse(responseBuilder.build());
        }

        /**
         * Item with a given ID not found e.g. "item not found"
         * 
         * 
         * @param entity
         *     "item not found"
         *     
         */
        public static ItemStorageResource.DeleteItemStorageItemByItemIdResponse withPlainNotFound(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(404).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new ItemStorageResource.DeleteItemStorageItemByItemIdResponse(responseBuilder.build());
        }

        /**
         * Bad request, e.g. malformed request body or query parameter. Details of the error (e.g. name of the parameter or line/character number with malformed data) provided in the response. e.g. "unable to delete item -- constraint violation"
         * 
         * 
         * @param entity
         *     "unable to delete item -- constraint violation"
         *     
         */
        public static ItemStorageResource.DeleteItemStorageItemByItemIdResponse withPlainBadRequest(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(400).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new ItemStorageResource.DeleteItemStorageItemByItemIdResponse(responseBuilder.build());
        }

        /**
         * Internal server error, e.g. due to misconfiguration e.g. Internal server error, contact administrator
         * 
         * @param entity
         *     Internal server error, contact administrator
         */
        public static ItemStorageResource.DeleteItemStorageItemByItemIdResponse withPlainInternalServerError(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(500).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new ItemStorageResource.DeleteItemStorageItemByItemIdResponse(responseBuilder.build());
        }

    }

    public class GetItemStorageItemByItemIdResponse
        extends org.folio.rest.jaxrs.resource.support.ResponseWrapper
    {


        private GetItemStorageItemByItemIdResponse(Response delegate) {
            super(delegate);
        }

        /**
         * Returns item with a given ID e.g. {
         *   "id": "cd28da0f-a3e4-465c-82f1-acade4e8e170",
         *   "instance_id": "cd28da0f-a3e4-465c-82f1-acade4e8e170",
         *   "title": "Uprooted",
         *   "barcode": "645398607547"
         * }
         * 
         * 
         * @param entity
         *     {
         *       "id": "cd28da0f-a3e4-465c-82f1-acade4e8e170",
         *       "instance_id": "cd28da0f-a3e4-465c-82f1-acade4e8e170",
         *       "title": "Uprooted",
         *       "barcode": "645398607547"
         *     }
         *     
         */
        public static ItemStorageResource.GetItemStorageItemByItemIdResponse withJsonOK(Item entity) {
            Response.ResponseBuilder responseBuilder = Response.status(200).header("Content-Type", "application/json");
            responseBuilder.entity(entity);
            return new ItemStorageResource.GetItemStorageItemByItemIdResponse(responseBuilder.build());
        }

        /**
         * Item with a given ID not found e.g. "item not found"
         * 
         * 
         * @param entity
         *     "item not found"
         *     
         */
        public static ItemStorageResource.GetItemStorageItemByItemIdResponse withPlainNotFound(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(404).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new ItemStorageResource.GetItemStorageItemByItemIdResponse(responseBuilder.build());
        }

        /**
         * Internal server error, e.g. due to misconfiguration e.g. internal server error, contact administrator
         * 
         * @param entity
         *     internal server error, contact administrator
         */
        public static ItemStorageResource.GetItemStorageItemByItemIdResponse withPlainInternalServerError(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(500).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new ItemStorageResource.GetItemStorageItemByItemIdResponse(responseBuilder.build());
        }

    }

    public class GetItemStorageItemResponse
        extends org.folio.rest.jaxrs.resource.support.ResponseWrapper
    {


        private GetItemStorageItemResponse(Response delegate) {
            super(delegate);
        }

        /**
         * Returns a list of item items e.g. [
         *   {
         *     "id": "cd28da0f-a3e4-465c-82f1-acade4e8e170",
         *     "instance_id": "cd28da0f-a3e4-465c-82f1-acade4e8e170",
         *     "title": "Nod",
         *     "barcode": "456743454532"
         *   },
         *   {
         *     "id": "cd28da0f-a3e4-465c-82f1-acade4e8e170",
         *     "instance_id": "cd28da0f-a3e4-465c-82f1-acade4e8e170",
         *     "title": "Uprooted",
         *     "barcode": "645398607547"
         *   }
         * ]
         * 
         * 
         * @param entity
         *     [
         *       {
         *         "id": "cd28da0f-a3e4-465c-82f1-acade4e8e170",
         *         "instance_id": "cd28da0f-a3e4-465c-82f1-acade4e8e170",
         *         "title": "Nod",
         *         "barcode": "456743454532"
         *       },
         *       {
         *         "id": "cd28da0f-a3e4-465c-82f1-acade4e8e170",
         *         "instance_id": "cd28da0f-a3e4-465c-82f1-acade4e8e170",
         *         "title": "Uprooted",
         *         "barcode": "645398607547"
         *       }
         *     ]
         *     
         */
        public static ItemStorageResource.GetItemStorageItemResponse withJsonOK(Items entity) {
            Response.ResponseBuilder responseBuilder = Response.status(200).header("Content-Type", "application/json");
            responseBuilder.entity(entity);
            return new ItemStorageResource.GetItemStorageItemResponse(responseBuilder.build());
        }

        /**
         * Bad request, e.g. malformed request body or query parameter. Details of the error (e.g. name of the parameter or line/character number with malformed data) provided in the response. e.g. unable to list item -- malformed parameter 'query', syntax error at column 6
         * 
         * @param entity
         *     unable to list item -- malformed parameter 'query', syntax error at column 6
         */
        public static ItemStorageResource.GetItemStorageItemResponse withPlainBadRequest(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(400).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new ItemStorageResource.GetItemStorageItemResponse(responseBuilder.build());
        }

        /**
         * Internal server error, e.g. due to misconfiguration e.g. internal server error, contact administrator
         * 
         * @param entity
         *     internal server error, contact administrator
         */
        public static ItemStorageResource.GetItemStorageItemResponse withPlainInternalServerError(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(500).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new ItemStorageResource.GetItemStorageItemResponse(responseBuilder.build());
        }

    }

    public class PostItemStorageItemByItemIdResponse
        extends org.folio.rest.jaxrs.resource.support.ResponseWrapper
    {


        private PostItemStorageItemByItemIdResponse(Response delegate) {
            super(delegate);
        }

    }

    public class PostItemStorageItemResponse
        extends org.folio.rest.jaxrs.resource.support.ResponseWrapper
    {


        private PostItemStorageItemResponse(Response delegate) {
            super(delegate);
        }

        /**
         * Returns a newly created item, with server-controlled fields like 'id' populated e.g. {
         *   "id": "cd28da0f-a3e4-465c-82f1-acade4e8e170",
         *   "instance_id": "cd28da0f-a3e4-465c-82f1-acade4e8e170",
         *   "title": "Uprooted",
         *   "barcode": "645398607547"
         * }
         * 
         * 
         * @param location
         *     URI to the created item item
         * @param entity
         *     {
         *       "id": "cd28da0f-a3e4-465c-82f1-acade4e8e170",
         *       "instance_id": "cd28da0f-a3e4-465c-82f1-acade4e8e170",
         *       "title": "Uprooted",
         *       "barcode": "645398607547"
         *     }
         *     
         */
        public static ItemStorageResource.PostItemStorageItemResponse withJsonCreated(String location, StreamingOutput entity) {
            Response.ResponseBuilder responseBuilder = Response.status(201).header("Content-Type", "application/json").header("Location", location);
            responseBuilder.entity(entity);
            return new ItemStorageResource.PostItemStorageItemResponse(responseBuilder.build());
        }

        /**
         * Bad request, e.g. malformed request body or query parameter. Details of the error (e.g. name of the parameter or line/character number with malformed data) provided in the response. e.g. "unable to add item -- malformed JSON at 13:3"
         * 
         * 
         * @param entity
         *     "unable to add item -- malformed JSON at 13:3"
         *     
         */
        public static ItemStorageResource.PostItemStorageItemResponse withPlainBadRequest(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(400).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new ItemStorageResource.PostItemStorageItemResponse(responseBuilder.build());
        }

        /**
         * Internal server error, e.g. due to misconfiguration e.g. Internal server error, contact administrator
         * 
         * @param entity
         *     Internal server error, contact administrator
         */
        public static ItemStorageResource.PostItemStorageItemResponse withPlainInternalServerError(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(500).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new ItemStorageResource.PostItemStorageItemResponse(responseBuilder.build());
        }

    }

    public class PutItemStorageItemByItemIdResponse
        extends org.folio.rest.jaxrs.resource.support.ResponseWrapper
    {


        private PutItemStorageItemByItemIdResponse(Response delegate) {
            super(delegate);
        }

        /**
         * Item successfully updated
         * 
         */
        public static ItemStorageResource.PutItemStorageItemByItemIdResponse withNoContent() {
            Response.ResponseBuilder responseBuilder = Response.status(204);
            return new ItemStorageResource.PutItemStorageItemByItemIdResponse(responseBuilder.build());
        }

        /**
         * Item with a given ID not found e.g. "item not found"
         * 
         * 
         * @param entity
         *     "item not found"
         *     
         */
        public static ItemStorageResource.PutItemStorageItemByItemIdResponse withPlainNotFound(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(404).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new ItemStorageResource.PutItemStorageItemByItemIdResponse(responseBuilder.build());
        }

        /**
         * Bad request, e.g. malformed request body or query parameter. Details of the error (e.g. name of the parameter or line/character number with malformed data) provided in the response. e.g. "unable to update item -- malformed JSON at 13:4"
         * 
         * 
         * @param entity
         *     "unable to update item -- malformed JSON at 13:4"
         *     
         */
        public static ItemStorageResource.PutItemStorageItemByItemIdResponse withPlainBadRequest(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(400).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new ItemStorageResource.PutItemStorageItemByItemIdResponse(responseBuilder.build());
        }

        /**
         * Internal server error, e.g. due to misconfiguration e.g. internal server error, contact administrator
         * 
         * @param entity
         *     internal server error, contact administrator
         */
        public static ItemStorageResource.PutItemStorageItemByItemIdResponse withPlainInternalServerError(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(500).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new ItemStorageResource.PutItemStorageItemByItemIdResponse(responseBuilder.build());
        }

    }

}
