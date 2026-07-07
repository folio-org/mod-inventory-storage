package org.folio.services.location;

import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.HttpStatus.HTTP_NOT_FOUND;
import static org.folio.HttpStatus.HTTP_NO_CONTENT;
import static org.folio.HttpStatus.HTTP_OK;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import java.util.Map;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.folio.client.LocationClient;
import org.folio.client.RestClientImpl;
import org.folio.rest.jaxrs.model.Location;
import org.folio.services.domainevent.LocationDomainEventPublisher;

public class LocationService {

  private final Map<String, String> okapiHeaders;
  private final LocationClient locationClient;
  private final LocationDomainEventPublisher domainEventPublisher;

  public LocationService(Context context, Map<String, String> okapiHeaders) {
    this.okapiHeaders = okapiHeaders;
    this.locationClient = new LocationClient(new RestClientImpl(context.owner().createHttpClient()));
    this.domainEventPublisher = new LocationDomainEventPublisher(context, okapiHeaders);
  }

  public Future<Response> getByQuery(String cql, int offset, int limit, boolean includeShadowLocations) {
    return locationClient.getByQuery(cql, offset, limit, includeShadowLocations, okapiHeaders)
      .map(this::toResponse);
  }

  public Future<Response> getById(String id) {
    return locationClient.getById(id, okapiHeaders)
      .map(this::toResponse);
  }

  public Future<Response> create(Location location) {
    return locationClient.create(location, okapiHeaders)
      .map(httpResponse -> {
        if (httpResponse.statusCode() == HTTP_CREATED.toInt()) {
          var created = httpResponse.bodyAsJson(Location.class);
          if (created != null) {
            domainEventPublisher.publishCreated()
              .handle(Response.status(HTTP_CREATED.toInt()).entity(created).build());
          }
        }
        return toResponse(httpResponse);
      });
  }

  public Future<Response> update(String id, Location location) {
    return locationClient.getById(id, okapiHeaders)
      .compose(oldResponse -> {
        if (oldResponse.statusCode() == HTTP_NOT_FOUND.toInt()) {
          return Future.succeededFuture(toResponse(oldResponse));
        }
        var oldLocation = oldResponse.statusCode() == HTTP_OK.toInt()
                          ? oldResponse.bodyAsJson(Location.class) : null;
        return locationClient.update(id, location, okapiHeaders)
          .map(httpResponse -> {
            if (httpResponse.statusCode() == HTTP_NO_CONTENT.toInt() && oldLocation != null) {
              domainEventPublisher.publishUpdated(oldLocation, location);
            }
            return toResponse(httpResponse);
          });
      });
  }

  public Future<Response> delete(String id) {
    return locationClient.getById(id, okapiHeaders)
      .compose(oldResponse -> {
        if (oldResponse.statusCode() == HTTP_NOT_FOUND.toInt()) {
          return Future.succeededFuture(toResponse(oldResponse));
        }
        var oldLocation = oldResponse.statusCode() == HTTP_OK.toInt()
                          ? oldResponse.bodyAsJson(Location.class) : null;
        return locationClient.delete(id, okapiHeaders)
          .map(httpResponse -> {
            if (httpResponse.statusCode() == HTTP_NO_CONTENT.toInt() && oldLocation != null) {
              domainEventPublisher.publishRemoved(oldLocation)
                .handle(Response.status(HTTP_NO_CONTENT.toInt()).build());
            }
            return toResponse(httpResponse);
          });
      });
  }

  public Future<Response> deleteAll() {
    return domainEventPublisher.publishAllRemoved()
      .map(v -> Response.status(HTTP_NO_CONTENT.toInt()).build());
  }

  private Response toResponse(HttpResponse<Buffer> httpResponse) {
    var builder = Response.status(httpResponse.statusCode());
    var body = httpResponse.bodyAsString();
    if (body != null && !body.isBlank()) {
      builder.entity(body).type(MediaType.APPLICATION_JSON_TYPE);
    }
    return builder.build();
  }
}
