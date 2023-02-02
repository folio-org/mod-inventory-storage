package org.folio.persist.entity;

import java.util.stream.Collectors;
import javax.ws.rs.core.Response;
import org.folio.rest.jaxrs.model.Instances;
import org.folio.rest.jaxrs.resource.support.ResponseDelegate;

public class GetInstanceStorageInstancesResponseInternal extends ResponseDelegate {

  public static final String CONTENT_TYPE = "Content-Type";
  public static final String TEXT_PLAIN = "text/plain";
  public static final String APPLICATION_JSON = "application/json";

  private GetInstanceStorageInstancesResponseInternal(Response response, Object entity) {
    super(response, entity);
  }

  private GetInstanceStorageInstancesResponseInternal(Response response) {
    super(response);
  }

  public static GetInstanceStorageInstancesResponseInternal respond200WithApplicationJson(
    InstancesInternal entity) {
    var result = new Instances().withResultInfo(entity.getResultInfo()).withTotalRecords(entity.getTotalRecords())
      .withInstances(entity.getInstances().stream().map(InstanceInternal::toInstanceDto).collect(Collectors.toList()));
    Response.ResponseBuilder responseBuilder = Response.status(200).header(CONTENT_TYPE, APPLICATION_JSON);
    responseBuilder.entity(result);
    return new GetInstanceStorageInstancesResponseInternal(responseBuilder.build(), result);
  }

  public static GetInstanceStorageInstancesResponseInternal respond400WithTextPlain(Object entity) {
    Response.ResponseBuilder responseBuilder = Response.status(400).header(CONTENT_TYPE, TEXT_PLAIN);
    responseBuilder.entity(entity);
    return new GetInstanceStorageInstancesResponseInternal(responseBuilder.build(), entity);
  }

  public static GetInstanceStorageInstancesResponseInternal respond401WithTextPlain(Object entity) {
    Response.ResponseBuilder responseBuilder = Response.status(401).header(CONTENT_TYPE, TEXT_PLAIN);
    responseBuilder.entity(entity);
    return new GetInstanceStorageInstancesResponseInternal(responseBuilder.build(), entity);
  }

  public static GetInstanceStorageInstancesResponseInternal respond500WithTextPlain(Object entity) {
    Response.ResponseBuilder responseBuilder = Response.status(500).header(CONTENT_TYPE, TEXT_PLAIN);
    responseBuilder.entity(entity);
    return new GetInstanceStorageInstancesResponseInternal(responseBuilder.build(), entity);
  }
}

