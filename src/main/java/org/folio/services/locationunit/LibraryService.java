package org.folio.services.locationunit;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import org.folio.persist.LibraryRepository;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.*;
import org.folio.rest.jaxrs.resource.LocationUnits.*;
import org.folio.rest.persist.PgUtil;
import org.folio.services.domainevent.LibraryDomainEventPublisher;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.folio.rest.impl.StorageHelper.logAndSaveError;

public class LibraryService {

  public static final String LIBRARY_TABLE = "loclibrary";

  private final Context context;
  private final Map<String, String> okapiHeaders;
  private final LibraryRepository repository;
  private final LibraryDomainEventPublisher domainEventService;

  public LibraryService(Context context, Map<String, String> okapiHeaders) {
    this.context = context;
    this.okapiHeaders = okapiHeaders;
    this.repository = new LibraryRepository(context, okapiHeaders);
    this.domainEventService = new LibraryDomainEventPublisher(context, okapiHeaders);
  }

  public Future<Response> getByQuery(String cql, int offset, int limit) {
    return PgUtil.get(LIBRARY_TABLE, Loclib.class, Loclibs.class,
      cql, offset, limit, okapiHeaders, context, GetLocationUnitsLibrariesResponse.class);
  }

  public Future<Response> getById(String id) {
    return PgUtil.getById(LIBRARY_TABLE, Loclib.class, id, okapiHeaders, context,
      GetLocationUnitsLibrariesByIdResponse.class);
  }

  public Future<Response> create(Loclib loclib) {
    return PgUtil.post(LIBRARY_TABLE, loclib, okapiHeaders, context, PostLocationUnitsLibrariesResponse.class)
      .onSuccess(domainEventService.publishCreated());
  }

  public Future<Response> update(String id, Loclib loclib) {
    if (loclib.getId() == null) {
      loclib.setId(id);
    }
    var checks = List.of(checkIdChange(id, loclib));
    return doLibraryCheck(checks)
      .compose(exceptions -> {
        if (exceptions.isEmpty()) {
          return repository.getById(id)
            .compose(oldLoclib ->
              PgUtil.put(LIBRARY_TABLE, loclib, id, okapiHeaders, context,
                  PutLocationUnitsLibrariesByIdResponse.class)
                .onSuccess(domainEventService.publishUpdated(oldLoclib))
            );
        } else {
          return Future.succeededFuture(PostLocationUnitsLibrariesResponse
            .respond422WithApplicationJson(toErrors(exceptions)));
        }
      });
  }

  public Future<Response> delete(String id) {
    return repository.getById(id)
      .compose(oldLocation ->
        PgUtil.deleteById(LIBRARY_TABLE, id, okapiHeaders, context,
            DeleteLocationUnitsLibrariesByIdResponse.class)
          .onSuccess(domainEventService.publishRemoved(oldLocation))
      );
  }

  public Future<Response> deleteAll() {
    return repository.deleteAll()
      .transform(prepareDeleteAllResponse())
      .onSuccess(response -> domainEventService.publishAllRemoved());
  }

  private Function<AsyncResult<RowSet<Row>>, Future<Response>> prepareDeleteAllResponse() {
    return reply -> reply.succeeded()
      ? Future.succeededFuture(DeleteLocationUnitsLibrariesResponse.respond204())
      : Future.succeededFuture(
      DeleteLocationUnitsLibrariesResponse.respond500WithTextPlain(reply.cause().getMessage())
    );
  }

  private Future<List<LocationUnitLibraryException>> doLibraryCheck(List<Optional<LocationUnitLibraryException>> optionals) {
    return Future.succeededFuture(optionals.stream().filter(Optional::isPresent).map(Optional::get).toList());
  }

  private Optional<LocationUnitLibraryException> checkIdChange(String id, Loclib entity) {
    if (!id.equals(entity.getId())) {
      return Optional.of(new LocationUnitLibraryException("id", "Illegal operation: id cannot be changed"));
    }
    return Optional.empty();
  }

  private Errors toErrors(List<LocationUnitLibraryException> exceptions) {
    var errorList = exceptions.stream()
      .map(LocationUnitLibraryException::toError)
      .toList();
    return new Errors().withErrors(errorList);
  }

  private static class LocationUnitLibraryException extends Exception {

    private final String field;

    LocationUnitLibraryException(String field, String message) {
      super(message);
      this.field = field;
    }

    public String getField() {
      return field;
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

