package org.folio.services.location;

import static org.folio.rest.impl.StorageHelper.logAndSaveError;
import static org.folio.rest.tools.utils.TenantTool.tenantId;

import io.vertx.core.Future;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.folio.persist.LocationRepository;
import org.folio.rest.exceptions.ValidationException;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.jaxrs.model.Locations;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.services.domainevent.LocationDomainEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class LocationService {

  private final LocationDomainEventPublisher locationDomainEventPublisher;
  private final LocationRepository locationRepository;

  public LocationService(LocationDomainEventPublisher locationDomainEventPublisher,
                         LocationRepository locationRepository) {
    this.locationRepository = locationRepository;
    this.locationDomainEventPublisher = locationDomainEventPublisher;
  }

  public Future<Locations> getByQuery(String cql, int offset, int limit, String totalRecords,
                                      boolean includeShadowLocations, Map<String, String> headers) {
    var additionalCql = includeShadowLocations ? null : "isShadow=false";
    return locationRepository
      .query(cql, additionalCql, offset, limit, totalRecords, tenantId(headers))
      .map(results -> new Locations()
        .withLocations(results.getResults())
        .withTotalRecords(results.getResultInfo().getTotalRecords()));
  }

  public Future<Location> getById(String id, Map<String, String> headers) {
    return locationRepository.findById(id, tenantId(headers));
  }

  public Future<Location> create(Location location, Map<String, String> headers) {
    var checks = List.of(checkAtLeastOneServicePoint(location),
      checkForDuplicateServicePoints(location),
      checkPrimaryServicePointRelationship(location));
    return doLocationChecks(checks)
      .compose(exceptions -> {
        if (exceptions.isEmpty()) {
          return locationRepository.create(location, tenantId(headers))
            .onSuccess(created -> locationDomainEventPublisher.publishRecordCreated(created, headers));
        }
        return Future.failedFuture(new ValidationException(toErrors(exceptions)));
      });
  }

  public Future<Void> update(String id, Location location, Map<String, String> headers) {
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
          return locationRepository.getById(id, tenantId(headers))
            .compose(oldLocation -> locationRepository.replace(id, location, tenantId(headers))
              .onSuccess(notUsed -> locationDomainEventPublisher.publishRecordUpdated(oldLocation, headers)));
        }
        return Future.failedFuture(new ValidationException(toErrors(exceptions)));
      });
  }

  public Future<Void> delete(String id, Map<String, String> headers) {
    return locationRepository.getById(id, tenantId(headers))
      .compose(oldLocation -> locationRepository.remove(id, tenantId(headers))
        .onSuccess(notUsed -> locationDomainEventPublisher.publishRecordRemoved(oldLocation, headers)));
  }

  public Future<Void> deleteAll(Map<String, String> headers) {
    return locationRepository.deleteAll(tenantId(headers))
      .onSuccess(notUsed -> locationDomainEventPublisher.publishAllRemoved(headers))
      .mapEmpty();
  }

  private Errors toErrors(List<LocationCheckException> exceptions) {
    var errorList = exceptions.stream()
      .map(LocationCheckException::toError)
      .toList();
    return new Errors().withErrors(errorList);
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
