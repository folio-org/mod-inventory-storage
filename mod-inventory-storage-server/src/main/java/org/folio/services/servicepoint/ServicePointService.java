package org.folio.services.servicepoint;

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
import org.folio.client.RestClientImpl;
import org.folio.client.ServicePointClient;
import org.folio.rest.jaxrs.model.ServicePoint;
import org.folio.services.domainevent.ServicePointDomainEventPublisher;

public class ServicePointService {

  private final Map<String, String> okapiHeaders;
  private final ServicePointClient servicePointClient;
  private final ServicePointDomainEventPublisher domainEventPublisher;

  public ServicePointService(Context context, Map<String, String> okapiHeaders) {
    this.okapiHeaders = okapiHeaders;
    this.servicePointClient = new ServicePointClient(new RestClientImpl(context.owner().createHttpClient()));
    this.domainEventPublisher = new ServicePointDomainEventPublisher(context, okapiHeaders);
  }

  public Future<Response> getByQuery(String cql, int offset, int limit,
                                     boolean includeRoutingServicePoints) {
    return servicePointClient.getByQuery(cql, offset, limit, includeRoutingServicePoints, okapiHeaders)
      .map(this::toResponse);
  }

  public Future<Response> getById(String id) {
    return servicePointClient.getById(id, okapiHeaders)
      .map(this::toResponse);
  }

  public Future<Response> create(ServicePoint servicePoint) {
    return servicePointClient.create(servicePoint, okapiHeaders)
      .compose(httpResponse -> {
        if (httpResponse.statusCode() == HTTP_CREATED.toInt()) {
          var created = httpResponse.bodyAsJson(ServicePoint.class);
          if (created != null) {
            return domainEventPublisher.publishCreated(created)
              .map(v -> toResponse(httpResponse));
          }
        }
        return Future.succeededFuture(toResponse(httpResponse));
      });
  }

  public Future<Response> update(String id, ServicePoint servicePoint) {
    return servicePointClient.getById(id, okapiHeaders)
      .compose(oldResponse -> {
        if (oldResponse.statusCode() == HTTP_NOT_FOUND.toInt()) {
          return Future.succeededFuture(toResponse(oldResponse));
        }
        var oldServicePoint = oldResponse.statusCode() == HTTP_OK.toInt()
                              ? oldResponse.bodyAsJson(ServicePoint.class) : null;
        return servicePointClient.update(id, servicePoint, okapiHeaders)
          .compose(httpResponse -> {
            if (httpResponse.statusCode() == HTTP_NO_CONTENT.toInt() && oldServicePoint != null) {
              return domainEventPublisher.publishUpdated(oldServicePoint, servicePoint)
                .map(v -> toResponse(httpResponse));
            }
            return Future.succeededFuture(toResponse(httpResponse));
          });
      });
  }

  public Future<Response> delete(String id) {
    return servicePointClient.getById(id, okapiHeaders)
      .compose(oldResponse -> {
        if (oldResponse.statusCode() == HTTP_NOT_FOUND.toInt()) {
          return Future.succeededFuture(toResponse(oldResponse));
        }
        var oldServicePoint = oldResponse.statusCode() == HTTP_OK.toInt()
                              ? oldResponse.bodyAsJson(ServicePoint.class) : null;
        return servicePointClient.delete(id, okapiHeaders)
          .compose(httpResponse -> {
            if (httpResponse.statusCode() == HTTP_NO_CONTENT.toInt() && oldServicePoint != null) {
              return domainEventPublisher.publishDeleted(oldServicePoint)
                .map(v -> toResponse(httpResponse));
            }
            return Future.succeededFuture(toResponse(httpResponse));
          });
      });
  }

  public Future<Response> deleteAll() {
    return Future.succeededFuture(Response.status(HTTP_NO_CONTENT.toInt()).build());
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
