
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
    @Path("items")
    @Validate
    void deleteItemStorageItems(
        @QueryParam("lang")
        @DefaultValue("en")
        @Pattern(regexp = "[a-zA-Z]{2}")
        String lang, java.util.Map<String, String>okapiHeaders, io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>>asyncResultHandler, Context vertxContext)
        throws Exception
    ;

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
    @Path("items")
    @Produces({
        "application/json",
        "text/plain"
    })
    @Validate
    void getItemStorageItems(
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
    @Path("items")
    @Consumes("application/json")
    @Produces({
        "application/json",
        "text/plain"
    })
    @Validate
    void postItemStorageItems(
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
    @Path("items/{itemId}")
    @Validate
    void postItemStorageItemsByItemId(
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
    @Path("items/{itemId}")
    @Produces({
        "application/json",
        "text/plain"
    })
    @Validate
    void getItemStorageItemsByItemId(
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
    @Path("items/{itemId}")
    @Produces({
        "text/plain"
    })
    @Validate
    void deleteItemStorageItemsByItemId(
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
    @Path("items/{itemId}")
    @Consumes("application/json")
    @Produces({
        "text/plain"
    })
    @Validate
    void putItemStorageItemsByItemId(
        @PathParam("itemId")
        @NotNull
        String itemId,
        @QueryParam("lang")
        @DefaultValue("en")
        @Pattern(regexp = "[a-zA-Z]{2}")
        String lang, Item entity, java.util.Map<String, String>okapiHeaders, io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>>asyncResultHandler, Context vertxContext)
        throws Exception
    ;

    public class DeleteItemStorageItemsByItemIdResponse
        extends org.folio.rest.jaxrs.resource.support.ResponseWrapper
    {


        private DeleteItemStorageItemsByItemIdResponse(Response delegate) {
            super(delegate);
        }

        /**
         * Item deleted successfully
         * 
         */
        public static ItemStorageResource.DeleteItemStorageItemsByItemIdResponse withNoContent() {
            Response.ResponseBuilder responseBuilder = Response.status(204);
            return new ItemStorageResource.DeleteItemStorageItemsByItemIdResponse(responseBuilder.build());
        }

        /**
         * Item with a given ID not found e.g. "item not found"
         * 
         * 
         * @param entity
         *     "item not found"
         *     
         */
        public static ItemStorageResource.DeleteItemStorageItemsByItemIdResponse withPlainNotFound(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(404).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new ItemStorageResource.DeleteItemStorageItemsByItemIdResponse(responseBuilder.build());
        }

        /**
         * Bad request, e.g. malformed request body or query parameter. Details of the error (e.g. name of the parameter or line/character number with malformed data) provided in the response. e.g. "unable to delete item -- constraint violation"
         * 
         * 
         * @param entity
         *     "unable to delete item -- constraint violation"
         *     
         */
        public static ItemStorageResource.DeleteItemStorageItemsByItemIdResponse withPlainBadRequest(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(400).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new ItemStorageResource.DeleteItemStorageItemsByItemIdResponse(responseBuilder.build());
        }

        /**
         * Internal server error, e.g. due to misconfiguration e.g. Internal server error, contact administrator
         * 
         * @param entity
         *     Internal server error, contact administrator
         */
        public static ItemStorageResource.DeleteItemStorageItemsByItemIdResponse withPlainInternalServerError(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(500).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new ItemStorageResource.DeleteItemStorageItemsByItemIdResponse(responseBuilder.build());
        }

    }

    public class DeleteItemStorageItemsResponse
        extends org.folio.rest.jaxrs.resource.support.ResponseWrapper
    {


        private DeleteItemStorageItemsResponse(Response delegate) {
            super(delegate);
        }

        /**
         * All items deleted
         * 
         */
        public static ItemStorageResource.DeleteItemStorageItemsResponse withNoContent() {
            Response.ResponseBuilder responseBuilder = Response.status(204);
            return new ItemStorageResource.DeleteItemStorageItemsResponse(responseBuilder.build());
        }

    }

    public class GetItemStorageItemsByItemIdResponse
        extends org.folio.rest.jaxrs.resource.support.ResponseWrapper
    {


        private GetItemStorageItemsByItemIdResponse(Response delegate) {
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
        public static ItemStorageResource.GetItemStorageItemsByItemIdResponse withJsonOK(Item entity) {
            Response.ResponseBuilder responseBuilder = Response.status(200).header("Content-Type", "application/json");
            responseBuilder.entity(entity);
            return new ItemStorageResource.GetItemStorageItemsByItemIdResponse(responseBuilder.build());
        }

        /**
         * Item with a given ID not found e.g. "item not found"
         * 
         * 
         * @param entity
         *     "item not found"
         *     
         */
        public static ItemStorageResource.GetItemStorageItemsByItemIdResponse withPlainNotFound(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(404).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new ItemStorageResource.GetItemStorageItemsByItemIdResponse(responseBuilder.build());
        }

        /**
         * Internal server error, e.g. due to misconfiguration e.g. internal server error, contact administrator
         * 
         * @param entity
         *     internal server error, contact administrator
         */
        public static ItemStorageResource.GetItemStorageItemsByItemIdResponse withPlainInternalServerError(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(500).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new ItemStorageResource.GetItemStorageItemsByItemIdResponse(responseBuilder.build());
        }

    }

    public class GetItemStorageItemsResponse
        extends org.folio.rest.jaxrs.resource.support.ResponseWrapper
    {


        private GetItemStorageItemsResponse(Response delegate) {
            super(delegate);
        }

        /**
         * Returns a list of item items e.g. {
         *   "items": [
         *     {
         *       "id": "cd28da0f-a3e4-465c-82f1-acade4e8e170",
         *       "instance_id": "cd28da0f-a3e4-465c-82f1-acade4e8e170",
         *       "title": "Nod",
         *       "barcode": "456743454532"
         *     },
         *     {
         *       "id": "cd28da0f-a3e4-465c-82f1-acade4e8e170",
         *       "instance_id": "cd28da0f-a3e4-465c-82f1-acade4e8e170",
         *       "title": "Uprooted",
         *       "barcode": "645398607547"
         *     }
         *   ],
         *   "total_records": 2
         * }
         * 
         * 
         * @param entity
         *     {
         *       "items": [
         *         {
         *           "id": "cd28da0f-a3e4-465c-82f1-acade4e8e170",
         *           "instance_id": "cd28da0f-a3e4-465c-82f1-acade4e8e170",
         *           "title": "Nod",
         *           "barcode": "456743454532"
         *         },
         *         {
         *           "id": "cd28da0f-a3e4-465c-82f1-acade4e8e170",
         *           "instance_id": "cd28da0f-a3e4-465c-82f1-acade4e8e170",
         *           "title": "Uprooted",
         *           "barcode": "645398607547"
         *         }
         *       ],
         *       "total_records": 2
         *     }
         *     
         */
        public static ItemStorageResource.GetItemStorageItemsResponse withJsonOK(Items entity) {
            Response.ResponseBuilder responseBuilder = Response.status(200).header("Content-Type", "application/json");
            responseBuilder.entity(entity);
            return new ItemStorageResource.GetItemStorageItemsResponse(responseBuilder.build());
        }

        /**
         * Bad request, e.g. malformed request body or query parameter. Details of the error (e.g. name of the parameter or line/character number with malformed data) provided in the response. e.g. unable to list items -- malformed parameter 'query', syntax error at column 6
         * 
         * @param entity
         *     unable to list items -- malformed parameter 'query', syntax error at column 6
         */
        public static ItemStorageResource.GetItemStorageItemsResponse withPlainBadRequest(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(400).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new ItemStorageResource.GetItemStorageItemsResponse(responseBuilder.build());
        }

        /**
         * Internal server error, e.g. due to misconfiguration e.g. internal server error, contact administrator
         * 
         * @param entity
         *     internal server error, contact administrator
         */
        public static ItemStorageResource.GetItemStorageItemsResponse withPlainInternalServerError(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(500).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new ItemStorageResource.GetItemStorageItemsResponse(responseBuilder.build());
        }

    }

    public class PostItemStorageItemsByItemIdResponse
        extends org.folio.rest.jaxrs.resource.support.ResponseWrapper
    {


        private PostItemStorageItemsByItemIdResponse(Response delegate) {
            super(delegate);
        }

    }

    public class PostItemStorageItemsResponse
        extends org.folio.rest.jaxrs.resource.support.ResponseWrapper
    {


        private PostItemStorageItemsResponse(Response delegate) {
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
        public static ItemStorageResource.PostItemStorageItemsResponse withJsonCreated(String location, StreamingOutput entity) {
            Response.ResponseBuilder responseBuilder = Response.status(201).header("Content-Type", "application/json").header("Location", location);
            responseBuilder.entity(entity);
            return new ItemStorageResource.PostItemStorageItemsResponse(responseBuilder.build());
        }

        /**
         * Bad request, e.g. malformed request body or query parameter. Details of the error (e.g. name of the parameter or line/character number with malformed data) provided in the response. e.g. "unable to add item -- malformed JSON at 13:3"
         * 
         * 
         * @param entity
         *     "unable to add item -- malformed JSON at 13:3"
         *     
         */
        public static ItemStorageResource.PostItemStorageItemsResponse withPlainBadRequest(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(400).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new ItemStorageResource.PostItemStorageItemsResponse(responseBuilder.build());
        }

        /**
         * Internal server error, e.g. due to misconfiguration e.g. Internal server error, contact administrator
         * 
         * @param entity
         *     Internal server error, contact administrator
         */
        public static ItemStorageResource.PostItemStorageItemsResponse withPlainInternalServerError(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(500).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new ItemStorageResource.PostItemStorageItemsResponse(responseBuilder.build());
        }

    }

    public class PutItemStorageItemsByItemIdResponse
        extends org.folio.rest.jaxrs.resource.support.ResponseWrapper
    {


        private PutItemStorageItemsByItemIdResponse(Response delegate) {
            super(delegate);
        }

        /**
         * Item successfully updated
         * 
         */
        public static ItemStorageResource.PutItemStorageItemsByItemIdResponse withNoContent() {
            Response.ResponseBuilder responseBuilder = Response.status(204);
            return new ItemStorageResource.PutItemStorageItemsByItemIdResponse(responseBuilder.build());
        }

        /**
         * Item with a given ID not found e.g. "item not found"
         * 
         * 
         * @param entity
         *     "item not found"
         *     
         */
        public static ItemStorageResource.PutItemStorageItemsByItemIdResponse withPlainNotFound(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(404).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new ItemStorageResource.PutItemStorageItemsByItemIdResponse(responseBuilder.build());
        }

        /**
         * Bad request, e.g. malformed request body or query parameter. Details of the error (e.g. name of the parameter or line/character number with malformed data) provided in the response. e.g. "unable to update item -- malformed JSON at 13:4"
         * 
         * 
         * @param entity
         *     "unable to update item -- malformed JSON at 13:4"
         *     
         */
        public static ItemStorageResource.PutItemStorageItemsByItemIdResponse withPlainBadRequest(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(400).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new ItemStorageResource.PutItemStorageItemsByItemIdResponse(responseBuilder.build());
        }

        /**
         * Internal server error, e.g. due to misconfiguration e.g. internal server error, contact administrator
         * 
         * @param entity
         *     internal server error, contact administrator
         */
        public static ItemStorageResource.PutItemStorageItemsByItemIdResponse withPlainInternalServerError(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(500).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new ItemStorageResource.PutItemStorageItemsByItemIdResponse(responseBuilder.build());
        }

    }

}
