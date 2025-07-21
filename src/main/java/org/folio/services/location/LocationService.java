package org.folio.services.location;

import static org.folio.rest.impl.StorageHelper.logAndSaveError;
import static org.folio.rest.persist.PgUtil.deleteById;
import static org.folio.rest.persist.PgUtil.post;
import static org.folio.rest.persist.PgUtil.put;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.folio.persist.LocationRepository;
import org.folio.rest.exceptions.BadRequestException;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.jaxrs.model.Locations;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.resource.Locations.DeleteLocationsByIdResponse;
import org.folio.rest.jaxrs.resource.Locations.DeleteLocationsResponse;
import org.folio.rest.jaxrs.resource.Locations.GetLocationsByIdResponse;
import org.folio.rest.jaxrs.resource.Locations.PostLocationsResponse;
import org.folio.rest.jaxrs.resource.Locations.PutLocationsByIdResponse;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.cql.CQLQueryValidationException;
import org.folio.services.domainevent.LocationDomainEventPublisher;

public class LocationService {

  public static final String LOCATION_TABLE = "location";

  private final Context context;
  private final Map<String, String> okapiHeaders;
  private final LocationRepository repository;
  private final LocationDomainEventPublisher domainEventService;

  public LocationService(Context context, Map<String, String> okapiHeaders) {
    this.context = context;
    this.okapiHeaders = okapiHeaders;

    this.repository = new LocationRepository(context, okapiHeaders);
    this.domainEventService = new LocationDomainEventPublisher(context, okapiHeaders);
  }

  public Future<Response> getByQuery(String cql, int offset, int limit, String totalRecords,
                                     boolean includeShadowLocations) {
    try {
      return repository.getByQuery(cql, offset, limit, totalRecords, includeShadowLocations)
        .map(results -> {
          var collection = new Locations();
          collection.setLocations(results.getResults());
          collection.setTotalRecords(results.getResultInfo().getTotalRecords());
          return Response.ok(collection, MediaType.APPLICATION_JSON_TYPE).build();
        });
    } catch (CQLQueryValidationException e) {
      return Future.failedFuture(new BadRequestException(e.getMessage()));
    } catch (Exception e) {
      return Future.failedFuture(e);
    }
  }

  public Future<Response> getById(String id) {
    return PgUtil.getById(LOCATION_TABLE, Location.class, id, okapiHeaders, context,
      GetLocationsByIdResponse.class);
  }

  public Future<Response> create(Location location) {
    var checks = List.of(checkAtLeastOneServicePoint(location),
      checkForDuplicateServicePoints(location),
      checkPrimaryServicePointRelationship(location));
    return doLocationChecks(checks)
      .compose(exceptions -> {
        if (exceptions.isEmpty()) {
          return post(LOCATION_TABLE, location, okapiHeaders, context, PostLocationsResponse.class)
            .onSuccess(domainEventService.publishCreated());
        } else {
          var errors = toErrors(exceptions);
          return Future.succeededFuture(PostLocationsResponse
            .respond422WithApplicationJson(errors));
        }
      });
  }

  public Future<Response> update(String id, Location location) {
    if (location.getId() == null) {
      location.setId(id);
    }
    var checks = List.of(checkIdChange(id, location),
      checkAtLeastOneServicePoint(location),
      checkForDuplicateServicePoints(location),
      checkPrimaryServicePointRelationship(location));
    return doLocationChecks(checks)
      .compose(exceptions -> {
        if (exceptions.isEmpty()) {
          return repository.getById(id)
            .compose(
              oldLocation -> put(LOCATION_TABLE, location, id, okapiHeaders, context, PutLocationsByIdResponse.class)
                .onSuccess(domainEventService.publishUpdated(oldLocation))
            );
        } else {
          return Future.succeededFuture(PostLocationsResponse
            .respond422WithApplicationJson(toErrors(exceptions)));
        }
      });
  }

  public Future<Response> delete(String id) {
    return repository.getById(id)
      .compose(oldLocation -> deleteById(LOCATION_TABLE, id, okapiHeaders, context,
        DeleteLocationsByIdResponse.class)
        .onSuccess(domainEventService.publishRemoved(oldLocation))
      );
  }

  public Future<Response> deleteAll() {
    return repository.deleteAll()
      .transform(prepareDeleteAllResponse())
      .onSuccess(response -> domainEventService.publishAllRemoved());
  }

  private Errors toErrors(List<LocationCheckException> exceptions) {
    var errorList = exceptions.stream()
      .map(LocationCheckException::toError)
      .toList();
    return new Errors().withErrors(errorList);
  }

  private Function<AsyncResult<RowSet<Row>>, Future<Response>> prepareDeleteAllResponse() {
    return reply -> reply.succeeded()
                    ? Future.succeededFuture(DeleteLocationsResponse.respond204())
                    : Future.succeededFuture(
                      DeleteLocationsResponse.respond500WithTextPlain(reply.cause().getMessage())
                    );
  }

  private Future<List<LocationCheckException>> doLocationChecks(List<Optional<LocationCheckException>> optionals) {
    return Future.succeededFuture(optionals.stream().filter(Optional::isPresent).map(Optional::get).toList());
  }

  private Optional<LocationCheckException> checkIdChange(String id, Location entity) {
    if (!id.equals(entity.getId())) {
      return Optional.of(new LocationCheckException("id", "Illegal operation: id cannot be changed"));
    }

    return Optional.empty();

  }

  private Optional<LocationCheckException> checkAtLeastOneServicePoint(Location entity) {
    if (entity.getServicePointIds().isEmpty()) {
      return Optional.of(
        new LocationCheckException("servicePointIds", "A location must have at least one Service Point assigned."));
    }

    return Optional.empty();

  }

  private Optional<LocationCheckException> checkPrimaryServicePointRelationship(Location entity) {
    if (!entity.getServicePointIds().contains(entity.getPrimaryServicePoint())) {
      return Optional.of(new LocationCheckException("primaryServicePoint",
        "A Location's Primary Service point must be included as a Service Point."));
    }

    return Optional.empty();

  }

  private Optional<LocationCheckException> checkForDuplicateServicePoints(Location entity) {
    if (entity.getServicePointIds().size() != new HashSet<>(entity.getServicePointIds()).size()) {
      return Optional.of(
        new LocationCheckException("servicePointIds", "A Service Point can only appear once on a Location."));
    }

    return Optional.empty();

  }

  private static class LocationCheckException extends Exception {

    private final String field;

    LocationCheckException(String field, String message) {
      super(message);
      this.field = field;
    }

    public String getField() {
      return this.field;
    }

    public Error toError() {
      Error error = new Error();
      Parameter p = new Parameter();
      p.setKey(getField());
      error.getParameters().add(p);
      error.setMessage(logAndSaveError(this));
      error.setCode("-1");
      error.setType("1");
      return error;
    }
  }

}
