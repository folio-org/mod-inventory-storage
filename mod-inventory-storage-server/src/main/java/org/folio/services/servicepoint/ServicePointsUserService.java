package org.folio.services.servicepoint;

import static org.folio.HttpStatus.HTTP_NOT_FOUND;
import static org.folio.HttpStatus.HTTP_NO_CONTENT;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import java.util.Map;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.folio.client.RestClientImpl;
import org.folio.client.ServicePointsUserClient;
import org.folio.rest.jaxrs.model.ServicePointsUser;

public class ServicePointsUserService {

  private final Map<String, String> okapiHeaders;
  private final ServicePointsUserClient servicePointsUserClient;

  public ServicePointsUserService(Context context, Map<String, String> okapiHeaders) {
    this.okapiHeaders = okapiHeaders;
    this.servicePointsUserClient = new ServicePointsUserClient(new RestClientImpl(context.owner().createHttpClient()));
  }

  public Future<Response> getByQuery(String cql, int offset, int limit, String totalRecords) {
    return servicePointsUserClient.getByQuery(cql, offset, limit, totalRecords, okapiHeaders)
      .map(this::toResponse);
  }

  public Future<Response> getById(String id) {
    return servicePointsUserClient.getById(id, okapiHeaders)
      .map(this::toResponse);
  }

  public Future<Response> create(ServicePointsUser servicePointsUser) {
    return servicePointsUserClient.create(servicePointsUser, okapiHeaders)
      .map(this::toResponse);
  }

  public Future<Response> update(String id, ServicePointsUser servicePointsUser) {
    return servicePointsUserClient.getById(id, okapiHeaders)
      .compose(oldResponse -> {
        if (oldResponse.statusCode() == HTTP_NOT_FOUND.toInt()) {
          return Future.succeededFuture(toResponse(oldResponse));
        }
        return servicePointsUserClient.update(id, servicePointsUser, okapiHeaders)
          .map(this::toResponse);
      });
  }

  public Future<Response> delete(String id) {
    return servicePointsUserClient.getById(id, okapiHeaders)
      .compose(oldResponse -> {
        if (oldResponse.statusCode() == HTTP_NOT_FOUND.toInt()) {
          return Future.succeededFuture(toResponse(oldResponse));
        }
        return servicePointsUserClient.delete(id, okapiHeaders)
          .map(this::toResponse);
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
