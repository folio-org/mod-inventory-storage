package org.folio.services.locationunit;

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
import org.folio.client.LocationCampusClient;
import org.folio.client.RestClientImpl;
import org.folio.rest.jaxrs.model.LocationCampus;
import org.folio.services.domainevent.CampusDomainEventPublisher;

public class CampusService {

  private final Map<String, String> okapiHeaders;
  private final LocationCampusClient campusClient;
  private final CampusDomainEventPublisher domainEventPublisher;

  public CampusService(Context context, Map<String, String> okapiHeaders) {
    this.okapiHeaders = okapiHeaders;
    this.campusClient = new LocationCampusClient(new RestClientImpl(context.owner().createHttpClient()));
    this.domainEventPublisher = new CampusDomainEventPublisher(context, okapiHeaders);
  }

  public Future<Response> getByQuery(String cql, int offset, int limit,
                                     boolean includeShadow) {
    return campusClient.getByQuery(cql, offset, limit, includeShadow, okapiHeaders)
      .map(this::toResponse);
  }

  public Future<Response> getById(String id) {
    return campusClient.getById(id, okapiHeaders)
      .map(this::toResponse);
  }

  public Future<Response> create(LocationCampus campus) {
    return campusClient.create(campus, okapiHeaders)
      .map(httpResponse -> {
        if (httpResponse.statusCode() == HTTP_CREATED.toInt()) {
          var created = httpResponse.bodyAsJson(LocationCampus.class);
          if (created != null) {
            domainEventPublisher.publishCreated()
              .handle(Response.status(HTTP_CREATED.toInt()).entity(created).build());
          }
        }
        return toResponse(httpResponse);
      });
  }

  public Future<Response> update(String id, LocationCampus campus) {
    return campusClient.getById(id, okapiHeaders)
      .compose(oldResponse -> {
        if (oldResponse.statusCode() == HTTP_NOT_FOUND.toInt()) {
          return Future.succeededFuture(toResponse(oldResponse));
        }
        var oldCampus = oldResponse.statusCode() == HTTP_OK.toInt()
                        ? oldResponse.bodyAsJson(LocationCampus.class) : null;
        return campusClient.update(id, campus, okapiHeaders)
          .map(httpResponse -> {
            if (httpResponse.statusCode() == HTTP_NO_CONTENT.toInt() && oldCampus != null) {
              domainEventPublisher.publishUpdated(oldCampus, campus);
            }
            return toResponse(httpResponse);
          });
      });
  }

  public Future<Response> delete(String id) {
    return campusClient.getById(id, okapiHeaders)
      .compose(oldResponse -> {
        if (oldResponse.statusCode() == HTTP_NOT_FOUND.toInt()) {
          return Future.succeededFuture(toResponse(oldResponse));
        }
        var oldCampus = oldResponse.statusCode() == HTTP_OK.toInt()
                        ? oldResponse.bodyAsJson(LocationCampus.class) : null;
        return campusClient.delete(id, okapiHeaders)
          .map(httpResponse -> {
            if (httpResponse.statusCode() == HTTP_NO_CONTENT.toInt() && oldCampus != null) {
              domainEventPublisher.publishRemoved(oldCampus)
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
