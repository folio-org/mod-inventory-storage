
package org.folio.rest.jaxrs.resource;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
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

@Path("instance_storage")
public interface InstanceStorageResource {


    /**
     * Retrieve a list of instance items.
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
    @Path("instance")
    @Produces({
        "application/json",
        "text/plain"
    })
    @Validate
    void getInstanceStorageInstance(
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
     *       "id": "cd28da0f-a3e4-465c-82f1-acade4e8e170",
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
    @Path("instance")
    @Consumes("application/json")
    @Produces({
        "application/json",
        "text/plain"
    })
    @Validate
    void postInstanceStorageInstance(
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
     */
    @GET
    @Path("instance/{instanceId}")
    @Validate
    void getInstanceStorageInstanceByInstanceId(
        @PathParam("instanceId")
        @NotNull
        String instanceId, java.util.Map<String, String>okapiHeaders, io.vertx.core.Handler<io.vertx.core.AsyncResult<Response>>asyncResultHandler, Context vertxContext)
        throws Exception
    ;

    public class GetInstanceStorageInstanceByInstanceIdResponse
        extends org.folio.rest.jaxrs.resource.support.ResponseWrapper
    {


        private GetInstanceStorageInstanceByInstanceIdResponse(Response delegate) {
            super(delegate);
        }

        /**
         * 
         */
        public static InstanceStorageResource.GetInstanceStorageInstanceByInstanceIdResponse withOK() {
            Response.ResponseBuilder responseBuilder = Response.status(200);
            return new InstanceStorageResource.GetInstanceStorageInstanceByInstanceIdResponse(responseBuilder.build());
        }

    }

    public class GetInstanceStorageInstanceResponse
        extends org.folio.rest.jaxrs.resource.support.ResponseWrapper
    {


        private GetInstanceStorageInstanceResponse(Response delegate) {
            super(delegate);
        }

        /**
         * Returns a list of instance items e.g. {
         *   "id": "cd28da0f-a3e4-465c-82f1-acade4e8e170",
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
         *       "id": "cd28da0f-a3e4-465c-82f1-acade4e8e170",
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
        public static InstanceStorageResource.GetInstanceStorageInstanceResponse withJsonOK(Instances entity) {
            Response.ResponseBuilder responseBuilder = Response.status(200).header("Content-Type", "application/json");
            responseBuilder.entity(entity);
            return new InstanceStorageResource.GetInstanceStorageInstanceResponse(responseBuilder.build());
        }

        /**
         * Bad request, e.g. malformed request body or query parameter. Details of the error (e.g. name of the parameter or line/character number with malformed data) provided in the response. e.g. unable to list instance -- malformed parameter 'query', syntax error at column 6
         * 
         * @param entity
         *     unable to list instance -- malformed parameter 'query', syntax error at column 6
         */
        public static InstanceStorageResource.GetInstanceStorageInstanceResponse withPlainBadRequest(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(400).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new InstanceStorageResource.GetInstanceStorageInstanceResponse(responseBuilder.build());
        }

        /**
         * Internal server error, e.g. due to misconfiguration e.g. internal server error, contact administrator
         * 
         * @param entity
         *     internal server error, contact administrator
         */
        public static InstanceStorageResource.GetInstanceStorageInstanceResponse withPlainInternalServerError(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(500).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new InstanceStorageResource.GetInstanceStorageInstanceResponse(responseBuilder.build());
        }

    }

    public class PostInstanceStorageInstanceResponse
        extends org.folio.rest.jaxrs.resource.support.ResponseWrapper
    {


        private PostInstanceStorageInstanceResponse(Response delegate) {
            super(delegate);
        }

        /**
         * Returns a newly created item, with server-controlled fields like 'id' populated e.g. {
         *   "id": "cd28da0f-a3e4-465c-82f1-acade4e8e170",
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
         *       "id": "cd28da0f-a3e4-465c-82f1-acade4e8e170",
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
        public static InstanceStorageResource.PostInstanceStorageInstanceResponse withJsonCreated(String location, StreamingOutput entity) {
            Response.ResponseBuilder responseBuilder = Response.status(201).header("Content-Type", "application/json").header("Location", location);
            responseBuilder.entity(entity);
            return new InstanceStorageResource.PostInstanceStorageInstanceResponse(responseBuilder.build());
        }

        /**
         * Bad request, e.g. malformed request body or query parameter. Details of the error (e.g. name of the parameter or line/character number with malformed data) provided in the response. e.g. "unable to add instance -- malformed JSON at 13:3"
         * 
         * 
         * @param entity
         *     "unable to add instance -- malformed JSON at 13:3"
         *     
         */
        public static InstanceStorageResource.PostInstanceStorageInstanceResponse withPlainBadRequest(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(400).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new InstanceStorageResource.PostInstanceStorageInstanceResponse(responseBuilder.build());
        }

        /**
         * Internal server error, e.g. due to misconfiguration e.g. Internal server error, contact administrator
         * 
         * @param entity
         *     Internal server error, contact administrator
         */
        public static InstanceStorageResource.PostInstanceStorageInstanceResponse withPlainInternalServerError(String entity) {
            Response.ResponseBuilder responseBuilder = Response.status(500).header("Content-Type", "text/plain");
            responseBuilder.entity(entity);
            return new InstanceStorageResource.PostInstanceStorageInstanceResponse(responseBuilder.build());
        }

    }

}
