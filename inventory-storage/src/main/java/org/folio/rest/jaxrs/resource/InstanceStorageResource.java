
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
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.Instances;

@Path("instance-storage")
public interface InstanceStorageResource {


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
    @Path("instances")
    @Produces({
        "text/plain"
    })
    @Validate
    void deleteInstanceStorageInstances(
        @QueryParam("lang")
        @DefaultValue("en")
        @Pattern(regexp = "[a-zA-Z]{2}")
        String lang, java.util.Map<String, String>okapiHeaders, io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>>asyncResultHandler, Context vertxContext)
        throws Exception
    ;

    /**
     * Retrieve a list of instance items.
     * 
     * @param offset
     *     Skip over a number of elements by specifying an offset value for the query e.g. 0
     * @param query
     *     JSON array [{"field1","value1","operator1"},{"field2","value2","operator2"},...,{"fieldN","valueN","operatorN"}] by title (using CQL)
     *      e.g. title="*uproot*"
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
    @Path("instances")
    @Produces({
        "application/json",
        "text/plain"
    })
    @Validate
    void getInstanceStorageInstances(
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
        @QueryParam("query")
        String query,
        @QueryParam("lang")
        @DefaultValue("en")
        @Pattern(regexp = "[a-zA-Z]{2}")
        String lang, java.util.Map<String, String>okapiHeaders, io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>>asyncResultHandler, Context vertxContext)
        throws Exception
    ;

    /**
     * Create a new instance item.
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
     *       "id" : "601a8dc4-dee7-48eb-b03f-d02fdf0debd0",
     *       "title": "ADVANCING LIBRARY EDUCATION: TECHNOLOGICAL INNOVATION AND INSTRUCTIONAL DESIGN",
     *       "identifiers": [
     *         {
     *           "namespace": "isbn",
     *           "value": "9781466636897"
     *         },
     *         {
     *           "namespace": "ybp",
     *           "value": "1"
     *         }
     *       ]
     *     }
     *     
     */
    @POST
    @Path("instances")
    @Consumes("application/json")
    @Produces({
        "application/json",
        "text/plain"
    })
    @Validate
    void postInstanceStorageInstances(
        @QueryParam("lang")
        @DefaultValue("en")
        @Pattern(regexp = "[a-zA-Z]{2}")
        String lang, Instance entity, java.util.Map<String, String>okapiHeaders, io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>>asyncResultHandler, Context vertxContext)
        throws Exception
    ;

    /**
     * Get Instance by InstanceId
     * Instances are stored and accessed by a hash of key properties. The rules which govern
     * how instance hashes are computed are business rules and defined in the service layer.
     * the storage layer only knows how to insert or retrieve instance records by ID.
     * 
     * 
     * @param instanceId
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
    @Path("instances/{instanceId}")
    @Produces({
        "application/json",
        "text/plain"
    })
    @Validate
    void getInstanceStorageInstancesByInstanceId(
        @PathParam("instanceId")
        @NotNull
        String instanceId,
        @QueryParam("lang")
        @DefaultValue("en")
        @Pattern(regexp = "[a-zA-Z]{2}")
        String lang, java.util.Map<String, String>okapiHeaders, io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>>asyncResultHandler, Context vertxContext)
        throws Exception
    ;

    /**
     * Delete instance item with given {instanceId}
     * 
     * 
     * @param instanceId
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
    @Path("instances/{instanceId}")
    @Produces({
        "text/plain"
    })
    @Validate
    void deleteInstanceStorageInstancesByInstanceId(
        @PathParam("instanceId")
        @NotNull
        String instanceId,
        @QueryParam("lang")
        @DefaultValue("en")
        @Pattern(regexp = "[a-zA-Z]{2}")
        String lang, java.util.Map<String, String>okapiHeaders, io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>>asyncResultHandler, Context vertxContext)
        throws Exception
    ;

    /**
     * Update instance item with given {instanceId}
     * 
     * 
     * @param instanceId
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
     *       "id" : "601a8dc4-dee7-48eb-b03f-d02fdf0debd0",
     *       "title": "ADVANCING LIBRARY EDUCATION: TECHNOLOGICAL INNOVATION AND INSTRUCTIONAL DESIGN",
     *       "identifiers": [
     *         {
     *           "namespace": "isbn",
     *           "value": "9781466636897"
     *         },
     *         {
     *           "namespace": "ybp",
     *           "value": "1"
     *         }
     *       ]
     *     }
     *     
     */
    @PUT
    @Path("instances/{instanceId}")
    @Consumes("application/json")
    @Produces({
        "text/plain"
    })
    @Validate
    void putInstanceStorageInstancesByInstanceId(
        @PathParam("instanceId")
        @NotNull
        String instanceId,
        @QueryParam("lang")
        @DefaultValue("en")
        @Pattern(regexp = "[a-zA-Z]{2}")
        String lang, Instance entity, java.util.Map<String, String>okapiHeaders, io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>>asyncResultHandler, Context vertxContext)
        throws Exception
    ;

    public class DeleteInstanceStorageInstancesByInstanceIdResponse
        extends org.folio.rest.jaxrs.resource.support.ResponseWrapper
    {


        private DeleteInstanceStorageInstancesByInstanceIdResponse(Response delegate) {
            super(delegate);
        }

        /**
         * Item deleted successfully
         * 
         */
        public static InstanceStorageResource.DeleteInstanceStorageInstancesByInstanceIdResponse withNoContent() {
            Response.ResponseBuilder responseBuilder = Response.status(204);
            return new InstanceStorageResource.DeleteInstanceStorageInstancesByInstanceIdResponse(responseBuilder.build());
        }

        /**
         * Item with a given ID not found e.g. "instance not found"
         * 
         * 
         * @param entity
         *     "instance not found"
         *     
         */
        public static InstanceStorageResource.DeleteInstanceStorageInstancesByInstanceIdResponse withPlainNotFound(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(404).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new InstanceStorageResource.DeleteInstanceStorageInstancesByInstanceIdResponse(responseBuilder.build());
        }

        /**
         * Bad request, e.g. malformed request body or query parameter. Details of the error (e.g. name of the parameter or line/character number with malformed data) provided in the response. e.g. "unable to delete instance -- constraint violation"
         * 
         * 
         * @param entity
         *     "unable to delete instance -- constraint violation"
         *     
         */
        public static InstanceStorageResource.DeleteInstanceStorageInstancesByInstanceIdResponse withPlainBadRequest(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(400).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new InstanceStorageResource.DeleteInstanceStorageInstancesByInstanceIdResponse(responseBuilder.build());
        }

        /**
         * Internal server error, e.g. due to misconfiguration e.g. Internal server error, contact administrator
         * 
         * @param entity
         *     Internal server error, contact administrator
         */
        public static InstanceStorageResource.DeleteInstanceStorageInstancesByInstanceIdResponse withPlainInternalServerError(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(500).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new InstanceStorageResource.DeleteInstanceStorageInstancesByInstanceIdResponse(responseBuilder.build());
        }

    }

    public class DeleteInstanceStorageInstancesResponse
        extends org.folio.rest.jaxrs.resource.support.ResponseWrapper
    {


        private DeleteInstanceStorageInstancesResponse(Response delegate) {
            super(delegate);
        }

        /**
         * All instances deleted
         * 
         */
        public static InstanceStorageResource.DeleteInstanceStorageInstancesResponse withNoContent() {
            Response.ResponseBuilder responseBuilder = Response.status(204);
            return new InstanceStorageResource.DeleteInstanceStorageInstancesResponse(responseBuilder.build());
        }

        /**
         * Internal server error, e.g. due to misconfiguration e.g. Internal server error, contact administrator
         * 
         * @param entity
         *     Internal server error, contact administrator
         */
        public static InstanceStorageResource.DeleteInstanceStorageInstancesResponse withPlainInternalServerError(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(500).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new InstanceStorageResource.DeleteInstanceStorageInstancesResponse(responseBuilder.build());
        }

    }

    public class GetInstanceStorageInstancesByInstanceIdResponse
        extends org.folio.rest.jaxrs.resource.support.ResponseWrapper
    {


        private GetInstanceStorageInstancesByInstanceIdResponse(Response delegate) {
            super(delegate);
        }

        /**
         * Returns item with a given ID e.g. {
         *   "id" : "601a8dc4-dee7-48eb-b03f-d02fdf0debd0",
         *   "title": "ADVANCING LIBRARY EDUCATION: TECHNOLOGICAL INNOVATION AND INSTRUCTIONAL DESIGN",
         *   "identifiers": [
         *     {
         *       "namespace": "isbn",
         *       "value": "9781466636897"
         *     },
         *     {
         *       "namespace": "ybp",
         *       "value": "1"
         *     }
         *   ]
         * }
         * 
         * 
         * @param entity
         *     {
         *       "id" : "601a8dc4-dee7-48eb-b03f-d02fdf0debd0",
         *       "title": "ADVANCING LIBRARY EDUCATION: TECHNOLOGICAL INNOVATION AND INSTRUCTIONAL DESIGN",
         *       "identifiers": [
         *         {
         *           "namespace": "isbn",
         *           "value": "9781466636897"
         *         },
         *         {
         *           "namespace": "ybp",
         *           "value": "1"
         *         }
         *       ]
         *     }
         *     
         */
        public static InstanceStorageResource.GetInstanceStorageInstancesByInstanceIdResponse withJsonOK(Instance entity) {
            Response.ResponseBuilder responseBuilder = Response.status(200).header("Content-Type", "application/json");
            responseBuilder.entity(entity);
            return new InstanceStorageResource.GetInstanceStorageInstancesByInstanceIdResponse(responseBuilder.build());
        }

        /**
         * Item with a given ID not found e.g. "instance not found"
         * 
         * 
         * @param entity
         *     "instance not found"
         *     
         */
        public static InstanceStorageResource.GetInstanceStorageInstancesByInstanceIdResponse withPlainNotFound(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(404).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new InstanceStorageResource.GetInstanceStorageInstancesByInstanceIdResponse(responseBuilder.build());
        }

        /**
         * Internal server error, e.g. due to misconfiguration e.g. internal server error, contact administrator
         * 
         * @param entity
         *     internal server error, contact administrator
         */
        public static InstanceStorageResource.GetInstanceStorageInstancesByInstanceIdResponse withPlainInternalServerError(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(500).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new InstanceStorageResource.GetInstanceStorageInstancesByInstanceIdResponse(responseBuilder.build());
        }

    }

    public class GetInstanceStorageInstancesResponse
        extends org.folio.rest.jaxrs.resource.support.ResponseWrapper
    {


        private GetInstanceStorageInstancesResponse(Response delegate) {
            super(delegate);
        }

        /**
         * Returns a list of instance items e.g. {
         *   "instances": [
         *     {
         *       "id" : "601a8dc4-dee7-48eb-b03f-d02fdf0debd0",
         *       "title": "ADVANCING LIBRARY EDUCATION: TECHNOLOGICAL INNOVATION AND INSTRUCTIONAL DESIGN",
         *       "identifiers": [
         *         {
         *           "namespace": "isbn",
         *           "value": "9781466636897"
         *         },
         *         {
         *           "namespace": "ybp",
         *           "value": "1"
         *         }
         *       ]
         *     },
         *     {
         *       "id" : "601a8dc4-dee7-48eb-b03f-d02fdf0debd0",
         *       "title": "ADVANCING RESEARCH METHODS WITH NEW TECHNOLOGIES.",
         *       "identifiers": [
         *         {
         *           "namespace": "isbn",
         *           "value": "9781466639195"
         *         },
         *         {
         *           "namespace": "ybp",
         *           "value": "2"
         *         }
         *       ]
         *     }
         *   ],
         *   "totalRecords": 2
         * }
         * 
         * 
         * @param entity
         *     {
         *       "instances": [
         *         {
         *           "id" : "601a8dc4-dee7-48eb-b03f-d02fdf0debd0",
         *           "title": "ADVANCING LIBRARY EDUCATION: TECHNOLOGICAL INNOVATION AND INSTRUCTIONAL DESIGN",
         *           "identifiers": [
         *             {
         *               "namespace": "isbn",
         *               "value": "9781466636897"
         *             },
         *             {
         *               "namespace": "ybp",
         *               "value": "1"
         *             }
         *           ]
         *         },
         *         {
         *           "id" : "601a8dc4-dee7-48eb-b03f-d02fdf0debd0",
         *           "title": "ADVANCING RESEARCH METHODS WITH NEW TECHNOLOGIES.",
         *           "identifiers": [
         *             {
         *               "namespace": "isbn",
         *               "value": "9781466639195"
         *             },
         *             {
         *               "namespace": "ybp",
         *               "value": "2"
         *             }
         *           ]
         *         }
         *       ],
         *       "totalRecords": 2
         *     }
         *     
         */
        public static InstanceStorageResource.GetInstanceStorageInstancesResponse withJsonOK(Instances entity) {
            Response.ResponseBuilder responseBuilder = Response.status(200).header("Content-Type", "application/json");
            responseBuilder.entity(entity);
            return new InstanceStorageResource.GetInstanceStorageInstancesResponse(responseBuilder.build());
        }

        /**
         * Bad request, e.g. malformed request body or query parameter. Details of the error (e.g. name of the parameter or line/character number with malformed data) provided in the response. e.g. unable to list instances -- malformed parameter 'query', syntax error at column 6
         * 
         * @param entity
         *     unable to list instances -- malformed parameter 'query', syntax error at column 6
         */
        public static InstanceStorageResource.GetInstanceStorageInstancesResponse withPlainBadRequest(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(400).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new InstanceStorageResource.GetInstanceStorageInstancesResponse(responseBuilder.build());
        }

        /**
         * Internal server error, e.g. due to misconfiguration e.g. internal server error, contact administrator
         * 
         * @param entity
         *     internal server error, contact administrator
         */
        public static InstanceStorageResource.GetInstanceStorageInstancesResponse withPlainInternalServerError(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(500).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new InstanceStorageResource.GetInstanceStorageInstancesResponse(responseBuilder.build());
        }

    }

    public class PostInstanceStorageInstancesResponse
        extends org.folio.rest.jaxrs.resource.support.ResponseWrapper
    {


        private PostInstanceStorageInstancesResponse(Response delegate) {
            super(delegate);
        }

        /**
         * Returns a newly created item, with server-controlled fields like 'id' populated e.g. {
         *   "id" : "601a8dc4-dee7-48eb-b03f-d02fdf0debd0",
         *   "title": "ADVANCING LIBRARY EDUCATION: TECHNOLOGICAL INNOVATION AND INSTRUCTIONAL DESIGN",
         *   "identifiers": [
         *     {
         *       "namespace": "isbn",
         *       "value": "9781466636897"
         *     },
         *     {
         *       "namespace": "ybp",
         *       "value": "1"
         *     }
         *   ]
         * }
         * 
         * 
         * @param location
         *     URI to the created instance item
         * @param entity
         *     {
         *       "id" : "601a8dc4-dee7-48eb-b03f-d02fdf0debd0",
         *       "title": "ADVANCING LIBRARY EDUCATION: TECHNOLOGICAL INNOVATION AND INSTRUCTIONAL DESIGN",
         *       "identifiers": [
         *         {
         *           "namespace": "isbn",
         *           "value": "9781466636897"
         *         },
         *         {
         *           "namespace": "ybp",
         *           "value": "1"
         *         }
         *       ]
         *     }
         *     
         */
        public static InstanceStorageResource.PostInstanceStorageInstancesResponse withJsonCreated(String location, StreamingOutput entity) {
            Response.ResponseBuilder responseBuilder = Response.status(201).header("Content-Type", "application/json").header("Location", location);
            responseBuilder.entity(entity);
            return new InstanceStorageResource.PostInstanceStorageInstancesResponse(responseBuilder.build());
        }

        /**
         * Bad request, e.g. malformed request body or query parameter. Details of the error (e.g. name of the parameter or line/character number with malformed data) provided in the response. e.g. "unable to add instance -- malformed JSON at 13:3"
         * 
         * 
         * @param entity
         *     "unable to add instance -- malformed JSON at 13:3"
         *     
         */
        public static InstanceStorageResource.PostInstanceStorageInstancesResponse withPlainBadRequest(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(400).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new InstanceStorageResource.PostInstanceStorageInstancesResponse(responseBuilder.build());
        }

        /**
         * Internal server error, e.g. due to misconfiguration e.g. Internal server error, contact administrator
         * 
         * @param entity
         *     Internal server error, contact administrator
         */
        public static InstanceStorageResource.PostInstanceStorageInstancesResponse withPlainInternalServerError(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(500).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new InstanceStorageResource.PostInstanceStorageInstancesResponse(responseBuilder.build());
        }

    }

    public class PutInstanceStorageInstancesByInstanceIdResponse
        extends org.folio.rest.jaxrs.resource.support.ResponseWrapper
    {


        private PutInstanceStorageInstancesByInstanceIdResponse(Response delegate) {
            super(delegate);
        }

        /**
         * Item successfully updated
         * 
         */
        public static InstanceStorageResource.PutInstanceStorageInstancesByInstanceIdResponse withNoContent() {
            Response.ResponseBuilder responseBuilder = Response.status(204);
            return new InstanceStorageResource.PutInstanceStorageInstancesByInstanceIdResponse(responseBuilder.build());
        }

        /**
         * Item with a given ID not found e.g. "instance not found"
         * 
         * 
         * @param entity
         *     "instance not found"
         *     
         */
        public static InstanceStorageResource.PutInstanceStorageInstancesByInstanceIdResponse withPlainNotFound(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(404).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new InstanceStorageResource.PutInstanceStorageInstancesByInstanceIdResponse(responseBuilder.build());
        }

        /**
         * Bad request, e.g. malformed request body or query parameter. Details of the error (e.g. name of the parameter or line/character number with malformed data) provided in the response. e.g. "unable to update instance -- malformed JSON at 13:4"
         * 
         * 
         * @param entity
         *     "unable to update instance -- malformed JSON at 13:4"
         *     
         */
        public static InstanceStorageResource.PutInstanceStorageInstancesByInstanceIdResponse withPlainBadRequest(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(400).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new InstanceStorageResource.PutInstanceStorageInstancesByInstanceIdResponse(responseBuilder.build());
        }

        /**
         * Internal server error, e.g. due to misconfiguration e.g. internal server error, contact administrator
         * 
         * @param entity
         *     internal server error, contact administrator
         */
        public static InstanceStorageResource.PutInstanceStorageInstancesByInstanceIdResponse withPlainInternalServerError(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(500).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new InstanceStorageResource.PutInstanceStorageInstancesByInstanceIdResponse(responseBuilder.build());
        }

    }

}
