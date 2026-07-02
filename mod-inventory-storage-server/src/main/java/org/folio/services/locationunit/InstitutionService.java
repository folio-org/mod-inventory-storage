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
import org.folio.client.LocationInstitutionClient;
import org.folio.client.RestClientImpl;
import org.folio.rest.jaxrs.model.LocationInstitution;
import org.folio.services.domainevent.InstitutionDomainEventPublisher;

public class InstitutionService {

  private final Map<String, String> okapiHeaders;
  private final LocationInstitutionClient institutionClient;
  private final InstitutionDomainEventPublisher domainEventPublisher;

  public InstitutionService(Context context, Map<String, String> okapiHeaders) {
    this.okapiHeaders = okapiHeaders;
    this.institutionClient = new LocationInstitutionClient(new RestClientImpl(context.owner().createHttpClient()));
    this.domainEventPublisher = new InstitutionDomainEventPublisher(context, okapiHeaders);
  }

  public Future<Response> getByQuery(String cql, int offset, int limit, boolean includeShadow) {
    return institutionClient.getByQuery(cql, offset, limit, includeShadow, okapiHeaders)
      .map(this::toResponse);
  }

  public Future<Response> getById(String id) {
    return institutionClient.getById(id, okapiHeaders)
      .map(this::toResponse);
  }

  public Future<Response> create(LocationInstitution institution) {
    return institutionClient.create(institution, okapiHeaders)
      .map(httpResponse -> {
        if (httpResponse.statusCode() == HTTP_CREATED.toInt()) {
          var created = httpResponse.bodyAsJson(LocationInstitution.class);
          if (created != null) {
            domainEventPublisher.publishCreated()
              .handle(Response.status(HTTP_CREATED.toInt()).entity(created).build());
          }
        }
        return toResponse(httpResponse);
      });
  }

  public Future<Response> update(String id, LocationInstitution institution) {
    return institutionClient.getById(id, okapiHeaders)
      .compose(oldResponse -> {
        if (oldResponse.statusCode() == HTTP_NOT_FOUND.toInt()) {
          return Future.succeededFuture(toResponse(oldResponse));
        }
        var oldInstitution = oldResponse.statusCode() == HTTP_OK.toInt()
                             ? oldResponse.bodyAsJson(LocationInstitution.class) : null;
        return institutionClient.update(id, institution, okapiHeaders)
          .map(httpResponse -> {
            if (httpResponse.statusCode() == HTTP_NO_CONTENT.toInt() && oldInstitution != null) {
              domainEventPublisher.publishUpdated(oldInstitution, institution);
            }
            return toResponse(httpResponse);
          });
      });
  }

  public Future<Response> delete(String id) {
    return institutionClient.getById(id, okapiHeaders)
      .compose(oldResponse -> {
        if (oldResponse.statusCode() == HTTP_NOT_FOUND.toInt()) {
          return Future.succeededFuture(toResponse(oldResponse));
        }
        var oldInstitution = oldResponse.statusCode() == HTTP_OK.toInt()
                             ? oldResponse.bodyAsJson(LocationInstitution.class) : null;
        return institutionClient.delete(id, okapiHeaders)
          .map(httpResponse -> {
            if (httpResponse.statusCode() == HTTP_NO_CONTENT.toInt() && oldInstitution != null) {
              domainEventPublisher.publishRemoved(oldInstitution)
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
