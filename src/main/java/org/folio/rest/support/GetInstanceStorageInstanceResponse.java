package org.folio.rest.support;

import io.vertx.core.http.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.folio.HttpStatus;
import org.folio.rest.jaxrs.model.Instances;
import org.folio.rest.jaxrs.resource.support.ResponseDelegate;

public final class GetInstanceStorageInstanceResponse extends ResponseDelegate {

  private GetInstanceStorageInstanceResponse(Response response, Object entity) {
    super(response, entity);
  }

  public static GetInstanceStorageInstanceResponse respond200WithApplicationJson(
    Instances entity) {
    Response.ResponseBuilder responseBuilder = Response.status(HttpStatus.HTTP_OK.toInt())
      .header(HttpHeaders.CONTENT_TYPE.toString(), MediaType.APPLICATION_JSON);
    responseBuilder.entity(entity);
    return new GetInstanceStorageInstanceResponse(responseBuilder.build(), entity);
  }

  public static GetInstanceStorageInstanceResponse respond400WithTextPlain(Object entity) {
    Response.ResponseBuilder responseBuilder = Response.status(HttpStatus.HTTP_BAD_REQUEST.toInt())
      .header(HttpHeaders.CONTENT_TYPE.toString(), MediaType.TEXT_PLAIN);
    responseBuilder.entity(entity);
    return new GetInstanceStorageInstanceResponse(responseBuilder.build(), entity);
  }

  public static GetInstanceStorageInstanceResponse respond401WithTextPlain(Object entity) {
    Response.ResponseBuilder responseBuilder = Response.status(HttpStatus.HTTP_UNAUTHORIZED.toInt())
      .header(HttpHeaders.CONTENT_TYPE.toString(), MediaType.TEXT_PLAIN);
    responseBuilder.entity(entity);
    return new GetInstanceStorageInstanceResponse(responseBuilder.build(), entity);
  }

  public static GetInstanceStorageInstanceResponse respond500WithTextPlain(Object entity) {
    Response.ResponseBuilder responseBuilder = Response.status(HttpStatus.HTTP_INTERNAL_SERVER_ERROR.toInt())
      .header(HttpHeaders.CONTENT_TYPE.toString(), MediaType.TEXT_PLAIN);
    responseBuilder.entity(entity);
    return new GetInstanceStorageInstanceResponse(responseBuilder.build(), entity);
  }
}
