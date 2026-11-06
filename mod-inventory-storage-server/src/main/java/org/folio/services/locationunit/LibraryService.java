package org.folio.services.locationunit;

import static org.folio.rest.tools.utils.TenantTool.tenantId;
import static org.folio.rest.tools.utils.ValidationHelper.createValidationErrorMessage;

import io.vertx.core.Future;
import java.util.Map;
import org.folio.persist.LibraryRepository;
import org.folio.rest.exceptions.ValidationException;
import org.folio.rest.jaxrs.model.LocationLibraries;
import org.folio.rest.jaxrs.model.LocationLibrary;
import org.folio.services.domainevent.LibraryDomainEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class LibraryService {

  private static final String MSG_ID_NOT_MATCH = "Illegal operation: Library ID cannot be changed";

  private final LibraryDomainEventPublisher libraryDomainEventPublisher;
  private final LibraryRepository libraryRepository;

  public LibraryService(LibraryDomainEventPublisher libraryDomainEventPublisher,
                 LibraryRepository libraryRepository) {
    this.libraryRepository = libraryRepository;
    this.libraryDomainEventPublisher = libraryDomainEventPublisher;
  }

  public Future<LocationLibraries> getByQuery(String cql, int offset, int limit, String totalRecords,
                           boolean includeShadow, Map<String, String> headers) {
    var additionalCql = includeShadow ? null : "isShadow=false";
    return libraryRepository
      .query(cql, additionalCql, offset, limit, totalRecords, tenantId(headers))
      .map(results -> new LocationLibraries()
        .withLoclibs(results.getResults())
        .withTotalRecords(results.getResultInfo().getTotalRecords()));
  }

  public Future<LocationLibrary> getById(String id, Map<String, String> headers) {
    return libraryRepository.findById(id, tenantId(headers));
  }

  public Future<LocationLibrary> create(LocationLibrary loclib, Map<String, String> headers) {
    return libraryRepository.create(loclib, tenantId(headers))
      .onSuccess(created -> libraryDomainEventPublisher.publishRecordCreated(created, headers));
  }

  public Future<Void> update(String id, LocationLibrary loclib, Map<String, String> headers) {
    if (loclib.getId() != null && !loclib.getId().equals(id)) {
      return Future.failedFuture(new ValidationException(
        createValidationErrorMessage("loclib", loclib.getId(), MSG_ID_NOT_MATCH)));
    }
    if (loclib.getId() == null) {
      loclib.setId(id);
    }

    return libraryRepository.getById(id, tenantId(headers))
      .compose(oldLibrary -> libraryRepository.replace(id, loclib, tenantId(headers))
        .onSuccess(notUsed -> libraryDomainEventPublisher.publishRecordUpdated(oldLibrary, headers)));
  }

  public Future<Void> delete(String id, Map<String, String> headers) {
    return libraryRepository.getById(id, tenantId(headers))
      .compose(oldLibrary -> libraryRepository.remove(id, tenantId(headers))
        .onSuccess(notUsed -> libraryDomainEventPublisher.publishRecordRemoved(oldLibrary, headers)));
  }

  public Future<Void> deleteAll(Map<String, String> headers) {
    return libraryRepository.deleteAll(tenantId(headers))
      .onSuccess(notUsed -> libraryDomainEventPublisher.publishAllRemoved(headers))
      .mapEmpty();
  }
}
