
package org.folio.rest.jaxrs.resource;

import javax.validation.constraints.Pattern;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import io.vertx.core.Context;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Item;
import org.folio.rest.jaxrs.model.Items;

@Path("item_storage")
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

}
