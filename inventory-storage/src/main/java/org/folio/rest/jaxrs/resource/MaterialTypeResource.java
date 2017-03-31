
package org.folio.rest.jaxrs.resource;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
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
import org.folio.rest.jaxrs.model.Mtype;
import org.folio.rest.jaxrs.model.Mtypes;


/**
 * Collection of material-type items.
 * 
 */
@Path("material-type")
public interface MaterialTypeResource {


    /**
     * Return a list of material types
     * 
     * @param offset
     *     Skip over a number of elements by specifying an offset value for the query e.g. 0
     * @param query
     *     JSON array [{"field1","value1","operator1"},{"field2","value2","operator2"},...,{"fieldN","valueN","operatorN"}] with valid searchable fields
     *      e.g. name=aaa
     *     
     * @param limit
     *     Limit the number of elements returned in the response e.g. 10
     * @param vertxContext
     *      The Vertx Context Object <code>io.vertx.core.Context</code> 
     * @param asyncResultHandler
     *     A <code>Handler<AsyncResult<Response>>></code> handler {@link io.vertx.core.Handler} which must be called as follows - Note the 'GetPatronsResponse' should be replaced with '[nameOfYourFunction]Response': (example only) <code>asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetPatronsResponse.withJsonOK( new ObjectMapper().readValue(reply.result().body().toString(), Patron.class))));</code> in the final callback (most internal callback) of the function.
     * @param lang
     *     Requested language. Optional. [lang=en]
     *     
     */
    @GET
    @Produces({
        "application/json",
        "text/plain"
    })
    @Validate
    void getMaterialType(
        @QueryParam("query")
        String query,
        @QueryParam("offset")
        @DefaultValue("0")
        @Min(0L)
        @Max(1000L)
        int offset,
        @QueryParam("limit")
        @DefaultValue("10")
        @Min(1L)
        @Max(100L)
        int limit,
        @QueryParam("lang")
        @DefaultValue("en")
        @Pattern(regexp = "[a-zA-Z]{2}")
        String lang, java.util.Map<String, String>okapiHeaders, io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>>asyncResultHandler, Context vertxContext)
        throws Exception
    ;

    /**
     * Create a new material type
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
     *       "name": "book"
     *     }
     *     
     */
    @POST
    @Consumes("application/json")
    @Produces({
        "application/json",
        "text/plain"
    })
    @Validate
    void postMaterialType(
        @QueryParam("lang")
        @DefaultValue("en")
        @Pattern(regexp = "[a-zA-Z]{2}")
        String lang, Mtype entity, java.util.Map<String, String>okapiHeaders, io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>>asyncResultHandler, Context vertxContext)
        throws Exception
    ;

    /**
     * Retrieve material-type item with given {material-typeId}
     * 
     * 
     * @param vertxContext
     *      The Vertx Context Object <code>io.vertx.core.Context</code> 
     * @param materialtypeId
     *     
     * @param asyncResultHandler
     *     A <code>Handler<AsyncResult<Response>>></code> handler {@link io.vertx.core.Handler} which must be called as follows - Note the 'GetPatronsResponse' should be replaced with '[nameOfYourFunction]Response': (example only) <code>asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetPatronsResponse.withJsonOK( new ObjectMapper().readValue(reply.result().body().toString(), Patron.class))));</code> in the final callback (most internal callback) of the function.
     * @param lang
     *     Requested language. Optional. [lang=en]
     *     
     */
    @GET
    @Path("{materialtypeId}")
    @Produces({
        "application/json",
        "text/plain"
    })
    @Validate
    void getMaterialTypeByMaterialtypeId(
        @PathParam("materialtypeId")
        @NotNull
        String materialtypeId,
        @QueryParam("lang")
        @DefaultValue("en")
        @Pattern(regexp = "[a-zA-Z]{2}")
        String lang, java.util.Map<String, String>okapiHeaders, io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>>asyncResultHandler, Context vertxContext)
        throws Exception
    ;

    /**
     * Delete material-type item with given {material-typeId}
     * 
     * 
     * @param vertxContext
     *      The Vertx Context Object <code>io.vertx.core.Context</code> 
     * @param materialtypeId
     *     
     * @param asyncResultHandler
     *     A <code>Handler<AsyncResult<Response>>></code> handler {@link io.vertx.core.Handler} which must be called as follows - Note the 'GetPatronsResponse' should be replaced with '[nameOfYourFunction]Response': (example only) <code>asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetPatronsResponse.withJsonOK( new ObjectMapper().readValue(reply.result().body().toString(), Patron.class))));</code> in the final callback (most internal callback) of the function.
     * @param lang
     *     Requested language. Optional. [lang=en]
     *     
     */
    @DELETE
    @Path("{materialtypeId}")
    @Produces({
        "text/plain"
    })
    @Validate
    void deleteMaterialTypeByMaterialtypeId(
        @PathParam("materialtypeId")
        @NotNull
        String materialtypeId,
        @QueryParam("lang")
        @DefaultValue("en")
        @Pattern(regexp = "[a-zA-Z]{2}")
        String lang, java.util.Map<String, String>okapiHeaders, io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>>asyncResultHandler, Context vertxContext)
        throws Exception
    ;

    /**
     * Update material-type item with given {material-typeId}
     * 
     * 
     * @param vertxContext
     *      The Vertx Context Object <code>io.vertx.core.Context</code> 
     * @param materialtypeId
     *     
     * @param asyncResultHandler
     *     A <code>Handler<AsyncResult<Response>>></code> handler {@link io.vertx.core.Handler} which must be called as follows - Note the 'GetPatronsResponse' should be replaced with '[nameOfYourFunction]Response': (example only) <code>asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetPatronsResponse.withJsonOK( new ObjectMapper().readValue(reply.result().body().toString(), Patron.class))));</code> in the final callback (most internal callback) of the function.
     * @param lang
     *     Requested language. Optional. [lang=en]
     *     
     * @param entity
     *      e.g. {
     *       "name": "book"
     *     }
     *     
     */
    @PUT
    @Path("{materialtypeId}")
    @Consumes("application/json")
    @Produces({
        "text/plain"
    })
    @Validate
    void putMaterialTypeByMaterialtypeId(
        @PathParam("materialtypeId")
        @NotNull
        String materialtypeId,
        @QueryParam("lang")
        @DefaultValue("en")
        @Pattern(regexp = "[a-zA-Z]{2}")
        String lang, Mtype entity, java.util.Map<String, String>okapiHeaders, io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>>asyncResultHandler, Context vertxContext)
        throws Exception
    ;

    public class DeleteMaterialTypeByMaterialtypeIdResponse
        extends org.folio.rest.jaxrs.resource.support.ResponseWrapper
    {


        private DeleteMaterialTypeByMaterialtypeIdResponse(Response delegate) {
            super(delegate);
        }

        /**
         * Item deleted successfully
         * 
         */
        public static MaterialTypeResource.DeleteMaterialTypeByMaterialtypeIdResponse withNoContent() {
            Response.ResponseBuilder responseBuilder = Response.status(204);
            return new MaterialTypeResource.DeleteMaterialTypeByMaterialtypeIdResponse(responseBuilder.build());
        }

        /**
         * Item with a given ID not found e.g. "material-type not found"
         * 
         * 
         * @param entity
         *     "material-type not found"
         *     
         */
        public static MaterialTypeResource.DeleteMaterialTypeByMaterialtypeIdResponse withPlainNotFound(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(404).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new MaterialTypeResource.DeleteMaterialTypeByMaterialtypeIdResponse(responseBuilder.build());
        }

        /**
         * Bad request, e.g. malformed request body or query parameter. Details of the error (e.g. name of the parameter or line/character number with malformed data) provided in the response. e.g. "unable to delete material-type -- constraint violation"
         * 
         * 
         * @param entity
         *     "unable to delete material-type -- constraint violation"
         *     
         */
        public static MaterialTypeResource.DeleteMaterialTypeByMaterialtypeIdResponse withPlainBadRequest(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(400).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new MaterialTypeResource.DeleteMaterialTypeByMaterialtypeIdResponse(responseBuilder.build());
        }

        /**
         * Internal server error, e.g. due to misconfiguration e.g. Internal server error, contact administrator
         * 
         * @param entity
         *     Internal server error, contact administrator
         */
        public static MaterialTypeResource.DeleteMaterialTypeByMaterialtypeIdResponse withPlainInternalServerError(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(500).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new MaterialTypeResource.DeleteMaterialTypeByMaterialtypeIdResponse(responseBuilder.build());
        }

    }

    public class GetMaterialTypeByMaterialtypeIdResponse
        extends org.folio.rest.jaxrs.resource.support.ResponseWrapper
    {


        private GetMaterialTypeByMaterialtypeIdResponse(Response delegate) {
            super(delegate);
        }

        /**
         * Returns item with a given ID e.g. {
         *   "name": "book"
         * }
         * 
         * 
         * @param entity
         *     {
         *       "name": "book"
         *     }
         *     
         */
        public static MaterialTypeResource.GetMaterialTypeByMaterialtypeIdResponse withJsonOK(Mtype entity) {
            Response.ResponseBuilder responseBuilder = Response.status(200).header("Content-Type", "application/json");
            responseBuilder.entity(entity);
            return new MaterialTypeResource.GetMaterialTypeByMaterialtypeIdResponse(responseBuilder.build());
        }

        /**
         * Item with a given ID not found e.g. "material-type not found"
         * 
         * 
         * @param entity
         *     "material-type not found"
         *     
         */
        public static MaterialTypeResource.GetMaterialTypeByMaterialtypeIdResponse withPlainNotFound(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(404).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new MaterialTypeResource.GetMaterialTypeByMaterialtypeIdResponse(responseBuilder.build());
        }

        /**
         * Internal server error, e.g. due to misconfiguration e.g. internal server error, contact administrator
         * 
         * @param entity
         *     internal server error, contact administrator
         */
        public static MaterialTypeResource.GetMaterialTypeByMaterialtypeIdResponse withPlainInternalServerError(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(500).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new MaterialTypeResource.GetMaterialTypeByMaterialtypeIdResponse(responseBuilder.build());
        }

    }

    public class GetMaterialTypeResponse
        extends org.folio.rest.jaxrs.resource.support.ResponseWrapper
    {


        private GetMaterialTypeResponse(Response delegate) {
            super(delegate);
        }

        /**
         * Returns a list of material-type items e.g. {
         *   "mtypes": [
         *     {
         *       "name": "book"
         *     },
         *     {
         *       "name": "dvd"
         *     }
         *   ],
         *   "totalRecords": 2
         * }
         * 
         * 
         * @param entity
         *     {
         *       "mtypes": [
         *         {
         *           "name": "book"
         *         },
         *         {
         *           "name": "dvd"
         *         }
         *       ],
         *       "totalRecords": 2
         *     }
         *     
         */
        public static MaterialTypeResource.GetMaterialTypeResponse withJsonOK(Mtypes entity) {
            Response.ResponseBuilder responseBuilder = Response.status(200).header("Content-Type", "application/json");
            responseBuilder.entity(entity);
            return new MaterialTypeResource.GetMaterialTypeResponse(responseBuilder.build());
        }

        /**
         * Bad request, e.g. malformed request body or query parameter. Details of the error (e.g. name of the parameter or line/character number with malformed data) provided in the response. e.g. unable to list material-type -- malformed parameter 'query', syntax error at column 6
         * 
         * @param entity
         *     unable to list material-type -- malformed parameter 'query', syntax error at column 6
         */
        public static MaterialTypeResource.GetMaterialTypeResponse withPlainBadRequest(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(400).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new MaterialTypeResource.GetMaterialTypeResponse(responseBuilder.build());
        }

        /**
         * Not authorized to perform requested action e.g. unable to list material-type -- unauthorized
         * 
         * @param entity
         *     unable to list material-type -- unauthorized
         */
        public static MaterialTypeResource.GetMaterialTypeResponse withPlainUnauthorized(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(401).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new MaterialTypeResource.GetMaterialTypeResponse(responseBuilder.build());
        }

        /**
         * Internal server error, e.g. due to misconfiguration e.g. internal server error, contact administrator
         * 
         * @param entity
         *     internal server error, contact administrator
         */
        public static MaterialTypeResource.GetMaterialTypeResponse withPlainInternalServerError(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(500).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new MaterialTypeResource.GetMaterialTypeResponse(responseBuilder.build());
        }

    }

    public class PostMaterialTypeResponse
        extends org.folio.rest.jaxrs.resource.support.ResponseWrapper
    {


        private PostMaterialTypeResponse(Response delegate) {
            super(delegate);
        }

        /**
         * Returns a newly created item, with server-controlled fields like 'id' populated e.g. {
         *   "name": "book"
         * }
         * 
         * 
         * @param location
         *     URI to the created material-type item
         * @param entity
         *     {
         *       "name": "book"
         *     }
         *     
         */
        public static MaterialTypeResource.PostMaterialTypeResponse withJsonCreated(String location, StreamingOutput entity) {
            Response.ResponseBuilder responseBuilder = Response.status(201).header("Content-Type", "application/json").header("Location", location);
            responseBuilder.entity(entity);
            return new MaterialTypeResource.PostMaterialTypeResponse(responseBuilder.build());
        }

        /**
         * Bad request, e.g. malformed request body or query parameter. Details of the error (e.g. name of the parameter or line/character number with malformed data) provided in the response. e.g. "unable to add material-type -- malformed JSON at 13:3"
         * 
         * 
         * @param entity
         *     "unable to add material-type -- malformed JSON at 13:3"
         *     
         */
        public static MaterialTypeResource.PostMaterialTypeResponse withPlainBadRequest(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(400).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new MaterialTypeResource.PostMaterialTypeResponse(responseBuilder.build());
        }

        /**
         * Not authorized to perform requested action e.g. unable to create material-type -- unauthorized
         * 
         * @param entity
         *     unable to create material-type -- unauthorized
         */
        public static MaterialTypeResource.PostMaterialTypeResponse withPlainUnauthorized(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(401).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new MaterialTypeResource.PostMaterialTypeResponse(responseBuilder.build());
        }

        /**
         * Internal server error, e.g. due to misconfiguration e.g. Internal server error, contact administrator
         * 
         * @param entity
         *     Internal server error, contact administrator
         */
        public static MaterialTypeResource.PostMaterialTypeResponse withPlainInternalServerError(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(500).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new MaterialTypeResource.PostMaterialTypeResponse(responseBuilder.build());
        }

    }

    public class PutMaterialTypeByMaterialtypeIdResponse
        extends org.folio.rest.jaxrs.resource.support.ResponseWrapper
    {


        private PutMaterialTypeByMaterialtypeIdResponse(Response delegate) {
            super(delegate);
        }

        /**
         * Item successfully updated
         * 
         */
        public static MaterialTypeResource.PutMaterialTypeByMaterialtypeIdResponse withNoContent() {
            Response.ResponseBuilder responseBuilder = Response.status(204);
            return new MaterialTypeResource.PutMaterialTypeByMaterialtypeIdResponse(responseBuilder.build());
        }

        /**
         * Item with a given ID not found e.g. "material-type not found"
         * 
         * 
         * @param entity
         *     "material-type not found"
         *     
         */
        public static MaterialTypeResource.PutMaterialTypeByMaterialtypeIdResponse withPlainNotFound(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(404).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new MaterialTypeResource.PutMaterialTypeByMaterialtypeIdResponse(responseBuilder.build());
        }

        /**
         * Bad request, e.g. malformed request body or query parameter. Details of the error (e.g. name of the parameter or line/character number with malformed data) provided in the response. e.g. "unable to update material-type -- malformed JSON at 13:4"
         * 
         * 
         * @param entity
         *     "unable to update material-type -- malformed JSON at 13:4"
         *     
         */
        public static MaterialTypeResource.PutMaterialTypeByMaterialtypeIdResponse withPlainBadRequest(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(400).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new MaterialTypeResource.PutMaterialTypeByMaterialtypeIdResponse(responseBuilder.build());
        }

        /**
         * Internal server error, e.g. due to misconfiguration e.g. internal server error, contact administrator
         * 
         * @param entity
         *     internal server error, contact administrator
         */
        public static MaterialTypeResource.PutMaterialTypeByMaterialtypeIdResponse withPlainInternalServerError(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(500).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new MaterialTypeResource.PutMaterialTypeByMaterialtypeIdResponse(responseBuilder.build());
        }

    }

}
