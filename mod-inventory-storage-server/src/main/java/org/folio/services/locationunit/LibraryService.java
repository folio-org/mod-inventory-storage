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
import org.folio.client.LocationLibraryClient;
import org.folio.client.RestClientImpl;
import org.folio.rest.jaxrs.model.LocationLibrary;
import org.folio.services.domainevent.LibraryDomainEventPublisher;

public class LibraryService {

  private final Map<String, String> okapiHeaders;
  private final LocationLibraryClient libraryClient;
  private final LibraryDomainEventPublisher domainEventPublisher;

  public LibraryService(Context context, Map<String, String> okapiHeaders) {
    this.okapiHeaders = okapiHeaders;
    this.libraryClient = new LocationLibraryClient(new RestClientImpl(context.owner().createHttpClient()));
    this.domainEventPublisher = new LibraryDomainEventPublisher(context, okapiHeaders);
  }

  public Future<Response> getByQuery(String cql, int offset, int limit,
                                     boolean includeShadow) {
    return libraryClient.getByQuery(cql, offset, limit, includeShadow, okapiHeaders)
      .map(this::toResponse);
  }

  public Future<Response> getById(String id) {
    return libraryClient.getById(id, okapiHeaders)
      .map(this::toResponse);
  }

  public Future<Response> create(LocationLibrary library) {
    return libraryClient.create(library, okapiHeaders)
      .map(httpResponse -> {
        if (httpResponse.statusCode() == HTTP_CREATED.toInt()) {
          var created = httpResponse.bodyAsJson(LocationLibrary.class);
          if (created != null) {
            domainEventPublisher.publishCreated()
              .handle(Response.status(HTTP_CREATED.toInt()).entity(created).build());
          }
        }
        return toResponse(httpResponse);
      });
  }

  public Future<Response> update(String id, LocationLibrary library) {
    return libraryClient.getById(id, okapiHeaders)
      .compose(oldResponse -> {
        if (oldResponse.statusCode() == HTTP_NOT_FOUND.toInt()) {
          return Future.succeededFuture(toResponse(oldResponse));
        }
        var oldLibrary = oldResponse.statusCode() == HTTP_OK.toInt()
                         ? oldResponse.bodyAsJson(LocationLibrary.class) : null;
        return libraryClient.update(id, library, okapiHeaders)
          .map(httpResponse -> {
            if (httpResponse.statusCode() == HTTP_NO_CONTENT.toInt() && oldLibrary != null) {
              domainEventPublisher.publishUpdated(oldLibrary, library);
            }
            return toResponse(httpResponse);
          });
      });
  }

  public Future<Response> delete(String id) {
    return libraryClient.getById(id, okapiHeaders)
      .compose(oldResponse -> {
        if (oldResponse.statusCode() == HTTP_NOT_FOUND.toInt()) {
          return Future.succeededFuture(toResponse(oldResponse));
        }
        var oldLibrary = oldResponse.statusCode() == HTTP_OK.toInt()
                         ? oldResponse.bodyAsJson(LocationLibrary.class) : null;
        return libraryClient.delete(id, okapiHeaders)
          .map(httpResponse -> {
            if (httpResponse.statusCode() == HTTP_NO_CONTENT.toInt() && oldLibrary != null) {
              domainEventPublisher.publishRemoved(oldLibrary)
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
